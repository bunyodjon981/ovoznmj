package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.TelefonimApplication
import com.example.ui.overlay.OverlayOrbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_ASSISTANT"
        const val ACTION_STOP = "com.example.action.STOP_ASSISTANT"
        const val ACTION_LISTEN_NOW = "com.example.action.LISTEN_NOW"
        const val ACTION_DISMISS_OVERLAY = "com.example.action.DISMISS_OVERLAY"
        const val ACTION_EXECUTE_TEXT = "com.example.action.EXECUTE_TEXT"
        const val EXTRA_TEXT_COMMAND = "extra_text_command"
        private const val NOTIFICATION_ID = 4101

        fun startAssistant(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopAssistant(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun triggerActiveListen(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_LISTEN_NOW
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun dismissOverlay(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_DISMISS_OVERLAY
            }
            context.startService(intent)
        }

        fun executeTextCommand(context: Context, command: String) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                action = ACTION_EXECUTE_TEXT
                putExtra(EXTRA_TEXT_COMMAND, command)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private enum class ListenStage {
        PASSIVE_WAKE,
        ACTIVE_INITIAL_WAIT,
        ACTIVE_FINAL_5S_WAIT,
        ACTIVE_STAY_OPEN
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var overlayManager: OverlayOrbManager
    private lateinit var voiceEngine: UzbekVoiceEngine
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager

    private var partialWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var listenStage: ListenStage = ListenStage.PASSIVE_WAKE
    private var isExecutingCommand = false
    private var currentWakeWords: List<String> = listOf("Telefonim")
    private var continuousWakeEnabled = true
    private var voiceFeedbackEnabled = true
    private var hasAnnouncedStart = false

    private var activeTimeoutJob: Job? = null
    private var countdownJob: Job? = null
    private var unmuteJob: Job? = null
    private var mutedMusicByUs = false
    private var mutedSystemByUs = false

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayOrbManager(this)
        voiceEngine = UzbekVoiceEngine(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        acquirePartialWakeLock()

        val app = application as TelefonimApplication
        serviceScope.launch {
            app.settingsDataStore.settingsFlow.collectLatest { settings ->
                currentWakeWords = settings.wakeWords
                continuousWakeEnabled = settings.continuousWakeListening
                voiceFeedbackEnabled = settings.voiceFeedbackEnabled
                AssistantRuntimeState.update {
                    it.copy(
                        wakeWord = settings.wakeWord,
                        wakeWords = settings.wakeWords
                    )
                }
                updateNotification()
            }
        }
    }

    private fun acquirePartialWakeLock() {
        try {
            if (partialWakeLock == null) {
                partialWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TelefonimAI::BackgroundWakeListening"
                ).apply { setReferenceCounted(false) }
            }
            if (partialWakeLock?.isHeld == false) {
                partialWakeLock?.acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        try {
            if (screenWakeLock == null) {
                screenWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "TelefonimAI::OverlayScreenWakeLock"
                ).apply { setReferenceCounted(false) }
            }
            if (screenWakeLock?.isHeld == false) {
                screenWakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseScreenWakeLock() {
        try {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()

        when (intent?.action) {
            ACTION_STOP -> {
                shutdownService()
                return START_NOT_STICKY
            }

            ACTION_DISMISS_OVERLAY -> {
                closeOverlayAndReturnPassive()
            }

            ACTION_LISTEN_NOW -> {
                AssistantRuntimeState.update { it.copy(isServiceRunning = true) }
                beginWakeGreetingAndListen()
            }

            ACTION_EXECUTE_TEXT -> {
                val cmd = intent.getStringExtra(EXTRA_TEXT_COMMAND).orEmpty()
                if (cmd.isNotBlank()) {
                    AssistantRuntimeState.update { it.copy(isServiceRunning = true) }
                    serviceScope.launch {
                        processUserCommand(cmd, showOverlay = false)
                    }
                }
            }

            else -> {
                AssistantRuntimeState.update { it.copy(isServiceRunning = true) }
                if (!hasAnnouncedStart) {
                    hasAnnouncedStart = true
                    serviceScope.launch {
                        if (voiceFeedbackEnabled) {
                            AssistantRuntimeState.update {
                                it.copy(
                                    aiResponse = "Ishga tushdim! Men tayyorman.",
                                    orbPhase = OrbPhase.SPEAKING
                                )
                            }
                            voiceEngine.speakAndAwait("Ishga tushdim! Men tayyorman.")
                        }
                        if (continuousWakeEnabled) {
                            startPassiveWakeListening()
                        } else {
                            AssistantRuntimeState.setPhase(OrbPhase.IDLE)
                        }
                    }
                } else if (continuousWakeEnabled && !isExecutingCommand && listenStage == ListenStage.PASSIVE_WAKE) {
                    startPassiveWakeListening()
                }
            }
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (!AssistantRuntimeState.state.value.isServiceRunning) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AssistantForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val listenIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AssistantForegroundService::class.java).apply { action = ACTION_LISTEN_NOW },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val namesPreview = currentWakeWords.take(3).joinToString(", ")
        return NotificationCompat.Builder(this, TelefonimApplication.CHANNEL_ASSISTANT_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Telefonim AI • 24/7 Faol")
            .setContentText("Chaqiruv so'zlari: $namesPreview")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Gapirish", listenIntent)
            .addAction(0, "To'xtatish", stopIntent)
            .build()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun silenceRecognizerBeepBriefly() {
        if (AssistantRuntimeState.state.value.orbPhase == OrbPhase.SPEAKING) return
        unmuteJob?.cancel()
        try {
            if (!audioManager.isMusicActive && !audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                mutedMusicByUs = true
            }
        } catch (_: Exception) {
        }
        try {
            if (!audioManager.isStreamMute(AudioManager.STREAM_SYSTEM)) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                mutedSystemByUs = true
            }
        } catch (_: Exception) {
        }

        unmuteJob = serviceScope.launch {
            delay(500L)
            restoreRecognizerStreams()
        }
    }

    private fun restoreRecognizerStreams() {
        try {
            if (mutedMusicByUs) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                mutedMusicByUs = false
            }
        } catch (_: Exception) {
            mutedMusicByUs = false
        }
        try {
            if (mutedSystemByUs) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                mutedSystemByUs = false
            }
        } catch (_: Exception) {
            mutedSystemByUs = false
        }
    }

    private fun startPassiveWakeListening() {
        if (!continuousWakeEnabled || isExecutingCommand || !hasMicPermission()) {
            if (!hasMicPermission()) {
                AssistantRuntimeState.setPhase(OrbPhase.IDLE)
            }
            return
        }
        cancelActiveTimers()
        listenStage = ListenStage.PASSIVE_WAKE
        AssistantRuntimeState.update {
            it.copy(
                orbPhase = OrbPhase.PASSIVE_WAKE_LISTENING,
                countdownSeconds = null
            )
        }
        startRecognizerInternal(isActiveCommand = false)
    }

    private fun resumePassiveWakeListening() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (AssistantRuntimeState.state.value.isServiceRunning && continuousWakeEnabled && !isExecutingCommand) {
                startPassiveWakeListening()
            } else {
                AssistantRuntimeState.setPhase(OrbPhase.IDLE)
            }
        }, 450L)
    }

    private fun beginWakeGreetingAndListen() {
        if (!hasMicPermission()) return

        mainHandler.removeCallbacksAndMessages(null)
        cancelActiveTimers()
        destroyRecognizer()
        restoreRecognizerStreams()
        voiceEngine.stopSpeaking()

        acquireScreenWakeLock()

        AssistantRuntimeState.update {
            it.copy(
                orbPhase = OrbPhase.SPEAKING,
                liveTranscript = "",
                aiResponse = "Assalomu alaykum! Buyrug'ingizni ayting.",
                countdownSeconds = null
            )
        }
        overlayManager.showOverlay { dismissOverlay(this) }

        serviceScope.launch {
            if (voiceFeedbackEnabled) {
                voiceEngine.speakAndAwait("Assalomu alaykum!")
            } else {
                delay(400L)
            }
            if (!AssistantRuntimeState.state.value.isOverlayVisible) return@launch
            startActiveListeningPhase(ListenStage.ACTIVE_INITIAL_WAIT)
        }
    }

    private fun startActiveListeningPhase(stage: ListenStage) {
        cancelActiveTimers()
        listenStage = stage

        AssistantRuntimeState.update {
            it.copy(
                orbPhase = OrbPhase.ACTIVE_LISTENING,
                countdownSeconds = if (stage == ListenStage.ACTIVE_FINAL_5S_WAIT) 5 else null
            )
        }

        startRecognizerInternal(isActiveCommand = true)

        when (stage) {
            ListenStage.ACTIVE_FINAL_5S_WAIT -> {
                countdownJob = serviceScope.launch {
                    for (sec in 5 downTo 1) {
                        AssistantRuntimeState.update { it.copy(countdownSeconds = sec) }
                        delay(1000L)
                    }
                    if (listenStage == ListenStage.ACTIVE_FINAL_5S_WAIT && !isExecutingCommand) {
                        closeOverlayAndReturnPassive()
                    }
                }
            }

            ListenStage.ACTIVE_INITIAL_WAIT -> {
                activeTimeoutJob = serviceScope.launch {
                    delay(6500L)
                    if (listenStage == ListenStage.ACTIVE_INITIAL_WAIT && !isExecutingCommand) {
                        triggerFiveSecondWarning()
                    }
                }
            }

            ListenStage.ACTIVE_STAY_OPEN, ListenStage.PASSIVE_WAKE -> Unit
        }
    }

    private fun triggerFiveSecondWarning() {
        if (isExecutingCommand || !AssistantRuntimeState.state.value.isOverlayVisible) return
        cancelActiveTimers()
        destroyRecognizer()
        restoreRecognizerStreams()

        serviceScope.launch {
            AssistantRuntimeState.update {
                it.copy(
                    orbPhase = OrbPhase.SPEAKING,
                    aiResponse = "Gapirmasangiz, 5 soniyada yopilaman.",
                    countdownSeconds = 5
                )
            }
            if (voiceFeedbackEnabled) {
                voiceEngine.speakAndAwait("Gapirmasangiz, 5 soniyada yopilaman.")
            }
            if (!AssistantRuntimeState.state.value.isOverlayVisible || isExecutingCommand) return@launch
            startActiveListeningPhase(ListenStage.ACTIVE_FINAL_5S_WAIT)
        }
    }

    private fun closeOverlayAndReturnPassive() {
        mainHandler.removeCallbacksAndMessages(null)
        cancelActiveTimers()
        destroyRecognizer()
        voiceEngine.stopSpeaking()
        overlayManager.hideOverlay()
        releaseScreenWakeLock()
        listenStage = ListenStage.PASSIVE_WAKE
        if (AssistantRuntimeState.state.value.isServiceRunning && continuousWakeEnabled) {
            resumePassiveWakeListening()
        } else {
            AssistantRuntimeState.setPhase(OrbPhase.IDLE)
        }
    }

    private fun startRecognizerInternal(isActiveCommand: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (isActiveCommand) {
                AssistantRuntimeState.update {
                    it.copy(
                        orbPhase = OrbPhase.IDLE,
                        aiResponse = "Ovozli aniqlash mavjud emas. Matn orqali buyruq bering."
                    )
                }
            }
            return
        }

        mainHandler.post {
            destroyRecognizer()
            silenceRecognizerBeepBriefly()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {
                    if (isActiveCommand) {
                        cancelActiveTimers()
                        AssistantRuntimeState.update { it.copy(countdownSeconds = null) }
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (isActiveCommand) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.06f, 1f)
                        AssistantRuntimeState.setAudioLevel(normalized)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    AssistantRuntimeState.setAudioLevel(0f)
                    if (!isActiveCommand) {
                        silenceRecognizerBeepBriefly()
                    }
                }

                override fun onError(error: Int) {
                    AssistantRuntimeState.setAudioLevel(0f)
                    if (!AssistantRuntimeState.state.value.isServiceRunning) return

                    if (isActiveCommand) {
                        when (listenStage) {
                            ListenStage.ACTIVE_INITIAL_WAIT -> triggerFiveSecondWarning()
                            ListenStage.ACTIVE_FINAL_5S_WAIT -> closeOverlayAndReturnPassive()
                            ListenStage.ACTIVE_STAY_OPEN -> {
                                // Stay open and keep listening quietly until user says "Yopil" or taps close
                                if (!isExecutingCommand && AssistantRuntimeState.state.value.isOverlayVisible) {
                                    mainHandler.postDelayed({
                                        if (listenStage == ListenStage.ACTIVE_STAY_OPEN &&
                                            !isExecutingCommand &&
                                            AssistantRuntimeState.state.value.isOverlayVisible
                                        ) {
                                            startActiveListeningPhase(ListenStage.ACTIVE_STAY_OPEN)
                                        }
                                    }, 500L)
                                }
                            }
                            ListenStage.PASSIVE_WAKE -> Unit
                        }
                    } else if (continuousWakeEnabled && !isExecutingCommand) {
                        silenceRecognizerBeepBriefly()
                        val backoff = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 600L
                        mainHandler.postDelayed({
                            if (AssistantRuntimeState.state.value.isServiceRunning &&
                                listenStage == ListenStage.PASSIVE_WAKE &&
                                !isExecutingCommand
                            ) {
                                startPassiveWakeListening()
                            }
                        }, backoff)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val bestText = matches.firstOrNull().orEmpty()

                    if (isActiveCommand) {
                        cancelActiveTimers()
                        if (bestText.isNotBlank()) {
                            serviceScope.launch {
                                processUserCommand(bestText, showOverlay = true)
                            }
                        } else {
                            when (listenStage) {
                                ListenStage.ACTIVE_INITIAL_WAIT -> triggerFiveSecondWarning()
                                ListenStage.ACTIVE_FINAL_5S_WAIT -> closeOverlayAndReturnPassive()
                                ListenStage.ACTIVE_STAY_OPEN -> startActiveListeningPhase(ListenStage.ACTIVE_STAY_OPEN)
                                ListenStage.PASSIVE_WAKE -> Unit
                            }
                        }
                    } else {
                        val handled = handlePassiveSpeechCandidates(matches, isPartial = false)
                        if (!handled && continuousWakeEnabled && !isExecutingCommand) {
                            resumePassiveWakeListening()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val partial = matches.firstOrNull().orEmpty()
                    if (isActiveCommand) {
                        if (partial.isNotBlank()) {
                            cancelActiveTimers()
                            AssistantRuntimeState.update {
                                it.copy(liveTranscript = partial, countdownSeconds = null)
                            }
                        }
                    } else {
                        handlePassiveSpeechCandidates(matches, isPartial = true)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val app = application as TelefonimApplication
            val isOffline = !app.offlineCommandEngine.isNetworkAvailable()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2400L)
                if (isOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            try {
                recognizer.startListening(intent)
            } catch (_: Exception) {
                if (!isActiveCommand) {
                    mainHandler.postDelayed({ startPassiveWakeListening() }, 1500L)
                }
            }
        }
    }

    private fun handlePassiveSpeechCandidates(candidates: List<String>, isPartial: Boolean): Boolean {
        for (phrase in candidates) {
            val afterWake = extractAfterAnyWakeWord(phrase, currentWakeWords)
            if (afterWake != null) {
                if (afterWake.isNotBlank() && !isPartial) {
                    destroyRecognizer()
                    restoreRecognizerStreams()
                    acquireScreenWakeLock()
                    serviceScope.launch {
                        processUserCommand(afterWake, showOverlay = true)
                    }
                    return true
                } else if (afterWake.isBlank() || !isPartial) {
                    beginWakeGreetingAndListen()
                    return true
                }
            }
        }
        return false
    }

    private fun extractAfterAnyWakeWord(transcript: String, wakeWords: List<String>): String? {
        val lowerText = transcript.lowercase().trim()
        if (lowerText.isBlank()) return null

        val allVariants = buildSet {
            wakeWords.forEach { raw ->
                val w = raw.lowercase().trim()
                if (w.isNotBlank()) {
                    add(w)
                    if (w == "telefonim") {
                        addAll(listOf("telefonim", "tilifonim", "telifonim", "telefon"))
                    }
                }
            }
        }

        for (v in allVariants) {
            val idx = lowerText.indexOf(v)
            if (idx >= 0) {
                return transcript.substring(idx + v.length).trim(' ', ',', '.', '!', '?')
            }
        }
        return null
    }

    private suspend fun processUserCommand(command: String, showOverlay: Boolean) {
        if (isExecutingCommand) return
        isExecutingCommand = true
        cancelActiveTimers()
        destroyRecognizer()
        restoreRecognizerStreams()

        if (showOverlay) {
            acquireScreenWakeLock()
            overlayManager.showOverlay { dismissOverlay(this) }
        }

        val app = application as TelefonimApplication
        val result = app.agentOrchestrator.handleCommand(command)

        if (showOverlay && !result.shouldCloseOverlay) {
            acquireScreenWakeLock()
            overlayManager.showOverlay { dismissOverlay(this) }
        }

        if (voiceFeedbackEnabled) {
            voiceEngine.speakAndAwait(result.replyUzbek)
        } else {
            AssistantRuntimeState.setPhase(OrbPhase.SPEAKING)
            delay(1500L)
        }

        isExecutingCommand = false

        if (result.shouldCloseOverlay) {
            closeOverlayAndReturnPassive()
            return
        }

        if (showOverlay && AssistantRuntimeState.state.value.isOverlayVisible) {
            // Stay open and keep listening until user explicitly says "Yopil" or taps close!
            startActiveListeningPhase(ListenStage.ACTIVE_STAY_OPEN)
        } else if (AssistantRuntimeState.state.value.isServiceRunning && continuousWakeEnabled) {
            resumePassiveWakeListening()
        } else {
            AssistantRuntimeState.setPhase(OrbPhase.IDLE)
        }
    }

    private fun cancelActiveTimers() {
        activeTimeoutJob?.cancel()
        activeTimeoutJob = null
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun shutdownService() {
        mainHandler.removeCallbacksAndMessages(null)
        cancelActiveTimers()
        unmuteJob?.cancel()
        restoreRecognizerStreams()
        destroyRecognizer()
        voiceEngine.shutdown()
        overlayManager.hideOverlay()
        releaseScreenWakeLock()
        try {
            if (partialWakeLock?.isHeld == true) partialWakeLock?.release()
        } catch (_: Exception) {
        }
        AssistantRuntimeState.update {
            it.copy(
                isServiceRunning = false,
                orbPhase = OrbPhase.IDLE,
                audioLevel = 0f,
                isOverlayVisible = false,
                countdownSeconds = null
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        cancelActiveTimers()
        unmuteJob?.cancel()
        restoreRecognizerStreams()
        destroyRecognizer()
        voiceEngine.shutdown()
        overlayManager.hideOverlay()
        releaseScreenWakeLock()
        try {
            if (partialWakeLock?.isHeld == true) partialWakeLock?.release()
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        AssistantRuntimeState.update {
            it.copy(isServiceRunning = false, orbPhase = OrbPhase.IDLE, countdownSeconds = null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

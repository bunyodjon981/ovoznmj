package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

class UzbekVoiceEngine(context: Context) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingCompletions = mutableMapOf<String, CompletableDeferred<Unit>>()
    private var pulseJob: Job? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return

            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            val uzbek = Locale.forLanguageTag("uz-UZ")
            val turkish = Locale.forLanguageTag("tr-TR")
            val res = engine.setLanguage(uzbek)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                val trRes = engine.setLanguage(turkish)
                if (trRes == TextToSpeech.LANG_MISSING_DATA || trRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
            } else {
                // Prefer high quality offline/local Uzbek voice if available
                try {
                    val bestVoice = engine.voices
                        ?.filter { it.locale.language.equals("uz", ignoreCase = true) }
                        ?.sortedByDescending { it.quality }
                        ?.firstOrNull()
                    if (bestVoice != null) {
                        engine.voice = bestVoice
                    }
                } catch (_: Exception) {
                }
            }

            engine.setSpeechRate(0.96f)
            engine.setPitch(1.0f)

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    startSpeakingWave()
                }

                override fun onDone(utteranceId: String?) {
                    stopSpeakingWave()
                    utteranceId?.let { pendingCompletions.remove(it)?.complete(Unit) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    stopSpeakingWave()
                    utteranceId?.let { pendingCompletions.remove(it)?.complete(Unit) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    stopSpeakingWave()
                    utteranceId?.let { pendingCompletions.remove(it)?.complete(Unit) }
                }
            })
            isReady = true
        }
    }

    fun speakFireAndForget(text: String) {
        if (text.isBlank()) return
        scope.launch {
            speakAndAwait(text)
        }
    }

    suspend fun speakAndAwait(text: String) {
        val cleanedText = normalizeUzbekSpeech(text)
        if (cleanedText.isBlank()) return

        val engine = tts
        if (engine == null || !isReady) {
            AssistantRuntimeState.setPhase(OrbPhase.SPEAKING)
            startSpeakingWave()
            delay(1400L)
            stopSpeakingWave()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Unit>()
        pendingCompletions[utteranceId] = deferred

        AssistantRuntimeState.setPhase(OrbPhase.SPEAKING)
        startSpeakingWave()

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val queued = engine.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (queued == TextToSpeech.ERROR) {
            pendingCompletions.remove(utteranceId)
            stopSpeakingWave()
            return
        }

        val estimatedTimeoutMs = (cleanedText.length * 105L).coerceIn(3000L, 25000L)
        withTimeoutOrNull(estimatedTimeoutMs) {
            deferred.await()
        }
        pendingCompletions.remove(utteranceId)
        stopSpeakingWave()
    }

    private fun normalizeUzbekSpeech(raw: String): String {
        return raw
            .replace("oʻ", "o'")
            .replace("Oʻ", "O'")
            .replace("gʻ", "g'")
            .replace("Gʻ", "G'")
            .replace("ʼ", "'")
            .replace("`", "'")
            .replace("’", "'")
            .replace(Regex("[*_#~`]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun stopSpeaking() {
        tts?.stop()
        stopSpeakingWave()
        pendingCompletions.values.forEach { it.complete(Unit) }
        pendingCompletions.clear()
    }

    private fun startSpeakingWave() {
        pulseJob?.cancel()
        pulseJob = scope.launch {
            var tick = 0f
            while (isActive) {
                tick += 0.35f
                val level = (0.48f + 0.38f * sin(tick) + 0.14f * sin(tick * 2.3f)).coerceIn(0.18f, 1f)
                AssistantRuntimeState.setAudioLevel(level)
                delay(45L)
            }
        }
    }

    private fun stopSpeakingWave() {
        pulseJob?.cancel()
        pulseJob = null
        AssistantRuntimeState.setAudioLevel(0f)
    }

    fun shutdown() {
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }
}

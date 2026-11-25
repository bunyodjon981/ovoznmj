package com.example.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TelefonimApplication
import com.example.data.AssistantSettings
import com.example.data.CommandHistoryEntity
import com.example.service.AssistantForegroundService
import com.example.service.AssistantRuntimeState
import com.example.service.AssistantRuntimeUiState
import com.example.service.OrbPhase
import com.example.service.PhoneControlAccessibilityService
import com.example.service.UzbekVoiceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionsHealth(
    val micGranted: Boolean = false,
    val contactsGranted: Boolean = false,
    val callGranted: Boolean = false,
    val smsGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false
) {
    val readinessFraction: Float
        get() {
            val checks = listOf(
                micGranted,
                contactsGranted,
                callGranted,
                notificationsGranted,
                overlayGranted,
                accessibilityGranted
            )
            return checks.count { it }.toFloat() / checks.size.toFloat()
        }
}

data class SetupUiState(
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TelefonimApplication
    private val settingsStore = app.settingsDataStore
    private val groqRepo = app.groqRepository
    private val historyDao = app.database.commandHistoryDao()
    private val localVoiceEngine = UzbekVoiceEngine(application)

    val settings: StateFlow<AssistantSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AssistantSettings())

    val runtimeState: StateFlow<AssistantRuntimeUiState> = AssistantRuntimeState.state

    val history: StateFlow<List<CommandHistoryEntity>> = historyDao.observeRecentCommands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissions = MutableStateFlow(PermissionsHealth())
    val permissions: StateFlow<PermissionsHealth> = _permissions.asStateFlow()

    private val _setupState = MutableStateFlow(SetupUiState())
    val setupState: StateFlow<SetupUiState> = _setupState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        val mic = hasPerm(ctx, Manifest.permission.RECORD_AUDIO)
        val contacts = hasPerm(ctx, Manifest.permission.READ_CONTACTS)
        val call = hasPerm(ctx, Manifest.permission.CALL_PHONE)
        val sms = hasPerm(ctx, Manifest.permission.SEND_SMS)
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)
        } else true
        val overlay = Settings.canDrawOverlays(ctx)
        val accessibility = isAccessibilityServiceEnabled(ctx)
        val online = app.offlineCommandEngine.isNetworkAvailable()

        AssistantRuntimeState.update {
            it.copy(isAccessibilityEnabled = accessibility, isOnline = online)
        }
        _permissions.value = PermissionsHealth(
            micGranted = mic,
            contactsGranted = contacts,
            callGranted = call,
            smsGranted = sms,
            notificationsGranted = notif,
            overlayGranted = overlay,
            accessibilityGranted = accessibility
        )
    }

    private fun hasPerm(ctx: Context, perm: String): Boolean {
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (PhoneControlAccessibilityService.isConnected()) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        val expectedComponent = ComponentName(context, PhoneControlAccessibilityService::class.java)
        val activeList = am?.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).orEmpty()
        return activeList.any {
            val si = it.resolveInfo?.serviceInfo
            si != null && ComponentName(si.packageName, si.name) == expectedComponent
        }
    }

    fun validateAndSaveApiKey(apiKey: String, wakeWord: String? = null) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            _setupState.update { it.copy(errorMessage = "Iltimos Groq API kalitini kiriting.") }
            return
        }

        viewModelScope.launch {
            _setupState.value = SetupUiState(isChecking = true)
            val result = groqRepo.fetchAvailableFreeModels(cleanKey)
            if (result.isSuccess) {
                settingsStore.saveGroqApiKey(cleanKey)
                settingsStore.saveActiveAndDiscoveredModels(result.bestModel, result.models)
                if (!wakeWord.isNullOrBlank()) {
                    settingsStore.saveWakeWord(wakeWord)
                }
                _setupState.value = SetupUiState(
                    isChecking = false,
                    statusMessage = "Ulandi! Faol bepul model: ${result.bestModel}"
                )
            } else {
                _setupState.value = SetupUiState(
                    isChecking = false,
                    errorMessage = result.errorMessage ?: "API kaliti tekshirilmadi."
                )
            }
        }
    }

    fun refreshFreeModels() {
        val currentKey = settings.value.groqApiKey
        if (currentKey.isBlank()) return
        viewModelScope.launch {
            _setupState.value = SetupUiState(isChecking = true)
            val result = groqRepo.fetchAvailableFreeModels(currentKey)
            if (result.isSuccess) {
                settingsStore.saveActiveAndDiscoveredModels(result.bestModel, result.models)
                _setupState.value = SetupUiState(
                    isChecking = false,
                    statusMessage = "${result.models.size} ta bepul model topildi. Tanlandi: ${result.bestModel}"
                )
            } else {
                _setupState.value = SetupUiState(
                    isChecking = false,
                    errorMessage = result.errorMessage
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsStore.saveActiveAndDiscoveredModels(modelId, settings.value.discoveredModels)
        }
    }

    fun saveWakeWords(wakeWords: List<String>) {
        viewModelScope.launch {
            settingsStore.saveWakeWords(wakeWords)
        }
    }

    fun saveUnlockPin(pin: String) {
        viewModelScope.launch {
            settingsStore.saveUnlockPin(pin)
        }
    }

    fun setAutoFreeModel(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoFreeModel(enabled)
        }
    }

    fun setVoiceFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setVoiceFeedback(enabled)
        }
    }

    fun setAutoDismissOverlay(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoDismissOverlay(enabled)
        }
    }

    fun setContinuousWake(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setContinuousWakeListening(enabled)
        }
    }

    fun toggleAssistantService() {
        val ctx = getApplication<Application>()
        if (runtimeState.value.isServiceRunning) {
            AssistantForegroundService.stopAssistant(ctx)
        } else {
            AssistantForegroundService.startAssistant(ctx)
        }
    }

    fun triggerVoiceListen() {
        val ctx = getApplication<Application>()
        AssistantForegroundService.triggerActiveListen(ctx)
    }

    fun sendTextCommand(command: String) {
        val clean = command.trim()
        if (clean.isBlank()) return
        val ctx = getApplication<Application>()

        if (runtimeState.value.isServiceRunning) {
            AssistantForegroundService.executeTextCommand(ctx, clean)
        } else {
            viewModelScope.launch {
                val res = app.agentOrchestrator.handleCommand(clean)
                if (settings.value.voiceFeedbackEnabled) {
                    localVoiceEngine.speakAndAwait(res.replyUzbek)
                } else {
                    AssistantRuntimeState.setPhase(OrbPhase.SPEAKING)
                    delay(1200L)
                }
                AssistantRuntimeState.setPhase(OrbPhase.IDLE)
            }
        }
    }

    fun testVoiceOutput() {
        val wake = settings.value.wakeWord
        localVoiceEngine.speakFireAndForget("Assalomu alaykum! Men $wake, sizning aqlli ovozli yordamchingizman.")
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        localVoiceEngine.shutdown()
    }
}

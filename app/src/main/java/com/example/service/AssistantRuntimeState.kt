package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OrbPhase {
    IDLE,
    PASSIVE_WAKE_LISTENING,
    ACTIVE_LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING,
    ERROR
}

data class AssistantRuntimeUiState(
    val isServiceRunning: Boolean = false,
    val orbPhase: OrbPhase = OrbPhase.IDLE,
    val audioLevel: Float = 0f,
    val wakeWord: String = "Telefonim",
    val wakeWords: List<String> = listOf("Telefonim"),
    val liveTranscript: String = "",
    val aiResponse: String = "",
    val lastActionSummary: String = "",
    val isOverlayVisible: Boolean = false,
    val temporarilyYieldForUnlock: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val countdownSeconds: Int? = null,
    val isOnline: Boolean = true
)

object AssistantRuntimeState {
    private val _state = MutableStateFlow(AssistantRuntimeUiState())
    val state: StateFlow<AssistantRuntimeUiState> = _state.asStateFlow()

    fun update(transform: (AssistantRuntimeUiState) -> AssistantRuntimeUiState) {
        _state.update(transform)
    }

    fun setAudioLevel(level: Float) {
        _state.update { it.copy(audioLevel = level.coerceIn(0f, 1f)) }
    }

    fun setPhase(phase: OrbPhase) {
        _state.update { it.copy(orbPhase = phase) }
    }
}

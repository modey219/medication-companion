package com.medcompanion.app.feature.assistant

import com.medcompanion.app.domain.assistant.AssistantError

/** One bubble in the conversation; kept in memory only and never stored. */
data class AssistantMessage(
    val id: Long,
    val fromPatient: Boolean,
    val text: String = "",
    /** The backend recognised an urgent symptom; renders the emergency card. */
    val isEmergency: Boolean = false,
)

/** Immutable UI state for the Assistant tab (T10). */
data class AssistantUiState(
    val isLoading: Boolean = true,
    /** The patient read and accepted the first-use consent. */
    val hasConsented: Boolean = false,
    /** The consent text was opened again from the chat to change the switch. */
    val isConsentReopened: Boolean = false,
    val shareMedications: Boolean = false,
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    /** The typed text matches the urgent list; shown before anything is sent. */
    val isUrgentInput: Boolean = false,
    val emergencyNumber: String = "",
    /** The text that failed, so the retry button can send it again. */
    val failedText: String? = null,
    val error: AssistantError? = null,
) {
    /** The consent replaces the chat until it is accepted. */
    val showConsent: Boolean
        get() = !hasConsented || isConsentReopened

    val canSend: Boolean
        get() = input.isNotBlank() && !isSending && !isUrgentInput

    val canRetry: Boolean
        get() = failedText != null && !isSending

    val canClear: Boolean
        get() = messages.isNotEmpty() || error != null
}

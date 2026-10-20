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

/** Immutable UI state for the Assistant tab (T10, T13). */
data class AssistantUiState(
    val isLoading: Boolean = true,
    /** The patient read and accepted the first-use consent. */
    val hasConsented: Boolean = false,
    /** The consent text was opened again from the chat to change a choice. */
    val isConsentReopened: Boolean = false,
    /**
     * The patient agreed to personalize answers with their health profile
     * (T13). Off until they turn it on; while off every question is generic.
     */
    val personalizeConsent: Boolean = false,
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    /** The typed text matches the urgent list; shown before anything is sent. */
    val isUrgentInput: Boolean = false,
    val emergencyNumber: String = "",
    /**
     * The health profile could not be read, so a personalized question was not
     * sent. Nothing leaves the device in this state (T13).
     */
    val contextFailed: Boolean = false,
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

    /** True while answers are generic because the profile is not shared. */
    val isGeneric: Boolean
        get() = !personalizeConsent
}

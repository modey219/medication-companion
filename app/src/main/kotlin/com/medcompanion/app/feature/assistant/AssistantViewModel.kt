package com.medcompanion.app.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.emergency.EmergencySymptoms
import com.medcompanion.app.data.assistant.AssistantMedicationContextBuilder
import com.medcompanion.app.domain.assistant.AssistantAuthor
import com.medcompanion.app.domain.assistant.AssistantChatClient
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantExchange
import com.medcompanion.app.domain.assistant.AssistantReply
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
import com.medcompanion.app.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Assistant tab (T10): the chat with the backend proxy.
 *
 * The conversation is held in memory only, so it disappears with the process
 * and is never written to disk. Two safety rules live here as well as on the
 * server:
 *
 * - the urgent keyword check runs on the device before anything is sent, and
 *   while it matches nothing leaves the phone;
 * - a failure is turned into a calm sentence with a retry, never a crash.
 *
 * The medication block is attached only when the patient turned sharing on and
 * only through [AssistantMedicationContextBuilder].
 */
@HiltViewModel
class AssistantViewModel
    @Inject
    constructor(
        private val chatClient: AssistantChatClient,
        private val settingsRepository: SettingsRepository,
        private val medicationContextBuilder: AssistantMedicationContextBuilder,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AssistantUiState())
        val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

        private var nextMessageId = 0L

        init {
            viewModelScope.launch {
                settingsRepository.observe().collect { settings ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasConsented = settings.assistantConsentCompleted,
                            shareMedications = settings.shareMedicationsWithAssistant,
                            emergencyNumber = settings.emergencyNumber,
                        )
                    }
                }
            }
        }

        /** The patient read the consent and may chat from now on. */
        fun onConsentAccepted() {
            viewModelScope.launch { settingsRepository.completeAssistantConsent() }
        }

        /** Opens the consent text again so the sharing switch can be changed. */
        fun onConsentReopened() = _uiState.update { it.copy(isConsentReopened = true) }

        fun onConsentClosed() = _uiState.update { it.copy(isConsentReopened = false) }

        fun onShareMedicationsChanged(share: Boolean) {
            viewModelScope.launch { settingsRepository.setShareMedicationsWithAssistant(share) }
        }

        fun onInputChange(text: String) {
            val urgent = EmergencySymptoms.isEmergencyText(text)
            _uiState.update {
                it.copy(
                    input = text,
                    isUrgentInput = urgent,
                    // A new sentence clears a previous failure, and an urgent one
                    // shows the emergency card straight away.
                    error = null,
                    failedText = null,
                )
            }
        }

        fun onSend() {
            val state = _uiState.value
            val text = state.input.trim()
            if (text.isEmpty() || state.isSending) return
            if (EmergencySymptoms.isEmergencyText(text)) {
                _uiState.update { it.copy(isUrgentInput = true) }
                return
            }

            val patientMessage = AssistantMessage(id = nextId(), fromPatient = true, text = text)
            _uiState.update {
                it.copy(
                    messages = it.messages + patientMessage,
                    input = "",
                    isUrgentInput = false,
                    error = null,
                    failedText = null,
                )
            }
            viewModelScope.launch {
                val request = buildRequest(state.messages, state.shareMedications, patientTurn = text)
                dispatch(request, retryText = text)
            }
        }

        /** Sends the failed text again without adding a second bubble. */
        fun onRetry() {
            val state = _uiState.value
            val failedText = state.failedText ?: return
            if (state.isSending) return

            val cleaned = state.messages.filterNot { it.isEmergency }
            _uiState.update { it.copy(messages = cleaned, failedText = null, error = null) }
            viewModelScope.launch {
                val request = buildRequest(cleaned, state.shareMedications, patientTurn = null)
                dispatch(request, retryText = failedText)
            }
        }

        fun onClearConversation() {
            _uiState.update { it.copy(messages = emptyList(), error = null, failedText = null, isUrgentInput = false) }
        }

        fun onErrorDismissed() = _uiState.update { it.copy(error = null, failedText = null) }

        private fun dispatch(
            request: AssistantRequest,
            retryText: String,
        ) {
            _uiState.update { it.copy(isSending = true, error = null) }
            viewModelScope.launch {
                val result =
                    try {
                        chatClient.send(request)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        AssistantResult.Failed(AssistantError.SERVER)
                    }
                when (result) {
                    is AssistantResult.Replied ->
                        _uiState.update { state ->
                            state.copy(
                                isSending = false,
                                messages = state.messages + result.reply.toMessage(),
                            )
                        }

                    is AssistantResult.Failed ->
                        _uiState.update {
                            it.copy(isSending = false, error = result.error, failedText = retryText)
                        }
                }
            }
        }

        /**
         * Builds the request from the messages on screen. [patientTurn] is the
         * new message, or null on a retry because it is already in the list.
         */
        private suspend fun buildRequest(
            messages: List<AssistantMessage>,
            shareMedications: Boolean,
            patientTurn: String?,
        ): AssistantRequest {
            val exchanges =
                messages
                    .filterNot { it.isEmergency }
                    .map { AssistantExchange(author = it.author(), text = it.text) }
                    .let { history ->
                        if (patientTurn == null) {
                            history
                        } else {
                            history + AssistantExchange(AssistantAuthor.PATIENT, patientTurn)
                        }
                    }

            val context =
                if (shareMedications) {
                    // A failure while reading the list must not stop the question:
                    // the patient then simply asks without their medication block.
                    runCatching { medicationContextBuilder.build() }.getOrNull()
                } else {
                    null
                }
            return AssistantRequest(
                exchanges = exchanges,
                shareMedications = shareMedications,
                medicationContext = context,
            )
        }

        private fun nextId(): Long = nextMessageId++

        private fun AssistantMessage.author(): AssistantAuthor =
            if (fromPatient) AssistantAuthor.PATIENT else AssistantAuthor.ASSISTANT

        private fun AssistantReply.toMessage(): AssistantMessage =
            when (this) {
                is AssistantReply.Answer ->
                    AssistantMessage(id = nextId(), fromPatient = false, text = text)

                AssistantReply.Emergency ->
                    AssistantMessage(id = nextId(), fromPatient = false, isEmergency = true)
            }
    }

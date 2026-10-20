package com.medcompanion.app.feature.assistant

import com.medcompanion.app.data.assistant.AssistantMedicationContextBuilder
import com.medcompanion.app.data.assistant.PatientContextBuilder
import com.medcompanion.app.domain.assistant.AssistantChatClient
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantMode
import com.medcompanion.app.domain.assistant.AssistantReply
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
import com.medcompanion.app.domain.assistant.AssistantReview
import com.medcompanion.app.domain.assistant.AssistantUrgency
import com.medcompanion.app.domain.assistant.PatientContext
import com.medcompanion.app.domain.settings.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Assistant tab (T10, T13). Three things must never regress: nothing about the
 * patient travels unless they turned the profile consent on, a personalized
 * question is never sent without its context, and a failure never sends an
 * urgent-symptom message to the server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {
    private val chatClient = RecordingChatClient()
    private val contextBuilder = mockk<PatientContextBuilder>(relaxed = true)
    private val medicationContextBuilder = mockk<AssistantMedicationContextBuilder>(relaxed = true)
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { contextBuilder.build() } returns SAMPLE_CONTEXT
        coEvery { medicationContextBuilder.build() } returns MEDICATION_CONTEXT
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AssistantViewModel(chatClient, settings, contextBuilder, medicationContextBuilder)

    @Test
    fun `the consent is shown until it is accepted`() {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.showConsent)

        viewModel.onConsentAccepted()

        assertFalse(viewModel.uiState.value.showConsent)
        assertTrue(settings.current.assistantConsentCompleted)
    }

    @Test
    fun `an accepted consent can be reopened to change sharing`() {
        val viewModel = viewModel()
        viewModel.onConsentAccepted()

        viewModel.onConsentReopened()
        assertTrue(viewModel.uiState.value.showConsent)

        viewModel.onConsentClosed()
        assertFalse(viewModel.uiState.value.showConsent)
    }

    @Test
    fun `sending shows the question and then the answer`() {
        val viewModel = viewModel()
        viewModel.onConsentAccepted()
        viewModel.onInputChange("  متى آخذ دوائي؟  ")

        viewModel.onSend()

        val state = viewModel.uiState.value
        assertEquals(listOf(true, false), state.messages.map { it.fromPatient })
        assertEquals("متى آخذ دوائي؟", state.messages.first().text)
        assertEquals(ANSWER, state.messages.last().text)
        assertEquals("", state.input)
        assertFalse(state.isSending)
        assertNull(state.error)
    }

    @Test
    fun `a question without the profile consent is generic and carries nothing`() {
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")

        viewModel.onSend()

        val request = chatClient.requests.single()
        assertEquals(AssistantMode.GENERIC, request.mode)
        assertNull(request.context)
        assertFalse(request.shareMedications)
        assertNull(request.medicationContext)
        coVerify(exactly = 0) { contextBuilder.build() }
    }

    @Test
    fun `with the profile consent the question is personalized with the context`() {
        runBlocking { settings.grantAssistantProfileConsent() }
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")

        viewModel.onSend()

        val request = chatClient.requests.single()
        assertEquals(AssistantMode.PERSONALIZED, request.mode)
        assertEquals(SAMPLE_CONTEXT, request.context)
        assertTrue(request.shareMedications)
        assertEquals(MEDICATION_CONTEXT, request.medicationContext)
    }

    @Test
    fun `a personalized question is not sent when the profile cannot be read`() {
        runBlocking { settings.grantAssistantProfileConsent() }
        coEvery { contextBuilder.build() } throws IllegalStateException("database closed")
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")

        viewModel.onSend()

        assertTrue(chatClient.requests.isEmpty())
        val state = viewModel.uiState.value
        assertTrue(state.contextFailed)
        assertTrue(state.messages.isEmpty())
        assertEquals("متى آخذ دوائي؟", state.input)
        assertNull(state.error)
    }

    @Test
    fun `turning the profile consent off and on again is stored`() {
        val viewModel = viewModel()

        viewModel.onProfileConsentChanged(true)
        assertTrue(settings.current.assistantProfileConsent.isGranted())

        viewModel.onProfileConsentChanged(false)
        assertFalse(settings.current.assistantProfileConsent.isGranted())
    }

    @Test
    fun `a failure to read the legacy medication block still sends the question`() {
        runBlocking { settings.grantAssistantProfileConsent() }
        coEvery { medicationContextBuilder.build() } throws IllegalStateException("database closed")
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")

        viewModel.onSend()

        val request = chatClient.requests.single()
        assertNull(request.medicationContext)
        assertEquals(SAMPLE_CONTEXT, request.context)
        val state = viewModel.uiState.value
        val last = state.messages.last()
        assertEquals(ANSWER, last.text)
    }

    @Test
    fun `an urgent message is never sent and shows the emergency card`() {
        val viewModel = viewModel()
        viewModel.onConsentAccepted()

        viewModel.onInputChange("عندي ألم في الصدر")
        val state = viewModel.uiState.value
        assertTrue(state.isUrgentInput)
        assertFalse(state.canSend)

        viewModel.onSend()

        assertTrue(chatClient.requests.isEmpty())
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `a failure shows a calm sentence and a retry`() {
        chatClient.result = AssistantResult.Failed(AssistantError.OFFLINE)
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")

        viewModel.onSend()

        val state = viewModel.uiState.value
        assertEquals(AssistantError.OFFLINE, state.error)
        assertTrue(state.canRetry)
        assertFalse(state.isSending)
    }

    @Test
    fun `a retry sends the same question without a second bubble`() {
        chatClient.result = AssistantResult.Failed(AssistantError.OFFLINE)
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")
        viewModel.onSend()

        chatClient.result = AssistantResult.Replied(AssistantReply.Answer(ANSWER))
        viewModel.onRetry()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(2, chatClient.requests.size)
        assertEquals(ANSWER, state.messages.last().text)
        assertNull(state.error)
        assertFalse(state.canRetry)
    }

    @Test
    fun `a server emergency shows the emergency card instead of medical text`() {
        chatClient.result = AssistantResult.Replied(AssistantReply.Emergency)
        val viewModel = viewModel()
        viewModel.onInputChange("كيف أنظّم مواعيد دوائي؟")
        viewModel.onSend()

        val state = viewModel.uiState.value
        val last = state.messages.last()
        assertTrue(last.isEmergency)
        assertEquals("", last.text)
    }

    @Test
    fun `the earlier turns are sent again with the new question`() {
        val viewModel = viewModel()
        viewModel.onInputChange("السؤال الأول")
        viewModel.onSend()
        viewModel.onInputChange("السؤال الثاني")
        viewModel.onSend()

        val second = chatClient.requests[1]
        assertEquals(3, second.exchanges.size)
        assertEquals("السؤال الأول", second.exchanges.first().text)
        assertEquals("السؤال الثاني", second.exchanges.last().text)
    }

    @Test
    fun `clearing removes the conversation and any error`() {
        chatClient.result = AssistantResult.Failed(AssistantError.SERVER)
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")
        viewModel.onSend()

        viewModel.onClearConversation()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertNull(state.error)
        assertFalse(state.canClear)
    }

    @Test
    fun `dismissing the sentence drops the retry`() {
        chatClient.result = AssistantResult.Failed(AssistantError.TIMEOUT)
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")
        viewModel.onSend()

        viewModel.onErrorDismissed()

        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `typing a new sentence clears the previous failure`() {
        chatClient.result = AssistantResult.Failed(AssistantError.OFFLINE)
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")
        viewModel.onSend()

        viewModel.onInputChange("سؤال آخر")

        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `the answer keeps the urgency and the review`() {
        chatClient.result =
            AssistantResult.Replied(
                AssistantReply.Answer(
                    text = ANSWER,
                    urgency = AssistantUrgency.SEE_DOCTOR_SOON,
                    review =
                        AssistantReview(
                            considered = listOf("جدولك"),
                            gaps = listOf("ضغط الدم"),
                            questions = listOf("هل أحتاج تعديلًا؟"),
                        ),
                ),
            )
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")

        viewModel.onSend()

        val state = viewModel.uiState.value
        val last = state.messages.last()
        assertEquals(AssistantUrgency.SEE_DOCTOR_SOON, last.urgency)
        assertEquals(listOf("جدولك"), last.review?.considered)
        assertEquals(listOf("ضغط الدم"), last.review?.gaps)
        assertEquals(listOf("هل أحتاج تعديلًا؟"), last.review?.questions)
    }

    @Test
    fun `an answer without a review shows no cards`() {
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")

        viewModel.onSend()

        val state = viewModel.uiState.value
        val last = state.messages.last()
        assertNull(last.urgency)
        assertNull(last.review)
    }

    @Test
    fun `the review panel opens and closes for one answer`() {
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")
        viewModel.onSend()
        val state = viewModel.uiState.value
        val id = state.messages.last().id

        viewModel.onReviewToggled(id)
        assertTrue(id in viewModel.uiState.value.expandedReviews)

        viewModel.onReviewToggled(id)
        assertFalse(id in viewModel.uiState.value.expandedReviews)
    }

    @Test
    fun `clearing the conversation closes every review panel`() {
        val viewModel = viewModel()
        viewModel.onInputChange("متى آخذ دوائي؟")
        viewModel.onSend()
        val state = viewModel.uiState.value
        val id = state.messages.last().id
        viewModel.onReviewToggled(id)

        viewModel.onClearConversation()

        val expanded = viewModel.uiState.value.expandedReviews
        assertTrue(expanded.isEmpty())
    }

    private class RecordingChatClient : AssistantChatClient {
        val requests = mutableListOf<AssistantRequest>()
        var result: AssistantResult = AssistantResult.Replied(AssistantReply.Answer(ANSWER))

        override suspend fun send(request: AssistantRequest): AssistantResult {
            requests += request
            return result
        }
    }

    private companion object {
        const val ANSWER = "حسب ما أدخلته، موعدك القادم الساعة الثامنة مساءً."
        const val MEDICATION_CONTEXT = "The patient's own medication list"
        val SAMPLE_CONTEXT = PatientContext(generatedAt = Instant.parse("2026-10-20T10:00:00Z"))
    }
}

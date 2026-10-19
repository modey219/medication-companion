package com.medcompanion.app.data.assistant

import com.medcompanion.app.domain.assistant.AssistantAuthor
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantExchange
import com.medcompanion.app.domain.assistant.AssistantReply
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The wire shape of `POST /v1/chat` (backend/README.md). A change on the
 * backend must fail here first, not on a patient's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AssistantPayloadsTest {
    @Test
    fun `the body carries the turns in order and the sharing flag`() {
        val body =
            AssistantPayloads.requestBody(
                AssistantRequest(
                    exchanges =
                        listOf(
                            AssistantExchange(AssistantAuthor.PATIENT, "سؤال"),
                            AssistantExchange(AssistantAuthor.ASSISTANT, "جواب"),
                        ),
                    shareMedications = false,
                    medicationContext = null,
                ),
            )

        val json = JSONObject(body)
        val messages = json.getJSONArray("messages")
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("سؤال", messages.getJSONObject(0).getString("content"))
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertFalse(json.getBoolean("shareMedications"))
        assertFalse(json.has("medicationContext"))
    }

    @Test
    fun `the medication block travels only when sharing is on`() {
        val body =
            AssistantPayloads.requestBody(
                AssistantRequest(
                    exchanges = listOf(AssistantExchange(AssistantAuthor.PATIENT, "سؤال")),
                    shareMedications = true,
                    medicationContext = "MEDICATION BLOCK",
                ),
            )

        val json = JSONObject(body)
        assertTrue(json.getBoolean("shareMedications"))
        assertEquals("MEDICATION BLOCK", json.getString("medicationContext"))
    }

    @Test
    fun `an empty medication block is not sent even when sharing is on`() {
        val body =
            AssistantPayloads.requestBody(
                AssistantRequest(
                    exchanges = listOf(AssistantExchange(AssistantAuthor.PATIENT, "سؤال")),
                    shareMedications = true,
                    medicationContext = "   ",
                ),
            )

        assertFalse(JSONObject(body).has("medicationContext"))
    }

    @Test
    fun `a 200 answer becomes the assistant text`() {
        val result = AssistantPayloads.parseReply("""{"reply":"  جواب  "}""")

        assertEquals(AssistantResult.Replied(AssistantReply.Answer("جواب")), result)
    }

    @Test
    fun `the emergency flag wins over any text`() {
        val result = AssistantPayloads.parseReply("""{"reply":"text","emergency":true}""")

        assertEquals(AssistantResult.Replied(AssistantReply.Emergency), result)
    }

    @Test
    fun `an empty, missing or unreadable answer is a server failure`() {
        assertEquals(
            AssistantResult.Failed(AssistantError.SERVER),
            AssistantPayloads.parseReply("""{"reply":"   "}"""),
        )
        assertEquals(AssistantResult.Failed(AssistantError.SERVER), AssistantPayloads.parseReply(null))
        assertEquals(AssistantResult.Failed(AssistantError.SERVER), AssistantPayloads.parseReply("not json"))
        assertEquals(AssistantResult.Failed(AssistantError.SERVER), AssistantPayloads.parseReply(""))
    }

    @Test
    fun `the status codes map to the sentences the patient sees`() {
        assertEquals(AssistantError.RATE_LIMITED, AssistantPayloads.errorForStatus(429))
        assertEquals(AssistantError.INVALID, AssistantPayloads.errorForStatus(400))
        assertEquals(AssistantError.INVALID, AssistantPayloads.errorForStatus(401))
        assertEquals(AssistantError.SERVER, AssistantPayloads.errorForStatus(500))
        assertEquals(AssistantError.SERVER, AssistantPayloads.errorForStatus(502))
        assertEquals(null, AssistantPayloads.errorForStatus(200))
    }
}

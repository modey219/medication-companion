package com.medcompanion.app.data.assistant

import com.medcompanion.app.domain.assistant.AssistantAuthor
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantExchange
import com.medcompanion.app.domain.assistant.AssistantMode
import com.medcompanion.app.domain.assistant.AssistantReply
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
import com.medcompanion.app.domain.assistant.AssistantReview
import com.medcompanion.app.domain.assistant.AssistantUrgency
import com.medcompanion.app.domain.assistant.PatientAdherence
import com.medcompanion.app.domain.assistant.PatientContext
import com.medcompanion.app.domain.assistant.PatientContextEntry
import com.medcompanion.app.domain.assistant.PatientContextHistory
import com.medcompanion.app.domain.assistant.PatientContextMedication
import com.medcompanion.app.domain.assistant.PatientContextSymptom
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.model.Sex
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

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
        assertEquals("generic", json.getString("mode"))
        assertFalse(json.has("context"))
        assertFalse(json.getBoolean("shareMedications"))
        assertFalse(json.has("medicationContext"))
    }

    @Test
    fun `a personalized request carries the whole context`() {
        val body =
            AssistantPayloads.requestBody(
                AssistantRequest(
                    exchanges = listOf(AssistantExchange(AssistantAuthor.PATIENT, "سؤال")),
                    mode = AssistantMode.PERSONALIZED,
                    context = sampleContext(),
                    shareMedications = true,
                ),
            )

        val json = JSONObject(body)
        assertEquals("personalized", json.getString("mode"))
        val context = json.getJSONObject("context")
        assertEquals("2026-10-20T10:00:00Z", context.getString("generatedAt"))
        assertEquals(62, context.getInt("ageYears"))
        assertEquals("FEMALE", context.getString("sex"))
        assertFalse(context.has("pregnantOrBreastfeeding"))

        val allergy = context.getJSONArray("allergies").getJSONObject(0)
        assertEquals("بنسلين", allergy.getString("label"))
        assertEquals(2, allergy.getInt("severity"))
        assertEquals("طفح جلدي", allergy.getString("reaction"))

        val history = context.getJSONArray("medicalHistory").getJSONObject(0)
        assertEquals("SURGERY", history.getString("type"))
        assertEquals(2020, history.getInt("year"))

        val medication = context.getJSONArray("medications").getJSONObject(0)
        assertEquals("ميتفورمين", medication.getString("name"))
        assertEquals("ACTIVE", medication.getString("status"))
        assertEquals(1, medication.getJSONArray("schedules").length())

        assertEquals("وصفة الطبيب", context.getJSONArray("stoppedLast30Days").getJSONObject(0).getString("stopReason"))

        val adherence = context.getJSONObject("adherenceLast14Days")
        assertEquals(12, adherence.getInt("taken"))
        assertEquals(3, adherence.getInt("missed"))
        assertEquals(20, adherence.getInt("recorded"))

        assertEquals(3, context.getJSONArray("recentSymptoms").getJSONObject(0).getInt("severity"))

        val sections = context.getJSONObject("sections")
        assertEquals("LISTED", sections.getString("allergies"))
        assertEquals("NOT_ASKED", sections.getString("conditions"))
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
        val result = AssistantPayloads.parseReply("""{"reply":"  اذهب  "}""")

        assertEquals(AssistantResult.Replied(AssistantReply.Answer("اذهب")), result)
    }

    @Test
    fun `the urgency and the review are read from the answer`() {
        val result =
            AssistantPayloads.parseReply(
                """{"reply":"نص","urgency":"URGENT_TODAY","review":""" +
                    """{"considered":["جدولك"],"gaps":["ضغط الدم"],"questions":["هل أحتاج تعديلًا؟"]}}""",
            )

        assertEquals(
            AssistantResult.Replied(
                AssistantReply.Answer(
                    text = "نص",
                    urgency = AssistantUrgency.URGENT_TODAY,
                    review =
                        AssistantReview(
                            considered = listOf("جدولك"),
                            gaps = listOf("ضغط الدم"),
                            questions = listOf("هل أحتاج تعديلًا؟"),
                        ),
                ),
            ),
            result,
        )
    }

    @Test
    fun `an answer from an older Worker has no urgency and no review`() {
        val result = AssistantPayloads.parseReply("""{"reply":"نص"}""")

        assertEquals(AssistantResult.Replied(AssistantReply.Answer("نص")), result)
    }

    @Test
    fun `an unknown urgency is ignored rather than guessed`() {
        val answer = AssistantPayloads.parseReply("""{"reply":"نص","urgency":"SOON"}""") as AssistantResult.Replied
        val reply = answer.reply as AssistantReply.Answer

        assertEquals(null, reply.urgency)
    }

    @Test
    fun `an empty review is not shown`() {
        val result =
            AssistantPayloads.parseReply(
                """{"reply":"نص","review":{"considered":[],"gaps":[],"questions":[]}}""",
            )
        val answer = result as AssistantResult.Replied
        val reply = answer.reply as AssistantReply.Answer

        assertEquals(null, reply.review)
    }

    @Test
    fun `a malformed review does not hide the answer`() {
        val result = AssistantPayloads.parseReply("""{"reply":"نص","review":"نص حر"}""")
        val answer = result as AssistantResult.Replied
        val reply = answer.reply as AssistantReply.Answer

        assertEquals("نص", reply.text)
        assertEquals(null, reply.review)
    }

    @Test
    fun `review bullets are trimmed, dropped when blank, and capped`() {
        val long = "x".repeat(400)
        val bullets = listOf(long, "  أول  ", "", "   ") + List(10) { "بند $it" }
        val json =
            """{"reply":"نص","review":{"considered":""" +
                bullets.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" } +
                "}}"

        val answer = AssistantPayloads.parseReply(json) as AssistantResult.Replied
        val review = (answer.reply as AssistantReply.Answer).review

        requireNotNull(review)
        assertEquals(8, review.considered.size)
        assertEquals(300, review.considered.first().length)
        assertEquals("أول", review.considered[1])
    }

    @Test
    fun `the emergency flag wins over a review`() {
        val result =
            AssistantPayloads.parseReply(
                """{"reply":"نص","emergency":true,"urgency":"URGENT_TODAY","review":{"considered":["x"]}}""",
            )

        assertEquals(AssistantResult.Replied(AssistantReply.Emergency), result)
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

    private fun sampleContext() =
        PatientContext(
            generatedAt = Instant.parse("2026-10-20T10:00:00Z"),
            ageYears = 62,
            sex = Sex.FEMALE,
            weightKg = 70,
            allergies =
                listOf(
                    PatientContextEntry(label = "بنسلين", severity = 2, reaction = "طفح جلدي", sinceYear = 2019),
                ),
            conditions = listOf(PatientContextEntry(label = "سكري")),
            medicalHistory =
                listOf(
                    PatientContextHistory(
                        type = MedicalHistoryType.SURGERY,
                        label = "استئصال المرارة",
                        year = 2020,
                    ),
                ),
            medications =
                listOf(
                    PatientContextMedication(
                        name = "ميتفورمين",
                        strength = "500 ملغ",
                        status = MedicationStatus.ACTIVE,
                        schedules = listOf("dose 1 قرص, food WITH, at 08:00"),
                    ),
                ),
            stoppedLast30Days =
                listOf(
                    PatientContextMedication(
                        name = "أسبرين",
                        strength = "",
                        status = MedicationStatus.STOPPED,
                        stopReason = "وصفة الطبيب",
                    ),
                ),
            adherenceLast14Days = PatientAdherence(taken = 12, missed = 3, recorded = 20),
            recentSymptoms =
                listOf(
                    PatientContextSymptom(
                        name = "صداع",
                        severity = 3,
                        loggedAt = Instant.parse("2026-10-15T09:00:00Z"),
                    ),
                ),
            sections =
                mapOf(
                    ProfileSection.ALLERGIES to ProfileSectionStatus.LISTED,
                    ProfileSection.CONDITIONS to ProfileSectionStatus.NOT_ASKED,
                ),
        )
}

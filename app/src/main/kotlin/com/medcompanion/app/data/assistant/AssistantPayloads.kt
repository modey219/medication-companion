package com.medcompanion.app.data.assistant

import com.medcompanion.app.domain.assistant.AssistantAuthor
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantReply
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
import com.medcompanion.app.domain.assistant.AssistantReview
import com.medcompanion.app.domain.assistant.AssistantUrgency
import com.medcompanion.app.domain.assistant.PatientContext
import com.medcompanion.app.domain.assistant.PatientContextEntry
import com.medcompanion.app.domain.assistant.PatientContextHistory
import com.medcompanion.app.domain.assistant.PatientContextMedication
import com.medcompanion.app.domain.assistant.PatientContextSymptom
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * The wire format of the assistant endpoint (`POST /v1/chat`, backend/README.md).
 *
 * Kept separate from the transport so the shape can be tested without a socket,
 * and so a future change on the backend has exactly one place to touch here.
 */
internal object AssistantPayloads {
    const val ENDPOINT_PATH = "/v1/chat"

    /**
     * The JSON body. [AssistantRequest.mode] always travels; the structured
     * context travels only in personalized mode and the legacy medication block
     * only while the patient shares it.
     */
    fun requestBody(request: AssistantRequest): String {
        val messages = JSONArray()
        request.exchanges.forEach { exchange ->
            messages.put(
                JSONObject()
                    .put("role", role(exchange.author))
                    .put("content", exchange.text),
            )
        }
        val body =
            JSONObject()
                .put("messages", messages)
                .put("mode", request.mode.wireName)
                // TODO(eng, T14): drop these two once the deployed T9 Worker
                // accepts `mode` and `context`.
                .put("shareMedications", request.shareMedications)
        if (request.shareMedications) {
            request.medicationContext
                ?.takeIf { it.isNotBlank() }
                ?.let { body.put("medicationContext", it) }
        }
        request.context?.let { body.put("context", contextJson(it)) }
        return body.toString()
    }

    /** The structured patient context; absent fields are simply left out. */
    private fun contextJson(context: PatientContext): JSONObject =
        JSONObject().apply {
            put("generatedAt", context.generatedAt.toString())
            context.ageYears?.let { put("ageYears", it) }
            context.sex?.let { put("sex", it.name) }
            context.pregnantOrBreastfeeding?.let { put("pregnantOrBreastfeeding", it) }
            context.weightKg?.let { put("weightKg", it) }
            put("allergies", entries(context.allergies))
            put("conditions", entries(context.conditions))
            put("medicalHistory", history(context.medicalHistory))
            put("medications", medications(context.medications))
            put("stoppedLast30Days", medications(context.stoppedLast30Days))
            context.adherenceLast14Days?.let { adherence ->
                put(
                    "adherenceLast14Days",
                    JSONObject()
                        .put("taken", adherence.taken)
                        .put("missed", adherence.missed)
                        .put("recorded", adherence.recorded),
                )
            }
            put("recentSymptoms", symptoms(context.recentSymptoms))
            put("sections", sections(context.sections))
        }

    private fun entries(items: List<PatientContextEntry>): JSONArray =
        JSONArray().apply {
            items.forEach { entry ->
                put(
                    JSONObject()
                        .apply {
                            put("label", entry.label)
                            entry.severity?.let { put("severity", it) }
                            entry.reaction?.let { put("reaction", it) }
                            entry.sinceYear?.let { put("sinceYear", it) }
                        },
                )
            }
        }

    private fun history(items: List<PatientContextHistory>): JSONArray =
        JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .apply {
                            put("type", item.type.name)
                            put("label", item.label)
                            item.year?.let { put("year", it) }
                        },
                )
            }
        }

    private fun medications(items: List<PatientContextMedication>): JSONArray =
        JSONArray().apply {
            items.forEach { medication ->
                put(
                    JSONObject()
                        .apply {
                            put("name", medication.name)
                            put("strength", medication.strength)
                            put("status", medication.status.name)
                            medication.stopReason?.let { put("stopReason", it) }
                            put("schedules", JSONArray(medication.schedules))
                        },
                )
            }
        }

    private fun symptoms(items: List<PatientContextSymptom>): JSONArray =
        JSONArray().apply {
            items.forEach { symptom ->
                put(
                    JSONObject()
                        .apply {
                            put("name", symptom.name)
                            put("severity", symptom.severity)
                            put("loggedAt", symptom.loggedAt.toString())
                        },
                )
            }
        }

    private fun sections(statuses: Map<ProfileSection, ProfileSectionStatus>): JSONObject =
        JSONObject().apply {
            statuses.forEach { (section, status) -> put(section.name.lowercase(), status.name) }
        }

    /**
     * Parses a 200 body; a missing or empty answer becomes a server failure.
     *
     * The urgency and the review are read only when the backend sent them. A
     * Worker built before T14 sends neither, and a malformed section is dropped
     * so a bad response can never hide the answer or invent a level.
     */
    fun parseReply(body: String?): AssistantResult {
        val json =
            runCatching { JSONObject(body.orEmpty()) }
                .getOrElse { return AssistantResult.Failed(AssistantError.SERVER) }
        if (json.optBoolean("emergency", false)) return AssistantResult.Replied(AssistantReply.Emergency)

        val reply = json.optString("reply").trim()
        if (reply.isEmpty()) return AssistantResult.Failed(AssistantError.SERVER)

        val urgency = AssistantUrgency.fromWire(json.optString("urgency").trim())
        val reviewJson = json.optJSONObject("review")
        val review = reviewJson?.toReview()?.takeUnless { it.isEmpty }
        return AssistantResult.Replied(AssistantReply.Answer(reply, urgency, review))
    }

    /** The review card, capped locally as well as on the server. */
    private fun JSONObject.toReview(): AssistantReview =
        AssistantReview(
            considered = bullets("considered"),
            gaps = bullets("gaps"),
            questions = bullets("questions"),
        )

    private fun JSONObject.bullets(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index).trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.length > MAX_REVIEW_CHARS) it.take(MAX_REVIEW_CHARS) else it }
            .take(MAX_REVIEW_BULLETS)
    }

    /** Hard display cap; the server caps its own review at 5 bullets per group. */
    private const val MAX_REVIEW_BULLETS = 8

    /** Hard display cap; the server caps its own phrases at 160 characters. */
    private const val MAX_REVIEW_CHARS = 300

    /**
     * Maps a non-200 status to the error the screen shows. Returns null for a
     * status we do not expect, which the caller treats as a server failure.
     */
    fun errorForStatus(status: Int): AssistantError? =
        when (status) {
            429 -> AssistantError.RATE_LIMITED
            in 400..499 -> AssistantError.INVALID
            in 500..599 -> AssistantError.SERVER
            else -> null
        }

    private fun role(author: AssistantAuthor): String =
        when (author) {
            AssistantAuthor.PATIENT -> "user"
            AssistantAuthor.ASSISTANT -> "assistant"
        }
}

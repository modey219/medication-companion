package com.medcompanion.app.data.assistant

import com.medcompanion.app.domain.assistant.AssistantAuthor
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantReply
import com.medcompanion.app.domain.assistant.AssistantRequest
import com.medcompanion.app.domain.assistant.AssistantResult
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

    /** The JSON body. The medication block travels only when sharing is on. */
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
                .put("shareMedications", request.shareMedications)
        if (request.shareMedications) {
            request.medicationContext
                ?.takeIf { it.isNotBlank() }
                ?.let { body.put("medicationContext", it) }
        }
        return body.toString()
    }

    /** Parses a 200 body; a missing or empty answer becomes a server failure. */
    fun parseReply(body: String?): AssistantResult {
        val json =
            runCatching { JSONObject(body.orEmpty()) }
                .getOrElse { return AssistantResult.Failed(AssistantError.SERVER) }
        if (json.optBoolean("emergency", false)) return AssistantResult.Replied(AssistantReply.Emergency)

        val reply = json.optString("reply").trim()
        return if (reply.isEmpty()) {
            AssistantResult.Failed(AssistantError.SERVER)
        } else {
            AssistantResult.Replied(AssistantReply.Answer(reply))
        }
    }

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

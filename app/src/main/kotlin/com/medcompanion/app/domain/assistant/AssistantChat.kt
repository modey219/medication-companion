package com.medcompanion.app.domain.assistant

/**
 * Who wrote one turn of the assistant conversation.
 *
 * The conversation lives in memory only: the app never stores it and the
 * backend never keeps it (AGENTS.md > AI Assistant).
 */
enum class AssistantAuthor {
    PATIENT,
    ASSISTANT,
}

/** One turn of the conversation. */
data class AssistantExchange(
    val author: AssistantAuthor,
    val text: String,
)

/**
 * What the app sends to the assistant backend.
 *
 * [medicationContext] is only non-null when the patient turned on sharing, and
 * [AssistantExchange]s are exactly what is on screen: nothing else leaves the
 * device.
 */
data class AssistantRequest(
    val exchanges: List<AssistantExchange>,
    val shareMedications: Boolean,
    val medicationContext: String?,
)

/** What the backend answered. */
sealed interface AssistantReply {
    /** A normal answer to show in the chat. */
    data class Answer(
        val text: String,
    ) : AssistantReply

    /**
     * The server recognised an urgent symptom. The screen shows the emergency
     * card and never treats this as medical guidance.
     */
    data object Emergency : AssistantReply
}

/**
 * Why a request did not produce an answer. Each value maps to a calm, plain
 * sentence; none of them blames the patient or suggests a medical action.
 */
enum class AssistantError {
    /** No connection; the patient can try again later. */
    OFFLINE,

    /** The answer took too long. */
    TIMEOUT,

    /** Too many questions in a short time; ask again later. */
    RATE_LIMITED,

    /** The assistant service failed; the app offers a retry. */
    SERVER,

    /** The request was rejected; kept for completeness, not expected on device. */
    INVALID,
}

/** The outcome of one send. */
sealed interface AssistantResult {
    data class Replied(
        val reply: AssistantReply,
    ) : AssistantResult

    data class Failed(
        val error: AssistantError,
    ) : AssistantResult
}

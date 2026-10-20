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
 * How the assistant should answer (T13, D-021): [GENERIC] with no patient data
 * at all, or [PERSONALIZED] from the health profile the patient agreed to share.
 * The backend requires the matching [AssistantRequest.context] for
 * [PERSONALIZED] and rejects a personalized request that carries none.
 */
enum class AssistantMode(
    val wireName: String,
) {
    GENERIC("generic"),
    PERSONALIZED("personalized"),
}

/**
 * What the app sends to the assistant backend.
 *
 * [context] is only non-null in [AssistantMode.PERSONALIZED], and
 * [AssistantExchange]s are exactly what is on screen: nothing else leaves the
 * device.
 */
data class AssistantRequest(
    val exchanges: List<AssistantExchange>,
    val mode: AssistantMode = AssistantMode.GENERIC,
    /** The profile context; non-null only in [AssistantMode.PERSONALIZED]. */
    val context: PatientContext? = null,
    /**
     * Kept so a Worker deployed before T14 keeps working: it mirrors [mode] and
     * the medication block that [context] already carries.
     * TODO(eng, T14): drop `shareMedications` and `medicationContext` together
     * with the backend's legacy window.
     */
    val shareMedications: Boolean = false,
    val medicationContext: String? = null,
)

/** What the backend answered. */
sealed interface AssistantReply {
    /**
     * A normal answer to show in the chat.
     *
     * [urgency] and [review] come from the backend's review pass (T14, T15).
     * They are null when the answer came from a Worker built before T14, and
     * the app never guesses them: it shows only what the server decided.
     */
    data class Answer(
        val text: String,
        val urgency: AssistantUrgency? = null,
        val review: AssistantReview? = null,
    ) : AssistantReply

    /**
     * The server recognised an urgent symptom. The screen shows the emergency
     * card and never treats this as medical guidance.
     */
    data object Emergency : AssistantReply
}

/**
 * How urgent the backend judged the patient's last message (T15).
 *
 * The app only displays this level; it never computes it and never lowers it.
 * An unknown value from a newer backend is ignored rather than guessed.
 */
enum class AssistantUrgency {
    EMERGENCY,
    URGENT_TODAY,
    SEE_DOCTOR_SOON,
    SELF_CARE,
    ;

    companion object {
        fun fromWire(value: String?): AssistantUrgency? =
            when (value) {
                "EMERGENCY" -> EMERGENCY
                "URGENT_TODAY" -> URGENT_TODAY
                "SEE_DOCTOR_SOON" -> SEE_DOCTOR_SOON
                "SELF_CARE" -> SELF_CARE
                else -> null
            }
    }
}

/**
 * The patient-safe part of the backend's review (T14): what it looked at, what
 * was missing, and questions the patient can ask a clinician.
 *
 * The server already filters these phrases for safety and length; the app only
 * displays them and keeps its own hard cap so a bad response cannot flood the
 * screen.
 */
data class AssistantReview(
    val considered: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = considered.isEmpty() && gaps.isEmpty() && questions.isEmpty()
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

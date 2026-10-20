package com.medcompanion.app.feature.assistant

import androidx.annotation.StringRes
import com.medcompanion.app.R
import com.medcompanion.app.domain.assistant.AssistantError
import com.medcompanion.app.domain.assistant.AssistantUrgency

/**
 * The sentence shown for a failed request. Every one is calm, blames nobody and
 * never suggests a medical action (AGENTS.md > Medical Safety Rules).
 */
@StringRes
fun AssistantError.messageRes(): Int =
    when (this) {
        AssistantError.OFFLINE -> R.string.assistant_error_offline
        AssistantError.TIMEOUT -> R.string.assistant_error_timeout
        AssistantError.RATE_LIMITED -> R.string.assistant_error_rate_limited
        AssistantError.SERVER -> R.string.assistant_error_server
        AssistantError.INVALID -> R.string.assistant_error_invalid
    }

/**
 * The words for the backend's urgency level (T15). The level is shown only
 * because the server decided it; the label is framed as a level, never as
 * advice, and it is never the only signal (the text always says it too).
 *
 * TODO(clinical-review, T15): the four labels, especially "general information"
 * for SELF_CARE, which must not read as "you do not need a doctor".
 */
@StringRes
fun AssistantUrgency.labelRes(): Int =
    when (this) {
        AssistantUrgency.EMERGENCY -> R.string.assistant_urgency_emergency
        AssistantUrgency.URGENT_TODAY -> R.string.assistant_urgency_urgent_today
        AssistantUrgency.SEE_DOCTOR_SOON -> R.string.assistant_urgency_see_doctor_soon
        AssistantUrgency.SELF_CARE -> R.string.assistant_urgency_self_care
    }

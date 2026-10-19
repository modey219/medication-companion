package com.medcompanion.app.feature.assistant

import androidx.annotation.StringRes
import com.medcompanion.app.R
import com.medcompanion.app.domain.assistant.AssistantError

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

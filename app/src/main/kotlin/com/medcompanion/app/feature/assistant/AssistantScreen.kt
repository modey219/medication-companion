package com.medcompanion.app.feature.assistant

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.EmergencyCard
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Assistant tab (T10). Until the first-use consent is accepted it shows the
 * consent text; afterwards it shows the conversation with the backend proxy.
 *
 * The screen is stateless; [AssistantContent] takes the state and the actions
 * so it can be previewed and tested without a ViewModel.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssistantContent(
        state = state,
        actions = AssistantActions(viewModel),
        modifier = modifier,
    )
}

/** Every change the screen can send back, so the UI stays stateless. */
internal data class AssistantActions(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onRetry: () -> Unit,
    val onClearConversation: () -> Unit,
    val onErrorDismissed: () -> Unit,
    val onConsentAccepted: () -> Unit,
    val onConsentReopened: () -> Unit,
    val onConsentClosed: () -> Unit,
    val onProfileConsentChanged: (Boolean) -> Unit,
) {
    constructor(viewModel: AssistantViewModel) : this(
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::onSend,
        onRetry = viewModel::onRetry,
        onClearConversation = viewModel::onClearConversation,
        onErrorDismissed = viewModel::onErrorDismissed,
        onConsentAccepted = viewModel::onConsentAccepted,
        onConsentReopened = viewModel::onConsentReopened,
        onConsentClosed = viewModel::onConsentClosed,
        onProfileConsentChanged = viewModel::onProfileConsentChanged,
    )
}

@Composable
internal fun AssistantContent(
    state: AssistantUiState,
    actions: AssistantActions,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.showConsent -> AssistantConsentContent(state = state, actions = actions, modifier = modifier)

        else -> AssistantChatContent(state = state, actions = actions, modifier = modifier)
    }
}

/**
 * First-use consent (AGENTS.md > AI Assistant, AGENTS.md > Privacy). It states
 * exactly what is sent before anything leaves the device, and the personalization
 * switch starts off.
 *
 * TODO(clinical-review): the consent wording. TODO(legal): the privacy wording
 * of what the backend does with a message.
 */
@Composable
private fun AssistantConsentContent(
    state: AssistantUiState,
    actions: AssistantActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Heading(stringResource(R.string.assistant_consent_title))
        BodyText(stringResource(R.string.assistant_consent_intro))
        Heading(stringResource(R.string.assistant_consent_sent_title))
        BodyText(stringResource(R.string.assistant_consent_sent_body))
        BodyText(stringResource(R.string.assistant_consent_medicine_body))
        ProfileConsentSwitch(state = state, actions = actions)
        BodyText(stringResource(R.string.assistant_consent_privacy_note))
        Text(
            text = stringResource(R.string.notice_not_medical_advice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.isConsentReopened) {
            OutlinedButton(
                onClick = actions.onConsentClosed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.assistant_consent_close))
            }
        } else {
            Button(
                onClick = actions.onConsentAccepted,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.assistant_consent_accept))
            }
        }
    }
}

@Composable
private fun ProfileConsentSwitch(
    state: AssistantUiState,
    actions: AssistantActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.assistant_consent_profile_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            BodyText(stringResource(R.string.assistant_consent_profile_desc))
        }
        Switch(
            checked = state.personalizeConsent,
            onCheckedChange = actions.onProfileConsentChanged,
        )
    }
}

@Composable
private fun AssistantChatContent(
    state: AssistantUiState,
    actions: AssistantActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxSize()) {
        // The one-line disclaimer stays visible for as long as the chat does
        // (AGENTS.md > AI Assistant).
        Text(
            text = stringResource(R.string.assistant_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (state.isGeneric) {
            InfoBanner(stringResource(R.string.assistant_mode_generic))
        }
        if (state.personalizeConsent && state.contextFailed) {
            InfoBanner(text = stringResource(R.string.assistant_context_failed), isError = true)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty()) {
                item(key = "empty") { BodyText(stringResource(R.string.assistant_empty)) }
            }
            items(state.messages, key = { it.id }) { message ->
                if (message.isEmergency) {
                    EmergencyCard(number = state.emergencyNumber.trim())
                } else {
                    MessageBubble(
                        message = message,
                        onReport = { shareReport(context, message.text) },
                    )
                }
            }
            if (state.isSending) {
                item(key = "typing") { TypingIndicator() }
            }
        }

        if (state.isUrgentInput) {
            EmergencyCard(
                number = state.emergencyNumber.trim(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        state.error?.let { error ->
            ErrorRow(
                message = stringResource(error.messageRes()),
                canRetry = state.canRetry,
                onRetry = actions.onRetry,
                onDismiss = actions.onErrorDismissed,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                label = { Text(stringResource(R.string.assistant_input_label)) },
                placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
            )
            Button(
                onClick = actions.onSend,
                enabled = state.canSend,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.assistant_send))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = actions.onClearConversation,
                enabled = state.canClear,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.assistant_clear))
            }
            TextButton(
                onClick = actions.onConsentReopened,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.assistant_settings))
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: AssistantMessage,
    onReport: () -> Unit,
) {
    val fromPatient = message.fromPatient
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromPatient) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalAlignment = if (fromPatient) Alignment.End else Alignment.Start,
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (fromPatient) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                    ),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (!fromPatient) {
                TextButton(onClick = onReport) {
                    Text(stringResource(R.string.assistant_report))
                }
            }
        }
    }
}

/** Shown while waiting for the answer; the text is read by TalkBack. */
@Composable
private fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.heightIn(min = 24.dp))
        Text(
            text = stringResource(R.string.assistant_thinking),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ErrorRow(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (canRetry) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.assistant_retry))
            }
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_dismiss))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

/** A one-line note above the chat: the generic mode, or a failed context. */
@Composable
private fun InfoBanner(
    text: String,
    isError: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Opens the share sheet with a report about one answer. The answer text is in
 * the template because the patient tapped report on that message; nothing is
 * sent anywhere until they pick an app (AGENTS.md > AI Assistant).
 *
 * TODO(legal): confirm the report template is acceptable for health data.
 */
private fun shareReport(
    context: Context,
    answerText: String,
) {
    @Suppress("DEPRECATION")
    val versionName =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    val body =
        context.getString(
            R.string.assistant_report_template,
            versionName,
            answerText,
        )
    runCatching {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.assistant_report_share_title)),
        )
    }
}

@ScreenPreviews
@Composable
private fun AssistantChatPreview() {
    MedCompanionTheme(dynamicColor = false) {
        AssistantContent(
            state =
                AssistantUiState(
                    isLoading = false,
                    hasConsented = true,
                    messages =
                        listOf(
                            AssistantMessage(id = 1, fromPatient = true, text = "متى آخذ دوائي؟"),
                            AssistantMessage(
                                id = 2,
                                fromPatient = false,
                                text = "حسب ما أدخلته، موعدك القادم الساعة 8 مساءً.",
                            ),
                        ),
                ),
            actions = previewActions(),
        )
    }
}

@ScreenPreviews
@Composable
private fun AssistantConsentPreview() {
    MedCompanionTheme(dynamicColor = false) {
        AssistantContent(
            state = AssistantUiState(isLoading = false),
            actions = previewActions(),
        )
    }
}

private fun previewActions() =
    AssistantActions(
        onInputChange = {},
        onSend = {},
        onRetry = {},
        onClearConversation = {},
        onErrorDismissed = {},
        onConsentAccepted = {},
        onConsentReopened = {},
        onConsentClosed = {},
        onProfileConsentChanged = {},
    )

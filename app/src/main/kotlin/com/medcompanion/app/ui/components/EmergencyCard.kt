package com.medcompanion.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.medcompanion.app.R

/**
 * The card shown when the patient's own words match the urgent symptom list
 * (symptoms log, and the assistant before it sends anything).
 *
 * It urges contacting emergency services, uses the patient's local number when
 * one is set (Profile tab) and the built-in default otherwise, and never
 * diagnoses or asks a follow-up question.
 *
 * TODO(clinical-review): the emergency wording; the built-in number is still a
 * placeholder (see strings.xml).
 */
@Composable
fun EmergencyCard(
    number: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val defaultNumber = stringResource(R.string.emergency_number).trim()
    val localNumber = number.ifBlank { defaultNumber }
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.emergency_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.emergency_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (localNumber.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.emergency_number_hint, localNumber),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { dialEmergency(context, localNumber) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.emergency_call))
                }
            }
        }
    }
}

/** Opens the dialer; a missing dialer app is ignored, never a crash. */
private fun dialEmergency(
    context: Context,
    number: String,
) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri())) }
}

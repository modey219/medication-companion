package com.medcompanion.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A value the patient changes with two large buttons instead of typing a
 * number (T8b). Both buttons keep a 48dp touch target, the current value is
 * written out as text, and nothing relies on color alone (AGENTS.md >
 * Localization and UX). A button is disabled at the end of the reviewed range,
 * so an out-of-range value can never be set.
 */
@Composable
internal fun ValueStepperRow(
    title: String,
    description: String,
    valueText: String,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepperButton(
                    label = DECREASE_LABEL,
                    contentDescription = decreaseContentDescription,
                    enabled = canDecrease,
                    onClick = onDecrease,
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                StepperButton(
                    label = INCREASE_LABEL,
                    contentDescription = increaseContentDescription,
                    enabled = canIncrease,
                    onClick = onIncrease,
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier =
            Modifier
                .size(48.dp)
                .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}

private const val DECREASE_LABEL = "\u2212"
private const val INCREASE_LABEL = "+"

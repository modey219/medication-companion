package com.medcompanion.app.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.feature.medications.labelRes
import com.medcompanion.app.ui.components.formatTime
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Today tab: today's doses grouped by time with overdue highlighting and
 * Taken / Snooze / Skip actions that go through the reminder engine
 * (AGENTS.md > Part C, T4; reminder setup banner from T5).
 */
@Composable
fun TodayScreen(
    onAddMedicationClick: () -> Unit,
    onReminderSetupClick: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.error_dose_update)

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.dismissError()
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TodayContent(
            state = state,
            onTaken = viewModel::onTaken,
            onSnoozed = viewModel::onSnoozed,
            onSkipped = viewModel::onSkipped,
            onAddMedicationClick = onAddMedicationClick,
            onReminderSetupClick = onReminderSetupClick,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
        )
    }
}

@Composable
internal fun TodayContent(
    state: TodayUiState,
    onTaken: (Long) -> Unit,
    onSnoozed: (Long) -> Unit,
    onSkipped: (Long) -> Unit,
    onAddMedicationClick: () -> Unit,
    onReminderSetupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.reminderSetupIncomplete && !state.isLoading) {
            ReminderSetupBanner(onClick = onReminderSetupClick)
        }
        Box(modifier = Modifier.weight(1f)) {
            TodayBody(
                state = state,
                onTaken = onTaken,
                onSnoozed = onSnoozed,
                onSkipped = onSkipped,
                onAddMedicationClick = onAddMedicationClick,
            )
        }
    }
}

@Composable
private fun ReminderSetupBanner(onClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = stringResource(R.string.today_reminders_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .padding(16.dp)
                    .heightIn(min = 24.dp),
        )
    }
}

@Composable
private fun TodayBody(
    state: TodayUiState,
    onTaken: (Long) -> Unit,
    onSnoozed: (Long) -> Unit,
    onSkipped: (Long) -> Unit,
    onAddMedicationClick: () -> Unit,
) {
    when {
        state.isLoading ->
            CenteredBox {
                CircularProgressIndicator()
            }

        state.showAddMedicationCta ->
            CenteredColumn {
                Text(
                    text = stringResource(R.string.medications_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onAddMedicationClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.action_add_medication))
                }
            }

        state.isEmpty ->
            CenteredBox {
                Text(
                    text = stringResource(R.string.today_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header_${group.time}") {
                        TimeHeader(timeText = group.time.formatTime(LocalContext.current))
                    }
                    items(group.doses, key = { it.id }) { row ->
                        DoseCard(
                            row = row,
                            onTaken = { onTaken(row.id) },
                            onSnoozed = { onSnoozed(row.id) },
                            onSkipped = { onSkipped(row.id) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun TimeHeader(timeText: String) {
    Text(
        text = timeText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
    )
}

@Composable
private fun DoseCard(
    row: DoseRow,
    onTaken: () -> Unit,
    onSnoozed: () -> Unit,
    onSkipped: () -> Unit,
) {
    val context = LocalContext.current
    val separator = stringResource(R.string.detail_separator)
    val foodLabel =
        if (row.foodRelation != null) {
            stringResource(row.foodRelation.labelRes())
        } else {
            ""
        }
    val details =
        listOf(
            row.strength,
            row.doseAmount,
            foodLabel,
        ).filter { it.isNotBlank() }.joinToString(separator)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            if (row.isOverdue) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                DoseStatusText(row)
            }
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (row.status == DoseStatus.SNOOZED && row.snoozedUntil != null) {
                val untilTime =
                    row.snoozedUntil
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .formatTime(context)
                Text(
                    text = stringResource(R.string.snoozed_until, untilTime),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (row.isActionable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onTaken,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    ) {
                        Text(stringResource(R.string.action_take))
                    }
                    OutlinedButton(
                        onClick = onSnoozed,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    ) {
                        Text(stringResource(R.string.action_snooze))
                    }
                    OutlinedButton(
                        onClick = onSkipped,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    ) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            }
        }
    }
}

/**
 * Status label — text is always present, so the overdue state never relies on
 * color alone (AGENTS.md > Localization and UX).
 */
@Composable
private fun DoseStatusText(row: DoseRow) {
    val text =
        when {
            row.isOverdue -> stringResource(R.string.dose_overdue)
            else -> stringResource(row.status.labelRes())
        }
    val color =
        when {
            row.isOverdue -> MaterialTheme.colorScheme.onErrorContainer
            row.status == DoseStatus.MISSED -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.primary
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@ScreenPreviews
@Composable
private fun TodayContentPreview() {
    val overdueRow =
        DoseRow(
            id = 1,
            medicationId = 1,
            name = "باراسيتامول",
            strength = "500 ملغ",
            doseAmount = "قرص واحد",
            foodRelation = FoodRelation.AFTER,
            status = DoseStatus.PENDING,
            scheduledAt = Instant.parse("2026-10-17T08:00:00Z"),
            isOverdue = true,
            isActionable = true,
            snoozedUntil = null,
        )
    val takenRow =
        DoseRow(
            id = 2,
            medicationId = 2,
            name = "ميتفورمين",
            strength = "850 ملغ",
            doseAmount = "قرص واحد",
            foodRelation = null,
            status = DoseStatus.TAKEN,
            scheduledAt = Instant.parse("2026-10-17T08:00:00Z"),
            isOverdue = false,
            isActionable = false,
            snoozedUntil = null,
        )
    val snoozedRow =
        DoseRow(
            id = 3,
            medicationId = 1,
            name = "باراسيتامول",
            strength = "500 ملغ",
            doseAmount = "قرص واحد",
            foodRelation = null,
            status = DoseStatus.SNOOZED,
            scheduledAt = Instant.parse("2026-10-17T14:00:00Z"),
            isOverdue = false,
            isActionable = true,
            snoozedUntil = Instant.parse("2026-10-17T14:10:00Z"),
        )
    MedCompanionTheme(dynamicColor = false) {
        TodayContent(
            state =
                TodayUiState(
                    isLoading = false,
                    hasMedications = true,
                    groups =
                        listOf(
                            DoseGroup(time = LocalTime.of(8, 0), doses = listOf(takenRow, overdueRow)),
                            DoseGroup(time = LocalTime.of(14, 0), doses = listOf(snoozedRow)),
                        ),
                ),
            onTaken = {},
            onSnoozed = {},
            onSkipped = {},
            onAddMedicationClick = {},
            onReminderSetupClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun TodayEmptyPreview() {
    MedCompanionTheme(dynamicColor = false) {
        TodayContent(
            state = TodayUiState(isLoading = false, hasMedications = false),
            onTaken = {},
            onSnoozed = {},
            onSkipped = {},
            onAddMedicationClick = {},
            onReminderSetupClick = {},
        )
    }
}

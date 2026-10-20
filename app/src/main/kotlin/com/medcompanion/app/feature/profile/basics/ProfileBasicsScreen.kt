package com.medcompanion.app.feature.profile.basics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.Sex
import com.medcompanion.app.ui.components.ChoiceSelector
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * "Basic information" (T12): the birth year, the sex, whether the patient is
 * pregnant or breastfeeding, and the weight. Everything stays on the device and
 * the assistant only ever sees the minimized copy of it.
 */
@Composable
fun ProfileBasicsScreen(
    viewModel: ProfileBasicsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val message = state.message
    val messageText = if (message != null) stringResource(message.messageRes()) else null
    LaunchedEffect(messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.onMessageShown()
    }

    ProfileBasicsContent(
        state = state,
        actions =
            ProfileBasicsActions(
                onBirthYearChange = viewModel::onBirthYearChange,
                onSexSelected = viewModel::onSexSelected,
                onPregnancySelected = viewModel::onPregnancySelected,
                onWeightChange = viewModel::onWeightChange,
                onSave = viewModel::onSave,
            ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Every change the screen can send back, so the UI stays stateless. */
internal data class ProfileBasicsActions(
    val onBirthYearChange: (String) -> Unit,
    val onSexSelected: (Sex?) -> Unit,
    val onPregnancySelected: (Boolean?) -> Unit,
    val onWeightChange: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
internal fun ProfileBasicsContent(
    state: ProfileBasicsUiState,
    actions: ProfileBasicsActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BodyText(stringResource(R.string.profile_basics_desc))
                    }
                }
            }
            item(key = "birth-year") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionHeading(stringResource(R.string.profile_basics_birth_year))
                        OutlinedTextField(
                            value = state.birthYear,
                            onValueChange = actions.onBirthYearChange,
                            label = { Text(stringResource(R.string.profile_basics_birth_year)) },
                            placeholder = { Text(stringResource(R.string.profile_basics_birth_year_hint)) },
                            isError = !state.isBirthYearValid,
                            supportingText = {
                                Text(
                                    text =
                                        if (!state.isBirthYearValid) {
                                            stringResource(R.string.profile_basics_birth_year_error)
                                        } else {
                                            ageText(state.ageYears)
                                        },
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item(key = "sex") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SectionHeading(stringResource(R.string.profile_basics_sex))
                        ChoiceSelector(
                            choices = sexChoices,
                            selected = state.sex,
                            onSelect = actions.onSexSelected,
                        )
                    }
                }
            }
            item(key = "pregnancy") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SectionHeading(stringResource(R.string.profile_basics_pregnancy))
                        BodyText(stringResource(R.string.profile_basics_pregnancy_desc))
                        ChoiceSelector(
                            choices = pregnancyChoices,
                            selected = state.pregnantOrBreastfeeding,
                            onSelect = actions.onPregnancySelected,
                        )
                    }
                }
            }
            item(key = "weight") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionHeading(stringResource(R.string.profile_basics_weight))
                        OutlinedTextField(
                            value = state.weightKg,
                            onValueChange = actions.onWeightChange,
                            label = { Text(stringResource(R.string.profile_basics_weight)) },
                            isError = !state.isWeightValid,
                            supportingText = {
                                if (!state.isWeightValid) {
                                    Text(
                                        stringResource(
                                            R.string.profile_basics_weight_error,
                                            PatientBasics.WEIGHT_KG_MIN,
                                            PatientBasics.WEIGHT_KG_MAX,
                                        ),
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item(key = "save") {
                Button(
                    onClick = actions.onSave,
                    enabled = state.isDirty && state.canSave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

/** The age line, or nothing when the birth year is unknown. */
@Composable
private fun ageText(ageYears: Int?): String =
    if (ageYears == null) "" else stringResource(R.string.profile_basics_age_value, ageYears)

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@ScreenPreviews
@Composable
private fun ProfileBasicsPreview() {
    MedCompanionTheme(dynamicColor = false) {
        ProfileBasicsContent(
            state =
                ProfileBasicsUiState(
                    isLoading = false,
                    birthYear = "1960",
                    sex = Sex.FEMALE,
                    weightKg = "70",
                    isDirty = true,
                    currentYear = 2026,
                ),
            actions =
                ProfileBasicsActions(
                    onBirthYearChange = {},
                    onSexSelected = {},
                    onPregnancySelected = {},
                    onWeightChange = {},
                    onSave = {},
                ),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

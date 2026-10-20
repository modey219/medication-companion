package com.medcompanion.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One option of a [ChoiceSelector]; a null [value] means "not set". */
internal data class Choice<T>(
    val value: T?,
    @StringRes val labelRes: Int,
)

/**
 * A vertical list of labelled radio rows, one of which may be "not set"
 * (AGENTS.md > Localization and UX): a full-size touch target for every row,
 * never color alone, and the chosen row is announced by the radio itself.
 */
@Composable
internal fun <T> ChoiceSelector(
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        choices.forEach { choice ->
            val isSelected = choice.value == selected
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(choice.value) },
                        ).heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(
                    text = stringResource(choice.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

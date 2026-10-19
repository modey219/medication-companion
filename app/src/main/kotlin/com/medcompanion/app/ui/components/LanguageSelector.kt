package com.medcompanion.app.ui.components

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
import com.medcompanion.app.domain.settings.AppLanguage

/**
 * The language choice, shared by the first-run screen and the Profile tab.
 * Each option is a labelled radio row with a full-size touch target, never
 * color alone (AGENTS.md > Localization and UX).
 */
@Composable
internal fun LanguageSelector(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    labelRes: (AppLanguage) -> Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AppLanguage.entries.forEach { language ->
            val isSelected = language == selected
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(language) },
                        ).heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(
                    text = stringResource(labelRes(language)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

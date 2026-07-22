package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamfoerster.mdhabits.core.i18n.Lang

/**
 * Row of language pills (one per supported [Lang], labeled with its endonym). Used in onboarding
 * and Settings; the choice applies immediately via
 * [com.adamfoerster.mdhabits.core.i18n.LocaleController].
 */
@Composable
fun LanguageSelector(
    selected: Lang,
    onSelect: (Lang) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.entries.forEach { lang ->
            SelectChip(
                label = lang.displayName,
                selected = lang == selected,
                modifier = Modifier.weight(1f),
            ) { onSelect(lang) }
        }
    }
}

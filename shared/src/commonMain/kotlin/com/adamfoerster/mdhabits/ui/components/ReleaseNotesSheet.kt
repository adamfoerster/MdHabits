package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamfoerster.mdhabits.core.i18n.Lang
import com.adamfoerster.mdhabits.core.releasenotes.LocalizedText
import com.adamfoerster.mdhabits.core.releasenotes.ReleaseNote
import com.adamfoerster.mdhabits.core.releasenotes.ReleaseNotes
import com.adamfoerster.mdhabits.ui.theme.Paper

/** The "What's new" sheet: shown once per update and again from Settings → About. */
@Composable
fun ReleaseNotesSheet(
    version: String,
    note: ReleaseNote?,
    lang: Lang,
    onDismiss: () -> Unit,
) {
    PaperSheet(
        title = ReleaseNotes.title.get(lang),
        subtitle = "${ReleaseNotes.subtitle.get(lang)} $version",
        onDismiss = onDismiss,
    ) {
        if (note == null || (note.features.isEmpty() && note.fixes.isEmpty())) {
            Text(ReleaseNotes.empty.get(lang), style = sansStyle(14.sp, Paper.muted))
        } else {
            Section(ReleaseNotes.featuresLabel, note.features, lang)
            Section(ReleaseNotes.fixesLabel, note.fixes, lang)
        }
        PrimaryButton(
            text = ReleaseNotes.gotIt.get(lang),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun Section(label: LocalizedText, items: List<LocalizedText>, lang: Lang) {
    if (items.isEmpty()) return
    SectionLabel(label.get(lang), Modifier.padding(top = 4.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = sansStyle(14.sp, Paper.accent, FontWeight.Bold))
                Text(item.get(lang), style = sansStyle(14.sp, Paper.ink))
            }
        }
    }
}

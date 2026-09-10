package com.adamfoerster.mdhabits.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.ui.components.CalendarIcon
import com.adamfoerster.mdhabits.ui.components.Chevron
import com.adamfoerster.mdhabits.ui.components.DownloadIcon
import com.adamfoerster.mdhabits.ui.components.FolderIcon
import com.adamfoerster.mdhabits.ui.components.GearIcon
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.LanguageSelector
import com.adamfoerster.mdhabits.ui.components.PaperSwitch
import com.adamfoerster.mdhabits.ui.components.PaperToast
import com.adamfoerster.mdhabits.ui.components.PencilIcon
import com.adamfoerster.mdhabits.ui.components.SectionLabel
import com.adamfoerster.mdhabits.ui.components.StarIcon
import com.adamfoerster.mdhabits.ui.components.TaskPickerSheet
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.rememberToastState
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onOpenTheme: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val vaultName by viewModel.vaultName.collectAsStateWithLifecycle()
    val themeName by viewModel.themeName.collectAsStateWithLifecycle()
    val lang by viewModel.lang.collectAsStateWithLifecycle()
    val mdPrayerEnabled by viewModel.mdPrayerEnabled.collectAsStateWithLifecycle()
    val mdPrayerFolderName by viewModel.mdPrayerFolderName.collectAsStateWithLifecycle()
    val mdPrayerTasks by viewModel.mdPrayerTasks.collectAsStateWithLifecycle()
    val mdPrayerLinkedTaskId by viewModel.mdPrayerLinkedTaskId.collectAsStateWithLifecycle()
    val mdPrayerLinkedTaskTitle by viewModel.mdPrayerLinkedTaskTitle.collectAsStateWithLifecycle()
    var showTaskPicker by remember { mutableStateOf(false) }
    val toast = rememberToastState()

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
                Kicker(strings.tabSettings)
                Text(strings.cfgTitle, Modifier.padding(top = 2.dp), style = handStyle(34.sp))
            }

            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Data
                Column {
                    SectionLabel(strings.cfgData, Modifier.padding(bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().paperCard(radius = 14)) {
                        SettingRow(
                            icon = { FolderIcon() },
                            title = strings.cfgDataFolder,
                            subtitle = vaultName ?: strings.onbChooseFolder,
                            trailing = {
                                Text(strings.cfgChange, style = sansStyle(13.sp, Paper.accent, FontWeight.SemiBold))
                            },
                            divider = true,
                            onClick = { viewModel.pickFolder() },
                        )
                        SettingRow(
                            icon = { DownloadIcon() },
                            title = strings.cfgExport,
                            subtitle = strings.cfgExportSub,
                        )
                    }
                }
                // mdPrayer
                Column {
                    SectionLabel(strings.cfgMdPrayer, Modifier.padding(bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().paperCard(radius = 14)) {
                        SettingRow(
                            icon = { StarIcon() },
                            title = strings.cfgMdPrayerEnable,
                            subtitle = strings.cfgMdPrayerHelp,
                            trailing = {
                                PaperSwitch(checked = mdPrayerEnabled, onCheckedChange = viewModel::setMdPrayerEnabled)
                            },
                            divider = mdPrayerEnabled,
                        )
                        if (mdPrayerEnabled) {
                            SettingRow(
                                icon = { FolderIcon() },
                                title = strings.cfgMdPrayerPickFolder,
                                subtitle = mdPrayerFolderName ?: strings.cfgMdPrayerNoFolder,
                                trailing = {
                                    Text(strings.cfgChange, style = sansStyle(13.sp, Paper.accent, FontWeight.SemiBold))
                                },
                                divider = true,
                                onClick = {
                                    viewModel.pickMdPrayerFolder { result ->
                                        when (result) {
                                            MdPrayerFolderResult.NotFound -> toast.show(strings.cfgMdPrayerNotFound)
                                            is MdPrayerFolderResult.Linked -> Unit
                                            MdPrayerFolderResult.Cancelled -> Unit
                                        }
                                    }
                                },
                            )
                            SettingRow(
                                icon = { PencilIcon() },
                                title = strings.cfgMdPrayerTask,
                                subtitle = mdPrayerLinkedTaskTitle ?: strings.cfgMdPrayerNoTask,
                                trailing = {
                                    Text(strings.cfgChange, style = sansStyle(13.sp, Paper.accent, FontWeight.SemiBold))
                                },
                                divider = mdPrayerFolderName != null,
                                onClick = { showTaskPicker = true },
                            )
                            if (mdPrayerFolderName != null) {
                                SettingRow(
                                    icon = { GearIcon() },
                                    title = strings.cfgMdPrayerSyncNow,
                                    subtitle = null,
                                    onClick = { viewModel.syncMdPrayerNow { toast.show(strings.cfgMdPrayerSyncSuccess) } },
                                )
                            }
                        }
                    }
                }
                // Week
                Column {
                    SectionLabel(strings.cfgWeek, Modifier.padding(bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().paperCard(radius = 14)) {
                        SettingRow(
                            icon = { CalendarIcon() },
                            title = strings.cfgWeekStart,
                            subtitle = null,
                            trailing = {
                                Text("${strings.cfgMonday} ›", style = sansStyle(13.sp, Paper.muted, FontWeight.SemiBold))
                            },
                            divider = true,
                        )
                        SettingRow(
                            icon = { PencilIcon() },
                            title = strings.cfgReminder,
                            subtitle = strings.cfgReminderSub,
                            trailing = {
                                Text(strings.cfgActive, style = sansStyle(13.sp, Paper.muted, FontWeight.SemiBold))
                            },
                        )
                    }
                }
                // Language
                Column {
                    SectionLabel(strings.cfgLanguage, Modifier.padding(bottom = 8.dp))
                    Box(Modifier.fillMaxWidth().paperCard(radius = 14).padding(16.dp)) {
                        LanguageSelector(selected = lang, onSelect = viewModel::setLanguage)
                    }
                }
                // Year
                Column {
                    SectionLabel(strings.cfgYear, Modifier.padding(bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().paperCard(radius = 14)) {
                        SettingRow(
                            icon = { StarIcon() },
                            title = strings.cfgAnnualTheme,
                            subtitle = themeName.ifEmpty { strings.noTheme },
                            trailing = { Chevron(left = false, color = Paper.faded, size = 18.dp) },
                            onClick = onOpenTheme,
                        )
                    }
                }
                // About
                Column {
                    SectionLabel(strings.cfgAbout, Modifier.padding(bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().paperCard(radius = 14)) {
                        SettingRow(
                            icon = { GearIcon() },
                            title = strings.cfgVersion,
                            subtitle = null,
                            trailing = {
                                Text(viewModel.version, style = sansStyle(13.sp, Paper.muted, FontWeight.SemiBold))
                            },
                        )
                    }
                }
            }
        }

        PaperToast(toast)
    }

    if (showTaskPicker) {
        TaskPickerSheet(
            title = strings.cfgMdPrayerPickTaskTitle,
            subtitle = strings.cfgMdPrayerPickTaskSub,
            tasks = mdPrayerTasks,
            selectedTaskId = mdPrayerLinkedTaskId,
            onDismiss = { showTaskPicker = false },
            onSelect = { task ->
                viewModel.linkMdPrayerTask(task)
                showTaskPicker = false
            },
        )
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.paperClick(onClick = onClick) else Modifier)
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        drawLine(
                            Paper.inset,
                            Offset(0f, size.height),
                            Offset(size.width, size.height),
                            1.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = sansStyle(14.5.sp, Paper.ink, FontWeight.SemiBold))
            if (subtitle != null) {
                Text(subtitle, style = sansStyle(12.sp, Paper.muted))
            }
        }
        trailing?.invoke()
    }
}

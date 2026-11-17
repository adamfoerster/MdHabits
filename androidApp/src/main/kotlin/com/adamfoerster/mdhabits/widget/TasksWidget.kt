package com.adamfoerster.mdhabits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyListScope
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.adamfoerster.mdhabits.MainActivity
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.usecase.WidgetTask
import com.adamfoerster.mdhabits.platform.AndroidWidgetData
import com.adamfoerster.mdhabits.platform.WidgetContent
import com.adamfoerster.mdhabits.ui.theme.Paper

/**
 * Home-screen widget listing today's per-day tasks and this week's weekly tasks (ad-hoc ones stay
 * in the app). Tapping a row checks it off or undoes it; tapping the title opens the app.
 */
class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = AndroidWidgetData.load(context)
        provideContent { WidgetBody(content) }
    }

    companion object {
        /** Redraws every placed widget, e.g. after the app changed tasks. */
        suspend fun refresh(context: Context) = TasksWidget().updateAll(context)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

/** Flips one task's completion, then redraws the widgets. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        AndroidWidgetData.toggle(context, taskId)
        TasksWidget.refresh(context)
    }
}

private val TaskIdKey = ActionParameters.Key<String>("taskId")

@Composable
private fun WidgetBody(content: WidgetContent) {
    val strings = content.strings
    val snapshot = content.snapshot
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Paper.bg)
            .cornerRadius(16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = strings.appName,
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            style = TextStyle(color = ColorProvider(Paper.inkDeep), fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(4.dp))
        if (snapshot.isEmpty) {
            Text(
                text = strings.cad.emptyTitle,
                style = TextStyle(color = ColorProvider(Paper.muted), fontSize = 13.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                section(strings.todaySection, snapshot.today)
                section(strings.weeklySection, snapshot.week)
            }
        }
    }
}

private fun LazyListScope.section(title: String, rows: List<WidgetTask>) {
    if (rows.isEmpty()) return
    item {
        Text(
            text = title.uppercase(),
            modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
            style = TextStyle(color = ColorProvider(Paper.faded), fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
    items(rows) { row -> TaskRow(row) }
}

@Composable
private fun TaskRow(row: WidgetTask) {
    // Same signs as Home: a habit's points go both ways until it is checked off.
    val sign = if (row.recurrence == Recurrence.HABIT && !row.completed) "±" else "+"
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to row.taskId))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (row.completed) "✓" else "○",
            style = TextStyle(
                color = ColorProvider(if (row.completed) Paper.greenCircle else Paper.subtle),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = row.title,
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(if (row.completed) Paper.doneText else Paper.ink),
                fontSize = 14.sp,
                textDecoration = if (row.completed) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
        Text(
            text = "$sign${row.points}",
            style = TextStyle(
                color = ColorProvider(if (row.completed) Paper.donePoints else Paper.pointsTint),
                fontSize = 12.sp,
            ),
        )
    }
}

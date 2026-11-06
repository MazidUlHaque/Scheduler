package com.mazidulhaque.kokhon.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.glance.text.TextStyle
import com.mazidulhaque.kokhon.KokhonApplication
import com.mazidulhaque.kokhon.MainActivity
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.accentColor
import com.mazidulhaque.kokhon.core.model.Entry
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** "Next up" on the home screen: the next few plans, each opening its page when tapped. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val app = context.applicationContext as KokhonApplication
        val now = app.clock.instant()
        val plans =
            app.entryRepository
                .startingFrom(now.minus(Duration.ofHours(2)))
                .filter { !it.isFinishedAt(now) }
                .sortedBy { it.start }
                .take(MAX_ROWS)
        val rows = plans.map { Row(it, label(context, it)) }
        provideContent { Content(context, rows) }
    }

    private data class Row(
        val entry: Entry,
        val whenText: String,
    )

    private fun label(
        context: Context,
        entry: Entry,
    ): String {
        val zone = ZoneId.systemDefault()
        val day = entry.start.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val time = TimeFormat.time(context, entry.start)
        return when (day) {
            today -> time
            today.plusDays(1) -> context.getString(R.string.widget_tomorrow, time)
            else -> TimeFormat.shortDate(entry.start) + ", " + time
        }
    }

    @Composable
    private fun Content(
        context: Context,
        rows: List<Row>,
    ) {
        val ink = ColorProvider(day = Color(0xFF16181D), night = Color(0xFFF2F3F5))
        val muted = ColorProvider(day = Color(0xFF4A4F57), night = Color(0xFFB9BEC5))
        val paper = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1A1C20))
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(paper)
                    .cornerRadius(20.dp)
                    .padding(14.dp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widget_next_up),
                    style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(context.getString(R.string.app_name), style = TextStyle(color = muted, fontSize = 12.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (rows.isEmpty()) {
                Text(
                    context.getString(R.string.widget_empty),
                    style = TextStyle(color = muted, fontSize = 14.sp),
                    modifier = GlanceModifier.clickable(openApp),
                )
            }
            rows.forEach { row ->
                val open =
                    actionStartActivity(
                        Intent(context, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_OPEN_ENTRY)
                            .putExtra(MainActivity.EXTRA_ENTRY_ID, row.entry.id),
                    )
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(open),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            GlanceModifier
                                .width(4.dp)
                                .height(28.dp)
                                .cornerRadius(2.dp)
                                .background(ColorProvider(day = row.entry.type.accentColor(), night = row.entry.type.accentColor())),
                    ) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Column {
                        Text(row.entry.title, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(row.whenText, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 4
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

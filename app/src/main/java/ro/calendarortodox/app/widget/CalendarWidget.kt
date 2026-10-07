package ro.calendarortodox.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import ro.calendarortodox.app.*
import ro.calendarortodox.app.R
import ro.calendarortodox.app.data.CalendarDay
import ro.calendarortodox.app.ui.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first

class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val day = context.calendarApp.repository.day(today)
        val localized = context.localizedContext()
        val language = context.calendarLanguage()
        val theme = context.calendarApp.preferences.settings.first().theme
        val dark = theme == "dark" || (theme == "system" && context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val background = if (dark) Color(0xFF151D19) else Color(0xFFF8F6F0)
        val primary = if (dark) Color(0xFFA5D2BB) else Color(0xFF285648)
        val foreground = if (dark) Color(0xFFE5E8DE) else Color(0xFF25332D)
        val red = if (dark) Color(0xFFFFADB0) else Color(0xFFA1333A)
        provideContent {
            val size = LocalSize.current
            Column(GlanceModifier.fillMaxSize().background(background)
                .padding(16.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java)))) {
                Text(dateLabel(today, language, "EEE, d MMM yyyy"), style = TextStyle(color = ColorProvider(primary), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(10.dp))
                if (day == null) Text(localized.getString(R.string.coverage_missing), style = TextStyle(color = ColorProvider(foreground), fontSize = 14.sp))
                else {
                    val count = ((size.height.value - 110) / 44).toInt().coerceIn(1, 4)
                    day.observances.take(count).forEach { observance ->
                        Text((if (observance.red) "✝ " else "") + observance.name.inLanguage(language), maxLines = if (size.height < 160.dp) 1 else 2,
                            style = TextStyle(color = ColorProvider(if (observance.red) red else foreground), fontSize = 16.sp, fontWeight = FontWeight.Medium))
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    Text(localized.getString(day.fasting.labelId()), maxLines = if (size.height < 160.dp) 1 else 2, style = TextStyle(color = ColorProvider(primary), fontSize = 12.sp))
                }
            }
        }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

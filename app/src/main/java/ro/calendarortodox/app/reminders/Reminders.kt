package ro.calendarortodox.app.reminders

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.updateAll
import androidx.work.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.calendarortodox.app.*
import ro.calendarortodox.app.R
import ro.calendarortodox.app.data.*
import ro.calendarortodox.app.ui.*
import ro.calendarortodox.app.widget.CalendarWidget
import java.time.*

object ReminderScheduler {
    private const val REQUEST = 100
    const val CHANNEL = "tomorrow"
    private val scheduleLock = Mutex()

    suspend fun reschedule(context: Context, force: Boolean = false) = scheduleLock.withLock {
        val settings = context.calendarApp.preferences.settings.first()
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java)
        val existing = PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        val state = context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE)
        val now = ZonedDateTime.now()
        val signature = "${settings.feastReminders}:${settings.fastReminders}:${settings.time}:${now.zone}"
        val scheduled = state.getString("date", null)
        // Preserve a due alarm that Android has deferred. Opening the app must not
        // silently replace tonight's reminder with tomorrow's alarm.
        if (!force && existing != null && state.getString("signature", null) == signature &&
            scheduled != null && scheduled >= now.toLocalDate().toString()) return@withLock
        existing?.let { manager.cancel(it); it.cancel() }
        if (!settings.feastReminders && !settings.fastReminders) { state.edit().clear().apply(); return@withLock }
        val next = ReminderPolicy.nextTime(now, settings.time)
        intent.putExtra("scheduledDate", next.toLocalDate().toString())
        val pending = PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
        state.edit().putString("signature", signature).putString("date", next.toLocalDate().toString()).apply()
    }

    internal suspend fun deliver(context: Context, scheduledDate: String?, now: LocalDate = LocalDate.now()) {
        val target = ReminderPolicy.targetIfFresh(scheduledDate, now) ?: return
        val settings = context.calendarApp.preferences.settings.first()
        val day = context.calendarApp.repository.day(target) ?: return
        if (!day.shouldRemind(settings.feastReminders, settings.fastReminders)) return
        val localized = context.localizedContext()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        if (!context.calendarApp.preferences.claimDelivery(target.toString())) return
        val language = context.calendarLanguage()
        val lines = mutableListOf<String>()
        if (settings.feastReminders && day.red) {
            if (day.sunday) lines += localized.getString(R.string.sunday)
            lines += day.observances.filter { it.red }.map { it.name.inLanguage(language) }
        }
        if (settings.fastReminders && day.isFast && (day.period == null || day.periodStart)) {
            lines += localized.getString(day.fasting.labelId())
            if (day.periodStart) lines += localized.getString(R.string.period_start) + " · " + localized.getString(periodLabel(day.period!!))
        }
        val open = PendingIntent.getActivity(context, 101,
            Intent(context, MainActivity::class.java).putExtra("date", target.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val content = lines.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.notification_title, dateLabel(target, language, "d MMMM")))
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        try { manager.notify(1001, notification) } catch (_: SecurityException) { /* Permission may change during delivery. */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.calendarApp.scope.launch {
            try {
                ReminderScheduler.deliver(context, intent.getStringExtra("scheduledDate"))
            } finally {
                try { ReminderScheduler.reschedule(context, force = true) } finally { pending.finish() }
            }
        }
    }
}

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_LOCALE_CHANGED)) return
        val pending = goAsync()
        context.calendarApp.scope.launch {
            try { ReminderScheduler.reschedule(context, force = true); CalendarWidget().updateAll(context) }
            finally { pending.finish() }
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ReminderScheduler.reschedule(applicationContext)
        CalendarWidget().updateAll(applicationContext)
        return Result.success()
    }
}

package ro.calendarortodox.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ro.calendarortodox.app.data.Preferences
import java.time.LocalDate
import ro.calendarortodox.app.ui.dateLabel
import ro.calendarortodox.app.ui.labelId
import androidx.glance.appwidget.compose
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ro.calendarortodox.app.widget.CalendarWidget
import ro.calendarortodox.app.reminders.ReminderScheduler
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class AppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun english() {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun calendarSearchWorksOfflineAndOpensADate() {
        val next = compose.activity.calendarApp.repository.search("Christmas", LocalDate.now()).first().nextDate
        Assume.assumeNotNull(next)
        compose.onNodeWithText("Calendar", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Craciun")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nativity of the Lord").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nativity of the Lord").performClick()
        compose.onNodeWithText(dateLabel(next!!, "en", "d MMMM yyyy")).assertExists()
        compose.onNodeWithText("Fast-free day").assertExists()
    }

    @Test fun monthYearAndWeekControlsWork() {
        compose.onNodeWithText("Calendar", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("Month").performClick()
        val current = compose.activity.calendarApp.repository.nearest(LocalDate.now())
        compose.onNodeWithText(dateLabel(current, "en", "MMMM yyyy")).performClick()
        compose.onNodeWithText("2027").performClick()
        compose.onNodeWithText("January").performClick()
        compose.onNodeWithText("January 2027").assertExists()
    }

    @Test fun languageSelectionTranslatesAndPersistsAcrossRecreation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Română").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Astăzi").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Astăzi").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Calendar", useUnmergedTree = true).assertExists()
        assertEquals("ro", compose.activity.calendarLanguage())
    }

    @Test fun localSettingsSurviveRecreation() {
        val app = compose.activity.calendarApp
        runBlocking { app.preferences.setTheme("dark"); app.preferences.setTime(19, 35); app.preferences.setReminders(true, false) }
        compose.activityRule.scenario.recreate()
        val settings = runBlocking { app.preferences.settings.first() }
        assertEquals("dark", settings.theme)
        assertEquals(19, settings.hour)
        assertEquals(35, settings.minute)
        assertTrue(settings.feastReminders)
        assertFalse(settings.fastReminders)
        runBlocking { app.preferences.setTheme("system"); app.preferences.setTime(18, 0); app.preferences.setReminders(false, false) }
    }

    @Test fun manifestDoesNotRequestNetworkOrExactAlarmAccess() {
        val context = compose.activity
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.INTERNET" in permissions)
        assertFalse("android.permission.SCHEDULE_EXACT_ALARM" in permissions)
        assertFalse("android.permission.USE_EXACT_ALARM" in permissions)
    }

    @Test fun notificationDeliveryClaimsAreIdempotent() {
        val preferences = compose.activity.calendarApp.preferences
        val unique = "test-${System.nanoTime()}"
        runBlocking {
            assertTrue(preferences.claimDelivery(unique))
            assertFalse(preferences.claimDelivery(unique))
        }
    }

    @Test fun localNotificationCombinesCategoriesAndOpensTomorrow() {
        val context = compose.activity
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).bufferedReader().use { it.readText() }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        runBlocking {
            context.calendarApp.preferences.claimDelivery("reset-${System.nanoTime()}")
            context.calendarApp.preferences.setReminders(true, true)
            ReminderScheduler.deliver(context, "2026-09-13", LocalDate.of(2026, 9, 13))
        }
        compose.waitUntil(5_000) { manager.activeNotifications.isNotEmpty() }
        val notification = manager.activeNotifications.single().notification
        assertTrue(notification.extras.getString("android.bigText")!!.contains("Exaltation"))
        assertTrue(notification.extras.getString("android.bigText")!!.contains("Fasting"))
        assertNotNull(notification.contentIntent)
        val posted = manager.activeNotifications.single().postTime
        runBlocking { ReminderScheduler.deliver(context, "2026-09-13", LocalDate.of(2026, 9, 13)) }
        assertEquals(posted, manager.activeNotifications.single().postTime)
        notification.contentIntent.send()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("14 September 2026").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { context.calendarApp.preferences.setReminders(false, false); ReminderScheduler.reschedule(context, force = true) }
        manager.cancelAll()
    }

    @Test fun widgetRendersCurrentDayAtCompactAndLargeSizes() {
        val context = compose.activity
        val day = context.calendarApp.repository.day(LocalDate.now())
        Assume.assumeNotNull(day)
        for ((width, height) in listOf(180 to 110, 300 to 250)) {
            val remoteViews = runBlocking { CalendarWidget().compose(context, size = DpSize(width.dp, height.dp)) }
            compose.runOnUiThread {
                // Launchers inflate platform RemoteViews, without an Activity's
                // AppCompat LayoutInflater factory replacing framework widgets.
                val hostContext = context.applicationContext
                val parent = android.widget.FrameLayout(hostContext)
                val view = remoteViews.apply(hostContext, parent)
                val density = context.resources.displayMetrics.density
                val w = (width * density).toInt()
                val h = (height * density).toInt()
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                fun texts(v: android.view.View): List<String> = when (v) {
                    is android.widget.TextView -> listOf(v.text.toString())
                    is android.view.ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
                    else -> emptyList()
                }
                assertTrue(texts(view).any { it.contains(day!!.observances.first().name.en) })
                assertTrue(texts(view).contains(context.getString(day!!.fasting.labelId())))
                val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                java.io.File(context.getExternalFilesDir(null), "widget-$width.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}

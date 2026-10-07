package ro.calendarortodox.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.*
import kotlinx.coroutines.*
import ro.calendarortodox.app.data.CalendarRepository
import ro.calendarortodox.app.data.Preferences
import ro.calendarortodox.app.reminders.RefreshWorker
import java.util.concurrent.TimeUnit

class CalendarApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository by lazy { CalendarRepository.parse(assets.open("calendar.json").bufferedReader().use { it.readText() }) }
    val preferences by lazy { Preferences(this) }
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("calendar-refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS).build())
    }
}

val Context.calendarApp get() = applicationContext as CalendarApplication

fun Context.calendarLanguage(): String {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        val locales = getSystemService(android.app.LocaleManager::class.java).applicationLocales
        if (!locales.isEmpty) return if (locales[0].language == "ro") "ro" else "en"
    } else {
        getSharedPreferences("locale", Context.MODE_PRIVATE).getString("selected", null)?.let { return it }
    }
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val language = if (!appLocales.isEmpty) appLocales[0]?.language else resources.configuration.locales[0].language
    return if (language == "ro") "ro" else "en"
}

fun Context.localizedContext(): Context {
    val config = android.content.res.Configuration(resources.configuration)
    config.setLocale(java.util.Locale.forLanguageTag(calendarLanguage()))
    return createConfigurationContext(config)
}

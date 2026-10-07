package ro.calendarortodox.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.preferences by preferencesDataStore("settings")

data class Settings(
    val theme: String = "system",
    val feastReminders: Boolean = false,
    val fastReminders: Boolean = false,
    val hour: Int = 18,
    val minute: Int = 0,
    val promptSeen: Boolean = false,
) { val time: LocalTime get() = LocalTime.of(hour, minute) }

class Preferences(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val feast = booleanPreferencesKey("feast_reminders")
    private val fast = booleanPreferencesKey("fast_reminders")
    private val hour = intPreferencesKey("hour")
    private val minute = intPreferencesKey("minute")
    private val prompt = booleanPreferencesKey("prompt_seen")
    private val delivered = stringPreferencesKey("last_delivered_target")
    val settings = context.preferences.data.map { p -> Settings(p[theme] ?: "system", p[feast] ?: false, p[fast] ?: false, p[hour] ?: 18, p[minute] ?: 0, p[prompt] ?: false) }
    suspend fun setTheme(value: String) { context.preferences.edit { it[theme] = value } }
    suspend fun setReminders(feasts: Boolean, fasts: Boolean) { context.preferences.edit { it[feast] = feasts; it[fast] = fasts; it[prompt] = true } }
    suspend fun setTime(h: Int, m: Int) { context.preferences.edit { it[hour] = h; it[minute] = m } }
    suspend fun dismissPrompt() { context.preferences.edit { it[prompt] = true } }
    suspend fun claimDelivery(target: String): Boolean {
        var claimed = false
        context.preferences.edit { if (it[delivered] != target) { it[delivered] = target; claimed = true } }
        return claimed
    }
}

package ro.calendarortodox.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import ro.calendarortodox.app.data.*
import ro.calendarortodox.app.reminders.ReminderScheduler
import ro.calendarortodox.app.ui.*
import ro.calendarortodox.app.widget.CalendarWidget
import androidx.glance.appwidget.updateAll
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var openDate by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openDate = intent.getStringExtra("date")
        setContent {
            val settings by calendarApp.preferences.settings.collectAsStateWithLifecycle(Settings())
            CalendarTheme(settings.theme) {
                CalendarApp(settings, openDate) { openDate = null }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDate = intent.getStringExtra("date")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarApp(settings: Settings, openDate: String?, consumed: () -> Unit) {
    val context = LocalContext.current
    val app = context.calendarApp
    val repository by produceState<CalendarRepository?>(null) { value = withContext(Dispatchers.IO) { app.repository } }
    val repo = repository
    if (repo == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val scope = rememberCoroutineScope()
    val language = context.calendarLanguage()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var selected by rememberSaveable { mutableStateOf(repo.nearest(today).toString()) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    fun updateReminders(feast: Boolean, fast: Boolean) {
        scope.launch {
            app.preferences.setReminders(feast, fast)
            ReminderScheduler.reschedule(context, force = true)
        }
        if ((feast || fast) && Build.VERSION.SDK_INT >= 33 && !notificationAllowed) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        today = LocalDate.now()
        notificationAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        scope.launch { ReminderScheduler.reschedule(context); CalendarWidget().updateAll(context) }
    }
    LaunchedEffect(Unit) {
        while (true) { today = LocalDate.now(); delay(30_000) }
    }
    LaunchedEffect(openDate) {
        openDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull()?.let { detail = it.toString(); selected = repo.nearest(it).toString() }; consumed() }
    }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = { IconButton(onClick = { tab = 2 }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) } }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Outlined.WbSunny, null) }, label = { Text(stringResource(R.string.today)) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Outlined.CalendarMonth, null) }, label = { Text(stringResource(R.string.calendar)) })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().widthIn(max = 760.dp), contentAlignment = Alignment.TopCenter) {
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item { Text(stringResource(R.string.daily_calendar), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary) }
                    item { DateHeading(today, language) }
                    item { DayDetails(repo.day(today), language) }
                    item {
                        val tomorrow = today.plusDays(1)
                        ElevatedCard(onClick = { detail = tomorrow.toString() }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.tomorrow).uppercase(Locale.forLanguageTag(language)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(dateLabel(tomorrow, language, "EEEE, d MMMM"), style = MaterialTheme.typography.titleMedium)
                                repo.day(tomorrow)?.let { day ->
                                    Text(day.observances.joinToString(" · ") { it.name.inLanguage(language) }, maxLines = 3)
                                    Text(stringResource(day.fasting.labelId()), style = MaterialTheme.typography.bodySmall)
                                } ?: Text(stringResource(R.string.coverage_missing))
                            }
                        }
                    }
                    if (!settings.promptSeen) item {
                        OutlinedCard { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.reminder_intro), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.reminder_explanation), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { updateReminders(true, true) }) { Text(stringResource(R.string.enable_reminders)) }
                            TextButton(onClick = { scope.launch { app.preferences.dismissPrompt() } }) { Text(stringResource(R.string.later)) }
                        } }
                    }
                    if (repo.bundle.reviewStatus != "reviewed") item { Text(stringResource(R.string.review_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                1 -> CalendarScreen(repo, LocalDate.parse(selected), today, language, { selected = it.toString() }, { detail = it.toString() })
                else -> SettingsScreen(settings, repo, language, notificationAllowed, ::updateReminders)
            }
        }
    }
    detail?.let { raw ->
        val date = LocalDate.parse(raw)
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                DateHeading(date, language)
                DayDetails(repo.day(date), language)
                TextButton(onClick = { detail = null }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
fun DateHeading(date: LocalDate, language: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(dateLabel(date, language, "EEEE" ).replaceFirstChar { it.titlecase(Locale.forLanguageTag(language)) }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(dateLabel(date, language, "d MMMM yyyy"), style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif)
    }
}

@Composable
fun DayDetails(day: CalendarDay?, language: String) {
    if (day == null) { Text(stringResource(R.string.coverage_missing)); return }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (day.sunday) Text("✝ " + stringResource(R.string.sunday), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.commemorations), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        day.observances.forEach { saint ->
            Text((if (saint.red) "✝  " else "") + saint.name.inLanguage(language), color = if (saint.red) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (day.isFast) Icons.Outlined.Spa else Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(day.fasting.labelId()), fontWeight = FontWeight.Medium)
                    day.period?.let { Text(stringResource(periodLabel(it)), style = MaterialTheme.typography.bodySmall) }
                    if (day.periodStart) Text(stringResource(R.string.period_start), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(repo: CalendarRepository, selected: LocalDate, today: LocalDate, language: String, select: (LocalDate) -> Unit, open: (LocalDate) -> Unit) {
    var weekly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf(false) }
    val results = remember(query, today) { repo.search(query, today) }
    val month = YearMonth.from(selected)
    val start = if (weekly) selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val count = if (weekly) 7 else ((month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7) * 7
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_search)) } }, shape = RoundedCornerShape(16.dp)) }
        if (query.isNotBlank()) {
            item { Text(stringResource(R.string.search_help), style = MaterialTheme.typography.bodySmall) }
            if (results.isEmpty()) item { Text(stringResource(R.string.no_matches)) }
            items(results, key = { it.observance.id }) { result ->
                OutlinedCard(onClick = { result.nextDate?.let { select(it); open(it) } }, enabled = result.nextDate != null) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(result.observance.name.inLanguage(language), color = if (result.observance.red) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(result.nextDate?.let { dateLabel(it, language) } ?: stringResource(R.string.no_upcoming), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!weekly, { weekly = false }, label = { Text(stringResource(R.string.monthly)) })
                        FilterChip(weekly, { weekly = true }, label = { Text(stringResource(R.string.weekly)) })
                    }
                    TextButton(onClick = { select(today) }, enabled = repo.day(today) != null) { Text(stringResource(R.string.today)) }
                }
            }
            item {
                val previous = if (weekly) selected.minusWeeks(1) else selected.minusMonths(1)
                val next = if (weekly) selected.plusWeeks(1) else selected.plusMonths(1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { select(repo.nearest(previous)) }, enabled = start > repo.firstDate) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.previous)) }
                    TextButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) { Text(dateLabel(selected, language, "MMMM yyyy"), style = MaterialTheme.typography.titleMedium) }
                    IconButton(onClick = { select(repo.nearest(next)) }, enabled = start.plusDays(count.toLong() - 1) < repo.lastDate) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.next)) }
                }
            }
            item {
                Row {
                    DayOfWeek.entries.forEach { day -> Text(day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag(language)), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.height(10.dp))
                (0 until count / 7).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { col ->
                            val date = start.plusDays((row * 7 + col).toLong())
                            val data = repo.day(date)
                            val label = dateLabel(date, language) + (if (data?.red == true) ", " + stringResource(R.string.feast) else "") + (if (data?.isFast == true) ", " + stringResource(data.fasting.labelId()) else "")
                            Surface(onClick = { select(date) }, enabled = data != null, modifier = Modifier.weight(1f).padding(1.dp).heightIn(min = 58.dp).semantics { contentDescription = label; this.selected = selected == date },
                                shape = RoundedCornerShape(14.dp), color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (date == today) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(date.dayOfMonth.toString(), color = when { data == null || (!weekly && date.month != month.month) -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f); data.red -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface }, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                                    Text((if (data?.red == true) "✝" else "") + (if (data?.isFast == true) " •" else ""), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.calendar_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { HorizontalDivider() }
            item { DateHeading(selected, language) }
            item { DayDetails(repo.day(selected), language) }
        }
    }
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text(stringResource(R.string.choose_month)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repo.years.forEach { year -> FilterChip(selected.year == year, { select(selected.withYear(year)) }, label = { Text(year.toString()) }) }
            }
            Month.entries.forEach { m -> TextButton(onClick = { select(LocalDate.of(selected.year, m, 1)); picker = false }, modifier = Modifier.fillMaxWidth()) { Text(m.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag(language))) } }
        }
    }, confirmButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.done)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, repo: CalendarRepository, language: String, notificationsAllowed: Boolean, updateReminders: (Boolean, Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = context.calendarApp.preferences
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif) }
        item {
            SettingsGroup(stringResource(R.string.language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("ro" to "Română", "en" to "English").forEach { (code, name) -> FilterChip(language == code, {
                        context.getSharedPreferences("locale", android.content.Context.MODE_PRIVATE).edit().putString("selected", code).apply()
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                        context.calendarApp.scope.launch { CalendarWidget().updateAll(context); ReminderScheduler.reschedule(context) }
                    }, label = { Text(name) }) }
                }
            }
        }
        item { SettingsGroup(stringResource(R.string.appearance)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (value, name) -> FilterChip(settings.theme == value, { scope.launch { preferences.setTheme(value); CalendarWidget().updateAll(context) } }, label = { Text(stringResource(name)) }) }
            }
        } }
        item { SettingsGroup(stringResource(R.string.reminders)) {
            PreferenceSwitch(stringResource(R.string.feast_reminders), stringResource(R.string.feast_reminders_detail), settings.feastReminders) { updateReminders(it, settings.fastReminders) }
            PreferenceSwitch(stringResource(R.string.fast_reminders), stringResource(R.string.fast_reminders_detail), settings.fastReminders) { updateReminders(settings.feastReminders, it) }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> scope.launch { preferences.setTime(h, m); ReminderScheduler.reschedule(context, force = true) } }, settings.hour, settings.minute, true).show()
            }) { Text(stringResource(R.string.reminder_time) + " · " + "%02d:%02d".format(settings.hour, settings.minute)) }
            Text(stringResource(R.string.timing_note), style = MaterialTheme.typography.bodySmall)
            if (!notificationsAllowed && (settings.feastReminders || settings.fastReminders)) {
                Text(stringResource(R.string.notifications_disabled), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(stringResource(R.string.open_system_settings)) }
            }
        } }
        item { SettingsGroup(stringResource(R.string.about)) {
            Text(stringResource(R.string.about_text))
            Text(stringResource(R.string.coverage, repo.years.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.version, repo.bundle.contentVersion), style = MaterialTheme.typography.bodySmall)
            if (repo.bundle.reviewStatus != "reviewed") Text(stringResource(R.string.review_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } }
        item { SettingsGroup(stringResource(R.string.privacy)) { Text(stringResource(R.string.privacy_text)) } }
        item { SettingsGroup(stringResource(R.string.sources)) {
            Text(stringResource(R.string.source_text))
            repo.bundle.sources.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        } }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, onValueChange = change), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(description, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, onCheckedChange = null)
    }
}

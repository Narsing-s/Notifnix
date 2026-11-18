package com.notifnix.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import java.util.Calendar
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val Ink = Color(0xFF172033)
private val Violet = Color(0xFF6657D9)
private val Canvas = Color(0xFFF5F6FB)
private const val CHANNEL_ID = "notifnix_simulations"
private const val PREFS = "notifnix_local"

class MainActivity : FragmentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val database by lazy { ScenarioDatabase.get(this) }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) lifecycleScope.launch {
            val imported = runCatching {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read selected backup")
                val array = org.json.JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val row = array.optJSONObject(index) ?: return@mapNotNull null
                    val title = row.optString("title").trim()
                    val detail = row.optString("detail").trim()
                    val type = row.optString("type", "ALERT").uppercase()
                    if (title.isBlank() || type !in setOf("CALL", "MESSAGE", "ALERT")) return@mapNotNull null
                    ScenarioEntity(
                        id = row.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        type = type, title = title, detail = detail,
                        createdAt = row.optLong("createdAt", System.currentTimeMillis()),
                        favorite = row.optBoolean("favorite", false)
                    )
                }
            }
            imported.onSuccess { rows ->
                if (rows.isEmpty()) Toast.makeText(this@MainActivity, "No valid scenarios found in backup", Toast.LENGTH_LONG).show()
                else {
                    database.scenarios().upsertAll(rows)
                    Toast.makeText(this@MainActivity, "Imported ${rows.size} scenarios", Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "Import failed: ${error.message ?: "Invalid backup"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        setContent { NotifnixApp(this, database) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Notifnix test alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Local notifications created by the user for simulation and testing"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canUseBiometric(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticateBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onError(errString.toString()) }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Notifnix")
            .setSubtitle("Confirm your identity to continue")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun showTestNotification(title: String, body: String) {
        requestNotificationPermission()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openApp = Intent(this, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(this, 71, openApp, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SIMULATED · $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        runCatching { NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification) }
    }

    fun chooseSmsDateTime(current: Long, onPicked: (Long) -> Unit) {
        val initial = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                timeInMillis = current
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(this, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                onPicked(selected.timeInMillis)
            }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), false).show()
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun importScenarios() {
        importBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
    }

    fun schedule(item: ScenarioEntity) {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val date = Calendar.getInstance().apply { set(year, month, day) }
            TimePickerDialog(this, { _, hour, minute ->
                date.set(Calendar.HOUR_OF_DAY, hour)
                date.set(Calendar.MINUTE, minute)
                date.set(Calendar.SECOND, 0)
                val delay = (date.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                requestNotificationPermission()
                val data = Data.Builder()
                    .putString("title", item.title)
                    .putString("detail", item.detail)
                    .putString("scenarioId", item.id)
                    .putLong("scheduledAt", date.timeInMillis)
                    .build()
                val request = OneTimeWorkRequestBuilder<ScenarioWorker>()
                    .setInputData(data)
                    .addTag("notifnix_scheduled")
                    .addTag("scenario_" + item.id)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()
                lifecycleScope.launch { database.scenarios().upsert(item.copy(scheduledAt = date.timeInMillis)) }
                WorkManager.getInstance(this).enqueueUniqueWork(
                    "notifnix_" + item.id, ExistingWorkPolicy.REPLACE, request
                )
                Toast.makeText(this, "Simulation scheduled", Toast.LENGTH_SHORT).show()
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    fun exportScenarios(items: List<ScenarioEntity>) {
        val json = org.json.JSONArray()
        items.forEach { json.put(org.json.JSONObject().put("id", it.id).put("type", it.type).put("title", it.title).put("detail", it.detail).put("createdAt", it.createdAt).put("favorite", it.favorite)) }
        val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_SUBJECT, "Notifnix scenario backup"); putExtra(Intent.EXTRA_TEXT, json.toString(2)) }
        startActivity(Intent.createChooser(send, "Export Notifnix backup"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifnixApp(context: MainActivity, database: ScenarioDatabase) {
    val scope = rememberCoroutineScope()
    val history by database.scenarios().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val smsHistory by database.smsMessages().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var scheduled by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    DisposableEffect(context) {
        val liveData = WorkManager.getInstance(context).getWorkInfosByTagLiveData("notifnix_scheduled")
        val observer = Observer<List<WorkInfo>> { value -> scheduled = value ?: emptyList() }
        liveData.observe(context, observer)
        onDispose { liveData.removeObserver(observer) }
    }
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var selected by remember { mutableIntStateOf(0) }
    var caller by remember { mutableStateOf("Alex Morgan") }
    var number by remember { mutableStateOf("+1 202 555 0148") }
    var messageFrom by remember { mutableStateOf("Maya") }
    var messageBody by remember { mutableStateOf("Hey! This is a sample test message.") }
    var smsNumber by remember { mutableStateOf("+1 202 555 0148") }
    var smsFolder by remember { mutableStateOf("INBOX") }
    var smsCount by remember { mutableIntStateOf(1) }
    var smsTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notificationTitle by remember { mutableStateOf("Build complete") }
    var notificationBody by remember { mutableStateOf("Your test notification is ready.") }
    var showCallPreview by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    val initialPinRecord = remember { PinSecurity.loadOrMigrate(prefs) }
    var pinRecord by remember { mutableStateOf(initialPinRecord ?: "") }
    var hasPin by remember { mutableStateOf(initialPinRecord != null) }
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("biometric_enabled", false)) }
    var unlocked by remember { mutableStateOf(initialPinRecord == null) }
    var pinInput by remember { mutableStateOf("") }
    var pinDialog by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf("") }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (hasPin) { unlocked = false; pinInput = "" } }
    var editingId by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("Call", "Messages", "Alerts", "Library", "Schedule", "Settings")
    val palette = if (darkMode) darkColorScheme(primary = Color(0xFFB8AEFF), background = Color(0xFF101522), surface = Color(0xFF1A2232)) else lightColorScheme(primary = Violet, background = Canvas)
    LaunchedEffect(Unit) {
        if (!prefs.getBoolean("room_migrated", false)) {
            runCatching {
                val raw = prefs.getString("scenarios", "[]") ?: "[]"
                val legacy = org.json.JSONArray(raw)
                val migrated = (0 until legacy.length()).map { index ->
                    val row = legacy.getJSONObject(index)
                    ScenarioEntity(
                        id = row.optLong("id", System.currentTimeMillis() + index).toString(),
                        type = row.optString("type", "MESSAGE"),
                        title = row.optString("title", "Imported scenario"),
                        detail = row.optString("detail", ""),
                        createdAt = row.optLong("id", System.currentTimeMillis() + index)
                    )
                }
                if (migrated.isNotEmpty()) database.scenarios().upsertAll(migrated)
            }
            prefs.edit().putBoolean("room_migrated", true).apply()
        }
    }

    fun save(type: String, title: String, detail: String) {
        val existing = history.firstOrNull { it.id == editingId }
        val item = ScenarioEntity(
            id = editingId ?: java.util.UUID.randomUUID().toString(),
            type = type, title = title.trim(), detail = detail.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            favorite = existing?.favorite ?: false
        )
        scope.launch { database.scenarios().upsert(item) }
        editingId = null
        snackbar = "Scenario saved to your private library"
        selected = 3
    }

    MaterialTheme(colorScheme = palette) {
        if (!unlocked) {
            LockScreen(
                value = pinInput,
                onValue = { pinInput = it },
                unlock = { if (PinSecurity.verify(pinInput, pinRecord)) { unlocked = true; pinInput = "" } else pinInput = "" },
                biometricEnabled = biometricEnabled,
                onBiometric = { context.authenticateBiometric({ unlocked = true; pinInput = "" }, { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }) }
            )
            return@MaterialTheme
        }
        Scaffold(
            containerColor = palette.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("notifnix", fontWeight = FontWeight.ExtraBold)
                        Text("ONE APP. EVERY INTERACTION.", style = MaterialTheme.typography.labelSmall, color = Violet)
                    } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = palette.background)
                )
            },
            bottomBar = {
                NavigationBar {
                    val icons = listOf(Icons.Default.Call, Icons.Default.Chat, Icons.Default.Notifications, Icons.Default.PhoneInTalk, Icons.Default.Schedule, Icons.Default.Favorite)
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(selected = selected == index, onClick = { selected = index }, icon = { Icon(icons[index], null) }, label = { Text(label) })
                    }
                }
            },
            snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(8.dp))
                Surface(color = if (darkMode) Color(0xFF282344) else Color(0xFFECEAFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("SIMULATION MODE", color = Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Create safe, local test experiences.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Nothing is sent to real people. Every item is marked simulated.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (selected) {
                    0 -> {
                        SectionTitle("Incoming call studio", "Preview a caller screen without placing a real call.")
                        Field("Caller name", caller) { caller = it }
                        Field("Phone number", number) { number = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showCallPreview = true; save("CALL", caller, number) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("Save & preview") }
                            OutlinedButton(onClick = { caller = listOf("Jordan Lee", "Taylor Reed", "Casey Morgan", "Avery Parker").random(); number = "+1 202 555 01%02d".format((10..99).random()) }) { Text("Random") }
                        }
                        if (showCallPreview) { Spacer(Modifier.height(14.dp)); CallPreview(caller, number) { showCallPreview = false } }
                    }
                    1 -> {
                        SectionTitle("Simulated SMS studio", "Create test messages in a private Notifnix inbox. These never enter your phone's real SMS app.")
                        Field("Sender name / label", messageFrom) { messageFrom = it }
                        Field("Sender phone number", smsNumber) { smsNumber = it }
                        Field("Message text", messageBody) { messageBody = it }
                        Text("Destination folder", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("INBOX", "SENT", "DRAFT").forEach { folder ->
                                    FilterChip(selected = smsFolder == folder, onClick = { smsFolder = folder }, label = { Text(folder.lowercase().replaceFirstChar { it.uppercase() }) })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("OUTBOX", "FAILED", "QUEUED").forEach { folder ->
                                    FilterChip(selected = smsFolder == folder, onClick = { smsFolder = folder }, label = { Text(folder.lowercase().replaceFirstChar { it.uppercase() }) })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("Message date & time", fontWeight = FontWeight.SemiBold)
                                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(smsTimestamp)), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { context.chooseSmsDateTime(smsTimestamp) { smsTimestamp = it } }) { Text("Change") }
                        }
                        Text("Bulk generate: $smsCount message${if (smsCount == 1) "" else "s"}", fontWeight = FontWeight.SemiBold)
                        Slider(value = smsCount.toFloat(), onValueChange = { smsCount = it.toInt().coerceIn(1, 100) }, valueRange = 1f..100f, steps = 98)
                        MessagePreview(messageFrom, messageBody)
                        Button(
                            onClick = {
                                if (messageFrom.isBlank() || smsNumber.isBlank() || messageBody.isBlank()) {
                                    Toast.makeText(context, "Enter sender, number, and message text", Toast.LENGTH_SHORT).show()
                                } else {
                                    val rows = (0 until smsCount).map { index ->
                                        SimulatedSmsEntity(
                                            id = java.util.UUID.randomUUID().toString(),
                                            sender = messageFrom.trim(),
                                            phoneNumber = smsNumber.trim(),
                                            body = messageBody.trim(),
                                            folder = smsFolder,
                                            timestamp = smsTimestamp + index
                                        )
                                    }
                                    scope.launch {
                                        database.smsMessages().upsertAll(rows)
                                        snackbar = "Added $smsCount simulated SMS message${if (smsCount == 1) "" else "s"} to $smsFolder"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Generate $smsCount simulated SMS") }
                        Spacer(Modifier.height(14.dp))
                        Text("Local SMS history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val folderRows = smsHistory.filter { it.folder == smsFolder }
                        if (folderRows.isEmpty()) {
                            Text("No simulated messages in ${smsFolder.lowercase()} yet.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF737B8E))
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                                items(folderRows, key = { it.id }) { sms ->
                                    Surface(color = palette.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("SIMULATED · ${sms.folder}", color = Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    Text(sms.sender, fontWeight = FontWeight.SemiBold)
                                                    Text(sms.phoneNumber, style = MaterialTheme.typography.labelSmall)
                                                }
                                                IconButton(onClick = { scope.launch { database.smsMessages().delete(sms.id) } }) { Icon(Icons.Default.Delete, "Delete simulated message") }
                                            }
                                            Text(sms.body)
                                            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(sms.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color(0xFF737B8E))
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { save("MESSAGE", messageFrom, messageBody) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Also save as a library scenario")
                        }
                    }
                    2 -> {
                        SectionTitle("Notification studio", "Send a local test notification to this device.")
                        Field("Notification title", notificationTitle) { notificationTitle = it }
                        Field("Notification body", notificationBody) { notificationBody = it }
                        Button(onClick = { context.showTestNotification(notificationTitle, notificationBody); save("ALERT", notificationTitle, notificationBody) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text("Show test notification") }
                    }
                    3 -> {
                        SectionTitle("Scenario library", "Search, edit, favorite, schedule, or export your saved scenarios.")
                        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search scenarios") })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("All") })
                            Spacer(Modifier.width(8.dp))
                            FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Favorites") })
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { context.exportScenarios(history) }) { Text("Export backup") }
                        }
                        val visible = history.filter { (!favoritesOnly || it.favorite) && (it.title.contains(search, true) || it.detail.contains(search, true) || it.type.contains(search, true)) }
                        if (visible.isEmpty()) EmptyState()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                            items(visible, key = { it.id }) { item ->
                                Surface(color = palette.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(item.type + " · SIMULATED", color = Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                Text(item.title, fontWeight = FontWeight.SemiBold)
                                                Text(item.detail, style = MaterialTheme.typography.bodySmall)
                                            }
                                            IconButton(onClick = { scope.launch { database.scenarios().upsert(item.copy(favorite = !item.favorite)) } }) { Icon(if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = Violet) }
                                            IconButton(onClick = { scope.launch { WorkManager.getInstance(context).cancelUniqueWork("notifnix_" + item.id); database.scenarios().delete(item.id) } }) { Icon(Icons.Default.Delete, "Delete") }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = {
                                                editingId = item.id
                                                when (item.type) {
                                                    "CALL" -> { caller = item.title; number = item.detail; selected = 0 }
                                                    "MESSAGE" -> { messageFrom = item.title; messageBody = item.detail; selected = 1 }
                                                    else -> { notificationTitle = item.title; notificationBody = item.detail; selected = 2 }
                                                }
                                            }) { Text("Edit") }
                                            TextButton(onClick = { context.schedule(item); snackbar = "Choose a date and time for this simulation" }) { Text("Schedule") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    4 -> {
                        SectionTitle("Scheduled simulations", "Manage local reminders created from your scenario library.")
                        val upcoming: List<Pair<WorkInfo, ScenarioEntity>> = scheduled.mapNotNull { work: WorkInfo ->
                            val scenarioId: String? = work.tags.firstOrNull { tag -> tag.startsWith("scenario_") }?.removePrefix("scenario_")
                            val scenario = history.firstOrNull { it.id == scenarioId }
                            if (scenario == null) null else work to scenario
                        }.sortedBy { it.second.scheduledAt ?: Long.MAX_VALUE }
                        if (upcoming.isEmpty()) {
                            EmptyState()
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                                items(upcoming, key = { it.first.id.toString() }) { (work, scenario) ->
                                    val canCancel = work.state == WorkInfo.State.ENQUEUED || work.state == WorkInfo.State.BLOCKED
                                    Surface(color = palette.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text("SIMULATED · " + work.state.name, color = Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text(scenario.title, fontWeight = FontWeight.SemiBold)
                                            Text(scenario.detail, style = MaterialTheme.typography.bodySmall)
                                            scenario.scheduledAt?.let { at ->
                                                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(at)), style = MaterialTheme.typography.labelSmall)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = { context.schedule(scenario) }) { Text("Reschedule") }
                                                if (canCancel) TextButton(onClick = {
                                                    scope.launch { WorkManager.getInstance(context).cancelWorkById(work.id); database.scenarios().clearScheduledAt(scenario.id) }
                                                }) { Text("Cancel") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        SectionTitle("Settings & tools", "Control privacy and personalize Notifnix.")
                        SettingRow("Dark appearance", "Use a darker color palette") {
                            Switch(checked = darkMode, onCheckedChange = { darkMode = it; prefs.edit().putBoolean("dark_mode", it).apply() })
                        }
                        SettingRow("App lock", if (!hasPin) "Set a local PIN to protect this app" else "PIN protection is enabled") {
                            TextButton(onClick = { pinInput = ""; pinDialog = true }) { Text(if (!hasPin) "Set PIN" else "Change PIN") }
                        }
                        SettingRow("Biometric unlock", if (!hasPin) "Set a PIN first to enable biometric fallback" else "Use fingerprint or supported face unlock") {
                            Switch(
                                checked = biometricEnabled,
                                enabled = hasPin,
                                onCheckedChange = { enabled ->
                                    if (enabled && !context.canUseBiometric()) {
                                        Toast.makeText(context, "Set up a strong biometric in Android settings first", Toast.LENGTH_LONG).show()
                                    } else {
                                        biometricEnabled = enabled
                                        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
                                    }
                                }
                            )
                        }
                        Button(onClick = { context.exportScenarios(history) }, modifier = Modifier.fillMaxWidth()) { Text("Export scenario backup") }
                        OutlinedButton(onClick = { context.importScenarios() }, modifier = Modifier.fillMaxWidth()) { Text("Import scenario backup") }
                        OutlinedButton(onClick = {
                            val examples = listOf(
                                ScenarioEntity(java.util.UUID.randomUUID().toString(), "MESSAGE", "Delivery update", "Your sample order is out for delivery."),
                                ScenarioEntity(java.util.UUID.randomUUID().toString(), "ALERT", "Calendar reminder", "Your demo appointment starts in 15 minutes."),
                                ScenarioEntity(java.util.UUID.randomUUID().toString(), "CALL", "Unknown caller", "+1 202 555 0188")
                            )
                            scope.launch { database.scenarios().upsertAll(examples); selected = 3 }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add sample template pack") }
                        OutlinedButton(onClick = {
                            val templates = listOf("Build complete" to "Your test build finished successfully.", "Reminder" to "This is a scheduled sample reminder.", "New message" to "You have a sample message waiting.")
                            val sample = templates.random()
                            notificationTitle = sample.first; notificationBody = sample.second; selected = 2
                        }, modifier = Modifier.fillMaxWidth()) { Text("Random notification template") }
                        Text("Notifnix stores scenarios locally. Export creates a shareable JSON backup; no account or cloud sync is used.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(snackbar.ifBlank { "Offline-first • Private by design • No real calls or SMS" }, color = Color(0xFF7A8192), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
            }
        }
        if (pinDialog) {
            AlertDialog(
                onDismissRequest = { pinDialog = false },
                title = { Text(if (!hasPin) "Set app PIN" else "Change app PIN") },
                text = { OutlinedTextField(pinInput, { pinInput = it.filter(Char::isDigit).take(6) }, label = { Text("New 4–6 digit PIN") }, singleLine = true) },
                confirmButton = { TextButton(onClick = { if (pinInput.length in 4..6) { pinRecord = PinSecurity.createRecord(pinInput); hasPin = true; unlocked = true; prefs.edit().putString("app_pin_hash", pinRecord).remove("app_pin").apply(); pinInput = ""; pinDialog = false } }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { pinDialog = false; pinInput = "" }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun LockScreen(
    value: String,
    onValue: (String) -> Unit,
    unlock: () -> Unit,
    biometricEnabled: Boolean,
    onBiometric: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Notifnix is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Enter your local app PIN to continue.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value, onValue, label = { Text("PIN") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        Button(onClick = unlock) { Text("Unlock with PIN") }
        if (biometricEnabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBiometric) { Text("Unlock with fingerprint / face") }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        trailing()
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF737B8E))
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = label != "Message text" && label != "Notification body")
    }
}

@Composable
private fun MessagePreview(sender: String, body: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("SIMULATED MESSAGE", color = Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(sender, fontWeight = FontWeight.Bold)
            Text(body)
        }
    }
}

@Composable
private fun CallPreview(name: String, phone: String, onClose: () -> Unit) {
    Surface(color = Color(0xFF172033), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SIMULATED INCOMING CALL", color = Color(0xFFBDB5FF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(76.dp).background(Color(0xFF5146A8), CircleShape), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(phone, color = Color(0xFFCFD3E0))
            Spacer(Modifier.height(22.dp))
            Button(onClick = onClose) { Text("End preview") }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
        Text("No matching scenarios yet. Create one from any studio.")
    }
}

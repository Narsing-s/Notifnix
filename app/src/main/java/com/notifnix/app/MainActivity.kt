package com.notifnix.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PhoneInTalk
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
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF172033)
private val Violet = Color(0xFF6657D9)
private val Canvas = Color(0xFFF5F6FB)
private const val CHANNEL_ID = "notifnix_simulations"
private const val PREFS = "notifnix_local"

data class Scenario(val id: Long, val type: String, val title: String, val detail: String)

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Violet, background = Canvas)) { NotifnixApp(this) } }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Notifnix test alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Local notifications created by the user for simulation and testing"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
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
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifnixApp(context: MainActivity) {
    var selected by remember { mutableIntStateOf(0) }
    var caller by remember { mutableStateOf("Alex Morgan") }
    var number by remember { mutableStateOf("+1 202 555 0148") }
    var messageFrom by remember { mutableStateOf("Maya") }
    var messageBody by remember { mutableStateOf("Hey! This is a sample test message.") }
    var notificationTitle by remember { mutableStateOf("Build complete") }
    var notificationBody by remember { mutableStateOf("Your test notification is ready.") }
    var history by remember { mutableStateOf(loadScenarios(context)) }
    var showCallPreview by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf("") }
    val tabs = listOf("Call", "Messages", "Alerts", "Library")

    fun save(type: String, title: String, detail: String) {
        val item = Scenario(System.currentTimeMillis(), type, title, detail)
        history = listOf(item) + history
        persistScenarios(context, history)
        snackbar = "$type simulation saved"
    }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("notifnix", fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text("ONE APP. EVERY INTERACTION.", style = MaterialTheme.typography.labelSmall, color = Violet)
                } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Canvas)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val icons = listOf(Icons.Default.Call, Icons.Default.Chat, Icons.Default.Notifications, Icons.Default.PhoneInTalk)
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(selected = selected == index, onClick = { selected = index }, icon = { Icon(icons[index], null) }, label = { Text(label) })
                }
            }
        },
        snackbarHost = { if (snackbar.isNotBlank()) Snackbar { Text(snackbar) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            Surface(color = Color(0xFFECEAFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SIMULATION MODE", color = Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Create safe, local test experiences.", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Nothing is sent to real people. Every item is marked simulated.", color = Color(0xFF596174), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            when (selected) {
                0 -> {
                    SectionTitle("Incoming call studio", "Preview a caller screen without placing a real call.")
                    Field("Caller name", caller) { caller = it }
                    Field("Phone number", number) { number = it }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showCallPreview = true; save("CALL", caller, number) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("Preview simulated call") }
                    if (showCallPreview) {
                        Spacer(Modifier.height(14.dp))
                        CallPreview(caller, number, onClose = { showCallPreview = false })
                    }
                }
                1 -> {
                    SectionTitle("Message studio", "Compose a sample SMS-style conversation.")
                    Field("Sender name", messageFrom) { messageFrom = it }
                    Field("Message text", messageBody) { messageBody = it }
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("SIMULATED MESSAGE", color = Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(messageFrom, fontWeight = FontWeight.Bold, color = Ink)
                            Text(messageBody, color = Ink)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { save("MESSAGE", messageFrom, messageBody) }, modifier = Modifier.fillMaxWidth()) { Text("Save message scenario") }
                }
                2 -> {
                    SectionTitle("Notification studio", "Send a local test notification to this device.")
                    Field("Notification title", notificationTitle) { notificationTitle = it }
                    Field("Notification body", notificationBody) { notificationBody = it }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { context.showTestNotification(notificationTitle, notificationBody); save("ALERT", notificationTitle, notificationBody) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text("Show test notification") }
                }
                else -> {
                    SectionTitle("Scenario library", "Your saved simulations stay on this device.")
                    if (history.isEmpty()) EmptyState()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(history, key = { it.id }) { item ->
                            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.type + " · SIMULATED", color = Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(item.title, color = Ink, fontWeight = FontWeight.SemiBold)
                                        Text(item.detail, color = Color(0xFF687084), style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { history = history.filterNot { it.id == item.id }; persistScenarios(context, history) }) { Icon(Icons.Default.Delete, "Delete scenario", tint = Color(0xFF8A91A2)) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Offline-first • Private by design • No real calls or SMS", color = Color(0xFF7A8192), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF737B8E))
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = label != "Message text" && label != "Notification body")
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
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E9D69))) { Text("End preview") }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
        Text("No scenarios saved yet. Create one from any studio.", color = Color(0xFF737B8E))
    }
}

private fun loadScenarios(context: Context): List<Scenario> = runCatching {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("scenarios", "[]") ?: "[]"
    val array = JSONArray(raw)
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Scenario(o.getLong("id"), o.getString("type"), o.getString("title"), o.getString("detail"))
    }
}.getOrDefault(emptyList())

private fun persistScenarios(context: Context, items: List<Scenario>) {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject().put("id", item.id).put("type", item.type).put("title", item.title).put("detail", item.detail))
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("scenarios", array.toString()).apply()
}

package com.msgs.app.ui

import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.BuildConfig
import com.msgs.app.engine.Engine
import com.msgs.app.ui.theme.Ink
import com.msgs.app.ui.theme.SansCondensed
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * R4 diagnostics: what the messaging engine has actually done. Nothing here is
 * inferred; a value that hasn't happened yet says so. No message text, phone
 * numbers or keys are shown.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val status by Engine.status.collectAsState()
    val diag by Engine.diag.collectAsState()
    val convs by Engine.conversations.collectAsState()
    val msgs by Engine.messages.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmUnpair by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }

    val defaultSms = remember(now) { runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull() }
    val (connLabel, connColor) = connection(status.paired, status.state, status.detail, status.phoneResponding)
    val rcs = convs.values.count { it.rcs }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Diagnostics", fontFamily = SansCondensed, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, color = Ink.Text)
        Text("R4 test build ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = Ink.Text3)

        Group("Connection")
        Item("Pairing", if (status.paired) "Paired by QR code; keys encrypted with Android Keystore" else "Not paired")
        Item("Authentication", auth(status.paired, status.state, status.detail))
        Item("Connection", connLabel, connColor)
        Item("Connect attempts (this run)", diag.connectAttempts.toString())
        Item("Automatic reconnects (this run)", diag.reconnects.toString())
        Item("Default texting app", when (defaultSms) {
            null -> "Unknown"
            "com.google.android.apps.messaging" -> "Google Messages"
            else -> defaultSms
        }, if (defaultSms != null && defaultSms != "com.google.android.apps.messaging") Ink.Warn else Ink.Text)

        Group("Data")
        Item("Conversations loaded", if (status.paired) convs.size.toString() else "—")
        Item("Transport (as Google reports it)", if (convs.isEmpty()) "Not available yet" else "$rcs RCS · ${convs.size - rcs} SMS/MMS")
        Item("Messages held in this app", "${msgs.values.sumOf { it.size }} (opened chats only, not full history)")
        Item("Last successful sync", ago(diag.lastSync, now))
        Item("Last successful send", ago(diag.lastSend, now))
        Item("Last message received", ago(diag.lastReceived, now))

        Group("Problems")
        Item("Last error", if (diag.lastError.isBlank()) "None this run" else "${diag.lastError} (${ago(diag.lastErrorAt, now)})",
            if (diag.lastError.isBlank()) Ink.Text else Ink.Danger)
        if (diag.legacyRemoved) Item("Old sign-in data", "Found and deleted at startup (Google cookies from builds 1–5)", Ink.Warn)
        if (diag.lastUnpair.isNotBlank()) Item("Last unpair", diag.lastUnpair)
        Item("Deleting messages/chats", if (BuildConfig.ALLOW_DELETE) "On" else "Off in this build")
        Item("In-app updater", if (BuildConfig.UPDATER_ENABLED) "On" else "Off in this build")

        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (status.paired) PillButton("Reconnect", accent = true) { Engine.connect() }
            if (status.paired) PillButton("Unpair") { confirmUnpair = true }
            PillButton("Back", onClick = onBack)
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair Msgs?") },
            text = { Text("This asks Google to revoke this pairing, then deletes the pairing keys, cached chats, avatars and logs from this phone. Messages in Google Messages aren't touched.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; Engine.unpair() }) { Text("Unpair", color = Ink.Danger) } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

private fun connection(paired: Boolean, state: String, detail: String, phoneOk: Boolean): Pair<String, Color> = when {
    !paired && (state == "unpaired" && detail.isNotBlank()) -> "Authentication required ($detail)" to Ink.Danger
    !paired -> "Not paired" to Ink.Text2
    state == "connected" && !phoneOk -> "Connected, but the phone isn't responding" to Ink.Warn
    state == "connected" -> "Connected" to Ink.GreenText
    state == "connecting" -> "Connecting" to Ink.Warn
    state == "reconnecting" -> "Reconnecting" to Ink.Warn
    state == "phone_not_responding" -> "Connected, but the phone isn't responding" to Ink.Warn
    state == "inactive" -> "Active on another device (Messages for web elsewhere)" to Ink.Warn
    state == "error" -> "Failed${if (detail.isNotBlank()) ": $detail" else ""}" to Ink.Danger
    else -> "Disconnected" to Ink.Text2
}

private fun auth(paired: Boolean, state: String, detail: String): String = when {
    paired -> "Session keys present"
    state == "unpaired" && detail.isNotBlank() -> "Required: $detail"
    else -> "No session"
}

private fun ago(ts: Long, now: Long): String {
    if (ts <= 0L) return "Not yet"
    val s = (now - ts) / 1000
    val rel = when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        else -> "${s / 3600} h ago"
    }
    return "$rel · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))}"
}

@Composable
private fun Group(title: String) =
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text3, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))

@Composable
private fun Item(label: String, value: String, color: Color = Ink.Text) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, color = Ink.Text2)
        Text(value, fontSize = 16.sp, lineHeight = 21.sp, color = color)
    }
}

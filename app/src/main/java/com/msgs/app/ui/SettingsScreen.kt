package com.msgs.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.msgs.app.BuildConfig
import com.msgs.app.data.Prefs
import com.msgs.app.engine.Engine
import com.msgs.app.notify.Notifications
import com.msgs.app.ui.theme.Sans
import com.msgs.app.ui.theme.SansCondensed
import com.msgs.app.ui.theme.Serif
import com.msgs.app.ui.theme.Ink
import com.msgs.app.update.TokenStore
import com.msgs.app.update.Updater
import com.msgs.app.util.CrashLog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val relay by Prefs.relay.collectAsState()
    val update by Updater.state.collectAsState()
    val status by Engine.status.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val listenerOn = remember(resumeTick) { Notifications.hasListenerAccess(context) }
    val postAllowed = remember(resumeTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val askPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(TokenStore.has(context)) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = Ink.Green,
        checkedThumbColor = Ink.Text,
        uncheckedTrackColor = Ink.Raised2,
        uncheckedThumbColor = Ink.Text3,
        uncheckedBorderColor = Ink.Line2,
    )

    LazyColumn(Modifier.fillMaxSize().background(Ink.Bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 4.dp)) {
                BackLink("Messages", onBack)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Settings",
                    fontFamily = SansCondensed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 56.sp,
                    lineHeight = 52.sp,
                    letterSpacing = (-1.4).sp,
                    color = Ink.Text,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }

        item {
            Section("Notifications") {
                SettingRow(MsgsIcons.Bell, "Msgs notifications", trailing = {
                    Text(
                        if (listenerOn) "On" else "Turn on",
                        color = if (listenerOn) Ink.Text2 else Ink.GreenText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }, onClick = { Notifications.openListenerSettings(context) })
                RowDivider()
                SettingRow(MsgsIcons.BellOff, "Silence Google Messages alerts", onClick = { Notifications.openGoogleMessagesNotificationSettings(context) })
                RowDivider()
                SettingRow(MsgsIcons.Bubble, "Take over Google Messages alerts", trailing = {
                    Switch(checked = relay, onCheckedChange = { Prefs.setRelay(it) }, colors = switchColors)
                }, onClick = { Prefs.setRelay(!relay) })
                if (!postAllowed) {
                    RowDivider()
                    SettingRow(MsgsIcons.Info, "Allow Msgs to show notifications", color = Ink.GreenText, onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPost.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
                Hint("Turn on access so Msgs can replace Google Messages' notifications. Then set Google Messages' notifications to Silent, not Off, so you only hear Msgs.")
            }
        }

        item {
            Section("Updates") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            when (val s = update) {
                                Updater.State.Idle -> "Build ${BuildConfig.VERSION_CODE}"
                                Updater.State.Checking -> "Checking…"
                                is Updater.State.UpToDate -> "You're on the newest build"
                                is Updater.State.Available -> "Build ${s.release.build} is ready"
                                is Updater.State.Downloading -> "Downloading build ${s.release.build}…"
                                is Updater.State.Ready -> "Build ${s.release.build} downloaded"
                                is Updater.State.Failed -> s.message
                            },
                            fontSize = 13.sp,
                            color = if (update is Updater.State.Failed) Ink.Danger else Ink.Text2,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    when (val s = update) {
                        is Updater.State.Available -> PillButton("Install", accent = true) {
                            scope.launch {
                                if (Updater.ensureInstallPermission(context)) {
                                    Updater.download(context, s.release)?.let { Updater.install(context, it) }
                                }
                            }
                        }
                        is Updater.State.Ready -> PillButton("Install", accent = true) {
                            if (Updater.ensureInstallPermission(context)) Updater.install(context, s.file)
                        }
                        is Updater.State.Downloading, Updater.State.Checking -> Unit
                        else -> PillButton("Check") { scope.launch { Updater.check(context, force = true) } }
                    }
                }
                (update as? Updater.State.Downloading)?.let {
                    LinearProgressIndicator(
                        progress = { it.progress },
                        color = Ink.GreenText,
                        trackColor = Ink.Raised2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                RowDivider()
                if (hasToken) {
                    SettingRow(MsgsIcons.Key, "GitHub token saved", trailing = {
                        Text("Remove", color = Ink.Danger, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }, onClick = {
                        TokenStore.clear(context)
                        hasToken = false
                    })
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ink.Raised2)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = token,
                                onValueChange = { token = it.trim() },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                textStyle = TextStyle(fontFamily = Sans, fontSize = 15.sp, color = Ink.Text),
                                cursorBrush = SolidColor(Ink.GreenText),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box {
                                        if (token.isEmpty()) Text("GitHub token (optional)", fontSize = 15.sp, color = Ink.Text3)
                                        inner()
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        PillButton("Save", accent = token.isNotBlank()) {
                            if (token.isNotBlank()) {
                                TokenStore.save(context, token)
                                token = ""
                                hasToken = true
                                Engine.toast("Token saved")
                            }
                        }
                    }
                }
                Hint("Updates come from your GitHub repo. A token is only needed if the repo is private. It's stored encrypted on this phone.")
            }
        }

        item {
            Section("Connection") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(if (status.state == "connected") Ink.GreenText else Ink.Text2))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (status.state) {
                            "connected" -> if (status.phoneResponding) "Connected to Google Messages" else "Connected, but your phone isn't responding"
                            "inactive" -> "Messages is active on another device"
                            "connecting", "reconnecting" -> "Connecting…"
                            "error" -> "Can't connect right now"
                            else -> "Not connected"
                        },
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (status.state == "inactive") {
                        PillButton("Use here", accent = true) { Engine.setActiveHere() }
                    } else if (status.state != "connected") {
                        PillButton("Retry") { Engine.connect() }
                    }
                }
                RowDivider()
                DangerRow(MsgsIcons.Unlink, "Unpair from Google Messages") { confirmUnpair = true }
            }
        }

        item {
            Section("Diagnostics") {
                SettingRow(MsgsIcons.Copy, "Copy engine log", onClick = {
                    clipboard.setText(AnnotatedString(runCatching { Engine.bridge.readLog() }.getOrDefault("")))
                    Engine.toast("Engine log copied")
                })
                RowDivider()
                SettingRow(MsgsIcons.Copy, "Copy crash log", onClick = {
                    val log = CrashLog.read(context)
                    clipboard.setText(AnnotatedString(log.ifBlank { "No crashes logged" }))
                    Engine.toast(if (log.isBlank()) "No crashes logged" else "Crash log copied")
                })
                RowDivider()
                SettingRow(MsgsIcons.Trash, "Clear crash log", onClick = {
                    CrashLog.clear(context)
                    Engine.toast("Crash log cleared")
                })
                Hint("If something breaks, copy a log and paste it to Claude.")
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair Msgs?") },
            text = { Text("Msgs will disconnect from Google Messages. You can pair again any time.") },
            confirmButton = {
                TextButton(onClick = { confirmUnpair = false; Engine.unpair() }) { Text("Unpair", color = Ink.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel", color = Ink.Text) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
        Text(
            title,
            fontFamily = Serif,
            fontSize = 18.sp,
            color = Ink.Text2,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().panel(), content = content)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = Ink.Text3, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp))
}

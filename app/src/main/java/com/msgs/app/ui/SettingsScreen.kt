package com.msgs.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import com.msgs.app.ui.theme.Danger
import com.msgs.app.ui.theme.Geist
import com.msgs.app.ui.theme.LocalPalette
import com.msgs.app.ui.theme.Palettes
import com.msgs.app.ui.theme.TextPrimary
import com.msgs.app.ui.theme.TextSecondary
import com.msgs.app.ui.theme.TextTertiary
import com.msgs.app.ui.theme.TitleFont
import com.msgs.app.ui.theme.barGlass
import com.msgs.app.ui.theme.glass
import com.msgs.app.update.TokenStore
import com.msgs.app.update.Updater
import com.msgs.app.util.CrashLog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val paletteName by Prefs.palette.collectAsState()
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(MsgsIcons.Back, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("SETTINGS", fontFamily = TitleFont, fontSize = 34.sp, letterSpacing = 0.5.sp, color = TextPrimary)
            }
        }

        item {
            Section("Look") {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Palettes.forEach { pal ->
                        val selected = pal.name == paletteName
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton) { Prefs.setPalette(pal.name) }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(58.dp)
                                    .border(if (selected) 2.dp else 1.dp, if (selected) pal.accent else Color.White.copy(alpha = 0.15f), CircleShape)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(pal.glow1.copy(alpha = 1f), pal.top, pal.bottom), start = Offset.Zero, end = Offset.Infinite)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(pal.accent))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(pal.name, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) TextPrimary else TextSecondary)
                        }
                    }
                }
            }
        }

        item {
            Section("Notifications") {
                SettingRow(MsgsIcons.Bell, "Msgs notifications", trailing = {
                    Text(if (listenerOn) "On" else "Turn on", color = if (listenerOn) TextSecondary else p.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }, onClick = { Notifications.openListenerSettings(context) })
                RowDivider()
                SettingRow(MsgsIcons.BellOff, "Silence Google Messages alerts", onClick = { Notifications.openGoogleMessagesNotificationSettings(context) })
                RowDivider()
                SettingRow(MsgsIcons.Bubble, "Take over Google Messages alerts", trailing = {
                    Switch(
                        checked = relay,
                        onCheckedChange = { Prefs.setRelay(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = p.accent,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }, onClick = { Prefs.setRelay(!relay) })
                if (!postAllowed) {
                    RowDivider()
                    SettingRow(MsgsIcons.Info, "Allow Msgs to show notifications", color = p.accent, onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPost.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
                Hint("Turn on access so Msgs can replace Google Messages' notifications. Then set Google Messages' notifications to silent, so you only hear Msgs.")
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
                            color = if (update is Updater.State.Failed) Danger else TextSecondary,
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
                        color = p.accent,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                RowDivider()
                if (hasToken) {
                    SettingRow(MsgsIcons.Key, "GitHub token saved", trailing = {
                        Text("Remove", color = Danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }, onClick = {
                        TokenStore.clear(context)
                        hasToken = false
                    })
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).height(46.dp).barGlass(RoundedCornerShape(23.dp)).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = token,
                                onValueChange = { token = it.trim() },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                textStyle = TextStyle(fontFamily = Geist, fontSize = 15.sp, color = TextPrimary),
                                cursorBrush = SolidColor(p.accent),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box {
                                        if (token.isEmpty()) Text("GitHub token (optional)", fontSize = 15.sp, color = TextTertiary)
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
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (status.state == "connected") p.accent else Color(0xFFF2C14E)))
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
                    if (status.state == "inactive") PillButton("Use here", accent = true) { Engine.setActiveHere() }
                    else if (status.state != "connected") PillButton("Retry") { Engine.connect() }
                }
                RowDivider()
                SettingRow(MsgsIcons.Unlink, "Unpair from Google Messages", color = Danger, onClick = { confirmUnpair = true })
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
                TextButton(onClick = { confirmUnpair = false; Engine.unpair() }) { Text("Unpair", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel", color = TextPrimary) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), fill = 0.08f, edge = 0.1f), content = content)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp))
}

@Composable
fun PillButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (accent) p.accent else Color.White.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (accent) p.onAccent else TextPrimary)
    }
}

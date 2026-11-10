package com.msgs.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.data.Prefs
import com.msgs.app.engine.AvatarRepo
import com.msgs.app.engine.Engine
import com.msgs.app.engine.Media
import com.msgs.app.engine.MediaRepo
import com.msgs.app.notify.Notifications
import com.msgs.app.ui.theme.Danger
import com.msgs.app.ui.theme.LocalPalette
import com.msgs.app.ui.theme.TextPrimary
import com.msgs.app.ui.theme.TextSecondary
import com.msgs.app.ui.theme.glass
import com.msgs.app.ui.theme.nameStyle
import kotlinx.coroutines.launch

@Composable
fun ContactScreen(convId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val conv = Engine.conversations.collectAsState().value[convId]
    val pins by Prefs.pins.collectAsState()
    val messages = Engine.messages.collectAsState().value[convId].orEmpty()
    val version by AvatarRepo.version.collectAsState()
    val key = remember(conv?.id, conv?.groupAvatarUrl) { AvatarRepo.keyFor(conv) }
    val hasCustom = remember(key, version) { AvatarRepo.hasCustom(key) }
    var viewer by remember { mutableStateOf<Media?>(null) }
    var muteMenu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { runCatching { AvatarRepo.setCustom(context, key, uri) }.onFailure { Engine.report(it) } }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val number = conv?.other?.let { it.number.ifBlank { it.rawNumber } }
    val dialNumber = conv?.other?.let { it.rawNumber.ifBlank { it.number } }
    val media = remember(messages) { messages.flatMap { it.media }.filter { it.isImage || it.isVideo }.reversed() }
    val pinned = convId in pins

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(MsgsIcons.Back, "Back to chat", onBack)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(124.dp)) {
                    Avatar(conv, 124.dp, modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape))
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(p.top)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(p.accent)
                            .clickable(role = Role.Button, onClick = pickPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MsgsIcons.Camera, contentDescription = "Choose a photo", tint = p.onAccent, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(conv?.title.orEmpty(), style = nameStyle(30), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!number.isNullOrBlank()) {
                    Text(number, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                } else if (conv?.isGroup == true) {
                    Text("${conv.others.size + 1} people", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (conv?.isGroup == false && !dialNumber.isNullOrBlank()) {
                    ActionTile(MsgsIcons.Phone, "Call", Modifier.weight(1f)) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))) }
                    }
                }
                ActionTile(MsgsIcons.Bubble, "Message", Modifier.weight(1f), onClick = onBack)
                Box(Modifier.weight(1f)) {
                    ActionTile(MsgsIcons.BellOff, "Mute", Modifier.fillMaxWidth()) { muteMenu = true }
                    DropdownMenu(expanded = muteMenu, onDismissRequest = { muteMenu = false }) {
                        DropdownMenuItem(text = { Text("Mute") }, onClick = { Engine.setMuted(convId, true); muteMenu = false })
                        DropdownMenuItem(text = { Text("Unmute") }, onClick = { Engine.setMuted(convId, false); muteMenu = false })
                    }
                }
                ActionTile(MsgsIcons.Pin, if (pinned) "Unpin" else "Pin", Modifier.weight(1f)) { Prefs.setPinned(convId, !pinned) }
            }
        }
        if (media.isNotEmpty()) {
            item {
                Column(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(24.dp), fill = 0.08f, edge = 0.1f)
                        .padding(14.dp),
                ) {
                    Text("Photos and media", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        media.take(4).forEachIndexed { i, m ->
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).clickable { if (m.isImage) viewer = m }) {
                                val bmp by produceState<ImageBitmap?>(null, m.cacheKey) { value = MediaRepo.thumbnail(m) }
                                bmp?.let { Image(it, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                if (i == 3 && media.size > 4) {
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                        Text("+${media.size - 4}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        repeat((4 - media.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Column(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), fill = 0.08f, edge = 0.1f),
            ) {
                SettingRow(MsgsIcons.Image, if (conv?.isGroup == true) "Group photo" else "Contact photo", trailing = {
                    Text(if (hasCustom) "Change" else "Choose", color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }, onClick = pickPhoto)
                if (hasCustom) {
                    RowDivider()
                    SettingRow(MsgsIcons.Close, "Remove my photo", onClick = { AvatarRepo.removeCustom(key) })
                }
                RowDivider()
                SettingRow(MsgsIcons.Pin, "Pin to top", trailing = {
                    Switch(
                        checked = pinned,
                        onCheckedChange = { Prefs.setPinned(convId, it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = p.accent,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }, onClick = { Prefs.setPinned(convId, !pinned) })
                RowDivider()
                SettingRow(MsgsIcons.Bell, "Notifications", onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_MESSAGES)
                        )
                    }
                })
                RowDivider()
                SettingRow(MsgsIcons.Archive, if (conv?.isArchived == true) "Unarchive" else "Archive", onClick = {
                    Engine.setArchived(convId, conv?.isArchived != true)
                    onBack()
                })
            }
        }
        if (conv?.isGroup == true) {
            item {
                Column(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(24.dp), fill = 0.08f, edge = 0.1f)
                        .padding(vertical = 6.dp),
                ) {
                    Text("Members", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    conv.others.forEach { person ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(conv, 40.dp, person = person)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName, style = nameStyle(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (person.name.isNotBlank() && person.number.isNotBlank()) {
                                    Text(person.number, fontSize = 13.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Photos you choose here stay in Msgs and override the picture from your contacts.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
            )
        }
    }

    viewer?.let { ImageViewer(it) { viewer = null } }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(70.dp)
            .glass(RoundedCornerShape(20.dp), fill = 0.09f, edge = 0.11f)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    label: String,
    color: Color = TextPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = color, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.padding(start = 51.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
fun DangerRow(icon: ImageVector, label: String, onClick: () -> Unit) = SettingRow(icon, label, color = Danger, onClick = onClick)

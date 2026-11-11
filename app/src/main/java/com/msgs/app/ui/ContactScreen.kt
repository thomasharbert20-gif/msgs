package com.msgs.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.data.Prefs
import com.msgs.app.engine.AvatarRepo
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Engine
import com.msgs.app.engine.Media
import com.msgs.app.engine.MediaRepo
import com.msgs.app.notify.Notifications
import com.msgs.app.ui.theme.SansCondensed
import com.msgs.app.ui.theme.Serif

import com.msgs.app.ui.theme.Ink
import kotlinx.coroutines.launch

@Composable
fun ContactScreen(convId: String, onBack: () -> Unit) {
    val context = LocalContext.current
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
    val isPerson = conv?.isGroup == false && !dialNumber.isNullOrBlank()

    LazyColumn(Modifier.fillMaxSize().background(Ink.Bg), contentPadding = PaddingValues(bottom = 36.dp)) {
        item {
            Poster(
                conv = conv,
                onBack = onBack,
                onEdit = if (isPerson) ({ editContact(context, dialNumber.orEmpty(), conv?.title.orEmpty()) }) else null,
                onPhoto = pickPhoto,
                photoLabel = if (hasCustom) "Change photo" else "Choose a photo",
            )
        }
        item {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp)) {
                Text(
                    conv?.title.orEmpty(),
                    fontFamily = SansCondensed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 46.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-1).sp,
                    color = Ink.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    !number.isNullOrBlank() -> Text(number, fontFamily = Serif, fontSize = 18.sp, color = Ink.Text2)
                    conv?.isGroup == true -> Text("${conv.others.size + 1} people", fontFamily = Serif, fontSize = 18.sp, color = Ink.Text2)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionTile(MsgsIcons.Message, "Message", Modifier.weight(1f), accent = true, onClick = onBack)
                if (isPerson) {
                    ActionTile(MsgsIcons.Phone, "Call", Modifier.weight(1f)) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))) }
                    }
                }
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
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Photos and media", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(media.size.toString(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text3)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        media.take(4).forEachIndexed { i, m ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(PortraitShape)
                                    .background(Ink.Raised)
                                    .clickable { if (m.isImage) viewer = m },
                            ) {
                                val bmp by produceState<ImageBitmap?>(null, m.cacheKey) { value = MediaRepo.thumbnail(m) }
                                bmp?.let { Image(it, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                if (i == 3 && media.size > 4) {
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
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
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().panel()) {
                SettingRow(MsgsIcons.Image, if (conv?.isGroup == true) "Group photo" else "Contact photo", trailing = {
                    Text(if (hasCustom) "Change" else "Choose", color = Ink.GreenText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }, onClick = pickPhoto)
                if (hasCustom) {
                    RowDivider()
                    SettingRow(MsgsIcons.Close, "Remove my photo", onClick = { AvatarRepo.removeCustom(key) })
                }
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
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().panel().padding(vertical = 6.dp)) {
                    Text(
                        "Members",
                        
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                    )
                    conv.others.forEach { person ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(conv, 42.dp, person = person, ring = Ink.Raised)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (person.name.isNotBlank() && person.number.isNotBlank()) {
                                    Text(person.number, fontSize = 13.sp, color = Ink.Text2)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Photos you choose here stay in Msgs and replace the picture from your contacts.",
                fontSize = 13.sp,
                color = Ink.Text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 18.dp),
            )
        }
    }

    viewer?.let { ImageViewer(it) { viewer = null } }
}

/** The contact's photo as a full-width poster, with the photo button riding its bottom edge. */
@Composable
private fun Poster(
    conv: Conversation?,
    onBack: () -> Unit,
    onEdit: (() -> Unit)?,
    onPhoto: () -> Unit,
    photoLabel: String,
) {
    val collage = rememberCollage(conv)
    Box(Modifier.fillMaxWidth().height(392.dp)) {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            if (collage && conv != null) {
                Box(Modifier.fillMaxSize().background(Ink.Mono), contentAlignment = Alignment.Center) {
                    Avatar(conv, 160.dp, ring = Ink.Mono)
                }
            } else {
                AvatarFill(conv, Modifier.fillMaxSize(), initialsSize = 96.sp)
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(MsgsIcons.Back, "Back to chat", onBack, background = Ink.Bg)
                Spacer(Modifier.weight(1f))
                if (onEdit != null) {
                    Box(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Ink.Bg)
                            .clickable(role = Role.Button, onClick = onEdit)
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Edit", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink.Text)
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ink.Bg)
                .padding(4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.Green)
                .clickable(role = Role.Button, onClick = onPhoto)
                .semantics { contentDescription = photoLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MsgsIcons.Camera, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(23.dp))
        }
    }
}

/** Opens the phone's Contacts app to add or edit this number. */
private fun editContact(context: Context, number: String, name: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_INSERT_OR_EDIT)
                .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                .apply { if (name.isNotBlank() && name.none { it.isDigit() }) putExtra(ContactsContract.Intents.Insert.NAME, name) }
        )
    }.onFailure { Engine.toast("Couldn't open Contacts") }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(66.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (accent) Ink.Green else Ink.Raised)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
    }
}

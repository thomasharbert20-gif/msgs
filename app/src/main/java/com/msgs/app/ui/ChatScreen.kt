@file:OptIn(ExperimentalFoundationApi::class)

package com.msgs.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.msgs.app.data.Prefs
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Engine
import com.msgs.app.engine.Media
import com.msgs.app.engine.MediaRepo
import com.msgs.app.engine.Message
import com.msgs.app.engine.Reaction
import com.msgs.app.engine.VoicePlayer
import com.msgs.app.engine.VoiceRecorder
import com.msgs.app.engine.audioDurationMs
import com.msgs.app.engine.deliveryText
import com.msgs.app.engine.previewText
import com.msgs.app.engine.tombstoneText
import com.msgs.app.ui.theme.Archivo
import com.msgs.app.ui.theme.ArchivoWide
import com.msgs.app.ui.theme.Ink
import com.msgs.app.ui.theme.bubbleColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val reactionChoices = listOf("❤️", "👍", "👎", "😂", "😮", "😥")

private sealed class ChatItem(val key: String) {
    class Day(val ts: Long, id: String) : ChatItem("day:$id")
    class Bubble(val m: Message, val first: Boolean, val last: Boolean, val showName: Boolean, val showAvatar: Boolean) : ChatItem(m.id)
    class Receipt(val text: String, val m: Message) : ChatItem("receipt:${m.id}")
    class Notice(val text: String, id: String) : ChatItem("notice:$id")
}

private fun sameSender(a: Message, b: Message) =
    !a.tombstone && !b.tombstone && a.fromMe == b.fromMe && (a.fromMe || a.participantId == b.participantId)

private fun buildItems(messages: List<Message>, conv: Conversation?): List<ChatItem> {
    val group = conv?.isGroup == true
    val visible = messages.filter { !it.deleted && (!it.tombstone || it.tombstoneText() != null) }
    val lastMine = visible.lastOrNull { it.fromMe && !it.tombstone }
    val out = ArrayList<ChatItem>(visible.size + 8)
    visible.forEachIndexed { i, m ->
        val prev = visible.getOrNull(i - 1)
        val next = visible.getOrNull(i + 1)
        val newDay = prev == null || m.ts - prev.ts > 30 * 60_000L
        if (newDay) out.add(ChatItem.Day(m.ts, m.id))
        if (m.tombstone) {
            out.add(ChatItem.Notice(m.tombstoneText().orEmpty(), m.id))
            return@forEachIndexed
        }
        val joinsPrev = !newDay && prev != null && sameSender(prev, m) && m.ts - prev.ts < 3 * 60_000L
        val joinsNext = next != null && sameSender(m, next) && next.ts - m.ts < 3 * 60_000L
        out.add(
            ChatItem.Bubble(
                m = m,
                first = !joinsPrev,
                last = !joinsNext,
                showName = group && !m.fromMe && !joinsPrev,
                showAvatar = group && !m.fromMe && !joinsNext,
            )
        )
        if (m === lastMine) m.deliveryText()?.let { out.add(ChatItem.Receipt(it, m)) }
    }
    return out
}

@Composable
fun ChatScreen(convId: String, showBack: Boolean, onBack: () -> Unit, onContact: () -> Unit) {
    val context = LocalContext.current
    val convMap by Engine.conversations.collectAsState()
    val conv = convMap[convId]
    val messages = Engine.messages.collectAsState().value[convId].orEmpty()
    val typingMap = Engine.typing.collectAsState().value[convId].orEmpty()
    val loading = Engine.loading.collectAsState().value.contains(convId)
    val hasOlder = Engine.hasOlder.collectAsState().value[convId] == true
    val bubbles by Prefs.bubbles.collectAsState()
    val bubbleDefault by Prefs.bubbleDefault.collectAsState()
    val mine = bubbleColor(bubbles[convId] ?: bubbleDefault)
    val othersUnread = remember(convMap, convId) { convMap.values.count { it.id != convId && it.isVisible && it.unread } }
    var draft by rememberSaveable(convId) { mutableStateOf("") }
    var replyTo by remember(convId) { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<Media?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var recordingSince by remember(convId) { mutableStateOf<Long?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val recorder = remember(convId) { VoiceRecorder(context.applicationContext) }
    val listState = rememberLazyListState()

    DisposableEffect(convId) {
        Engine.openConversationId = convId
        Engine.loadMessages(convId)
        Engine.markConversationRead(convId)
        onDispose {
            if (Engine.openConversationId == convId) Engine.openConversationId = null
            recorder.cancel()
            VoicePlayer.stop()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Engine.sendMedia(context, convId, uri, replyTo?.id)
            replyTo = null
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) {
            Engine.sendMedia(context, convId, uri, replyTo?.id, mimeOverride = "image/jpeg")
            replyTo = null
        }
        cameraUri = null
    }

    fun beginRecording() {
        if (recorder.start()) recordingSince = System.currentTimeMillis()
        else Engine.toast("Couldn't start recording")
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording() else Engine.toast("Msgs needs the microphone to record voice messages")
    }

    val chatItems = remember(messages, conv) { buildItems(messages, conv) }
    val now = remember(tick, typingMap) { System.currentTimeMillis() }
    val typing = typingMap.values.any { it > now }
    LaunchedEffect(typingMap, tick) {
        val next = typingMap.values.filter { it > System.currentTimeMillis() }.minOrNull() ?: return@LaunchedEffect
        delay(next - System.currentTimeMillis() + 50)
        tick++
    }

    val newest = messages.lastOrNull()?.id
    LaunchedEffect(newest) {
        if (listState.firstVisibleItemIndex <= 3) listState.animateScrollToItem(0)
    }
    val nearTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 10 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearTop, hasOlder) {
        if (nearTop && hasOlder) Engine.loadOlder(convId)
    }

    Column(Modifier.fillMaxSize().background(Ink.Bg)) {
        ChatHeader(conv, typing, showBack, othersUnread, onBack, onContact)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            ) {
                if (typing) item(key = "typing") { TypingBubble(conv) }
                items(chatItems.asReversed(), key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Day -> DayLabel(item.ts)
                        is ChatItem.Notice -> NoticeLine(item.text)
                        is ChatItem.Receipt -> ReceiptLine(item)
                        is ChatItem.Bubble -> MessageBubble(
                            item = item,
                            conv = conv,
                            all = messages,
                            mine = mine,
                            onReply = { replyTo = it },
                            onOpenImage = { viewer = it },
                            onContact = onContact,
                        )
                    }
                }
                if (loading && messages.isEmpty()) {
                    item(key = "loading") {
                        Text(
                            "Loading messages…",
                            color = Ink.Text2,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
            }
        }
        if (conv?.readOnly == true) {
            Text(
                "You can't reply to this conversation",
                color = Ink.Text2,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
            )
        } else {
            Composer(
                conv = conv,
                draft = draft,
                onDraft = {
                    draft = it
                    if (it.isNotBlank() && conv?.rcs == true) Engine.userTyping(convId)
                },
                replyTo = replyTo,
                onCancelReply = { replyTo = null },
                onSend = {
                    Engine.sendText(convId, draft, replyTo?.id)
                    draft = ""
                    replyTo = null
                },
                onAttach = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onCamera = {
                    val dir = File(context.cacheDir, "media").apply { mkdirs() }
                    val uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".files",
                        File(dir, "camera_${System.currentTimeMillis()}.jpg"),
                    )
                    cameraUri = uri
                    runCatching { camera.launch(uri) }.onFailure { Engine.toast("No camera app found") }
                },
                mine = mine,
                recordingSince = recordingSince,
                onMic = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        beginRecording()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onSendRecording = {
                    recordingSince = null
                    val file = recorder.finish()
                    if (file != null) Engine.sendVoice(context, convId, file) else Engine.toast("That was too short to send")
                },
                onCancelRecording = {
                    recordingSince = null
                    recorder.cancel()
                },
            )
        }
    }

    viewer?.let { ImageViewer(it) { viewer = null } }
}

@Composable
private fun ChatHeader(
    conv: Conversation?,
    typing: Boolean,
    showBack: Boolean,
    othersUnread: Int,
    onBack: () -> Unit,
    onContact: () -> Unit,
) {
    val context = LocalContext.current
    val subtitle = when {
        conv == null -> ""
        typing -> "typing…"
        conv.isGroup -> conv.others.joinToString(", ") { it.first.ifBlank { it.displayName } }
        conv.rcs -> "RCS chat"
        else -> "Text message"
    }
    val number = conv?.other?.let { it.rawNumber.ifBlank { it.number } }
    Column(Modifier.fillMaxWidth().background(Ink.Bg).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (showBack) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Ink.Raised)
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = "Back to messages" }
                        .padding(start = 8.dp, end = if (othersUnread > 0) 15.dp else 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MsgsIcons.Back, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(22.dp))
                    if (othersUnread > 0) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            othersUnread.toString(),
                            fontFamily = ArchivoWide,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Ink.Text,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onContact)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .then(if (typing) Modifier.border(2.dp, Ink.GreenText, CircleShape) else Modifier)
                        .padding(4.dp),
                ) {
                    Avatar(conv, 56.dp)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conv?.title.orEmpty(),
                        fontFamily = ArchivoWide,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(MsgsIcons.Chevron, contentDescription = null, tint = Ink.Text3, modifier = Modifier.size(12.dp))
                }
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (typing) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (typing) Ink.GreenText else Ink.Text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (conv?.isGroup == false && !number.isNullOrBlank()) {
                CircleIcon(
                    MsgsIcons.Phone,
                    "Call ${conv.title}",
                    { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) } },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp),
                    iconSize = 20.dp,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
    }
}

@Composable
private fun DayLabel(ts: Long) {
    val context = LocalContext.current
    val (day, time) = remember(ts) { chatDayLabel(context, ts) }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink.Text2)) { append(day) }
            append("  $time")
        },
        fontSize = 12.sp,
        color = Ink.Text3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun NoticeLine(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Ink.Text3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 24.dp),
    )
}

@Composable
private fun ReceiptLine(item: ChatItem.Receipt) {
    val failed = item.m.failed
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            item.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (failed) Ink.Danger else Ink.Text3,
            modifier = Modifier
                .padding(top = 4.dp, end = 4.dp)
                .then(if (failed) Modifier.clickable { Engine.retry(item.m) } else Modifier),
        )
    }
}

private val senderColors = listOf(
    Color(0xFF2FAE6F), Color(0xFF8FB4FF), Color(0xFFF2A97E),
    Color(0xFFE6A2C6), Color(0xFFF2D27A), Color(0xFFB7A3F0),
)

private fun nameColor(seed: String): Color = senderColors[(seed.hashCode() and 0x7fffffff) % senderColors.size]

@Composable
private fun MessageBubble(
    item: ChatItem.Bubble,
    conv: Conversation?,
    all: List<Message>,
    mine: Color,
    onReply: (Message) -> Unit,
    onOpenImage: (Media) -> Unit,
    onContact: () -> Unit,
) {
    val m = item.m
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val big = 20.dp
    val small = 6.dp
    val shape = if (m.fromMe) {
        RoundedCornerShape(topStart = big, topEnd = if (item.first) big else small, bottomEnd = if (item.last) big else small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (item.first) big else small, topEnd = big, bottomEnd = big, bottomStart = if (item.last) big else small)
    }
    val sender = conv?.participants?.firstOrNull { it.id == m.participantId }
    val group = conv?.isGroup == true
    val fill = if (m.fromMe) mine else Ink.Raised
    val hasReactions = m.reactions.isNotEmpty()
    val local = m.localPreview

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (hasReactions) 16.dp else if (item.first) 6.dp else 2.dp),
        horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (group && !m.fromMe) {
            if (item.showAvatar) {
                Avatar(conv, 32.dp, person = sender, modifier = Modifier.clickable(onClick = onContact))
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Box {
            Column(
                Modifier
                    .widthIn(max = 290.dp)
                    .alpha(if (m.pending) 0.7f else 1f)
                    .clip(shape)
                    .background(fill)
                    .combinedClickable(
                        onClick = { if (m.failed) Engine.retry(m) },
                        onLongClick = { menu = true },
                    ),
            ) {
                if (item.showName) {
                    Text(
                        sender?.first?.ifBlank { null } ?: sender?.displayName ?: m.senderName.ifBlank { "Someone" },
                        fontFamily = ArchivoWide,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = nameColor(m.participantId),
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
                    )
                }
                if (m.replyTo.isNotBlank()) {
                    val quoted = all.firstOrNull { it.id == m.replyTo }
                    if (quoted != null) {
                        val who = if (quoted.fromMe) "You" else conv?.participants?.firstOrNull { it.id == quoted.participantId }?.displayName ?: quoted.senderName
                        Column(
                            Modifier
                                .padding(start = 6.dp, end = 6.dp, top = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(who, fontFamily = ArchivoWide, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
                            Text(quoted.previewText(), fontSize = 13.sp, color = Ink.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (local != null) {
                    if (local.startsWith("audio:")) {
                        val path = local.removePrefix("audio:")
                        VoiceNote(key = "local:$path", fromMe = true, mine = mine) { File(path).takeIf { it.exists() } }
                    } else {
                        LocalImage(local)
                    }
                }
                m.media.forEach { media ->
                    if (media.isAudio) {
                        VoiceNote(key = media.cacheKey, fromMe = m.fromMe, mine = mine) { MediaRepo.fullFile(media) }
                    } else {
                        MediaBlock(media, onOpenImage)
                    }
                }
                if (m.text.isNotBlank()) {
                    Text(
                        m.text,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = Ink.Text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
            if (hasReactions) {
                Row(
                    Modifier
                        .align(if (m.fromMe) Alignment.TopStart else Alignment.TopEnd)
                        .offset(x = if (m.fromMe) (-10).dp else 10.dp, y = (-16).dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    m.reactions.forEach { ReactionPill(it) }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    reactionChoices.forEach { e ->
                        Text(
                            e,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { Engine.react(m, e); menu = false }
                                .padding(6.dp),
                        )
                    }
                }
                HorizontalDivider(color = Ink.Line2)
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(MsgsIcons.Reply, contentDescription = null) },
                    onClick = { onReply(m); menu = false },
                )
                if (m.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(MsgsIcons.Copy, contentDescription = null) },
                        onClick = { clipboard.setText(AnnotatedString(m.text)); menu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = Ink.Danger) },
                    leadingIcon = { Icon(MsgsIcons.Trash, contentDescription = null, tint = Ink.Danger) },
                    onClick = { Engine.deleteMessage(m); menu = false },
                )
            }
        }
    }
}

@Composable
private fun ReactionPill(r: Reaction) {
    Row(
        Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Ink.Bg)
            .padding(3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (r.mine) Ink.Green else Ink.Raised2)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.emoji, fontSize = 14.sp)
        if (r.count > 1) {
            Spacer(Modifier.width(4.dp))
            Text(r.count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
        }
    }
}

/** A voice message: play button, bars that fill as it plays, and its length. */
@Composable
private fun VoiceNote(key: String, fromMe: Boolean, mine: Color, loadFile: suspend () -> File?) {
    val scope = rememberCoroutineScope()
    val player by VoicePlayer.state.collectAsState()
    var file by remember(key) { mutableStateOf<File?>(null) }
    val duration by produceState(0, key) {
        val f = loadFile()
        file = f
        value = if (f != null) withContext(Dispatchers.IO) { audioDurationMs(f) } else 0
    }
    val active = player.key == key
    val playing = active && player.playing
    val progress = if (active && player.durationMs > 0) player.positionMs / player.durationMs.toFloat() else 0f
    val shown = if (active) player.positionMs else duration
    Row(
        Modifier.width(236.dp).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Ink.Text)
                .clickable(role = Role.Button) {
                    scope.launch {
                        val f = file ?: loadFile()?.also { file = it }
                        if (f != null) VoicePlayer.toggle(key, f) else Engine.toast("Couldn't load that voice message")
                    }
                }
                .semantics { contentDescription = if (playing) "Pause voice message" else "Play voice message" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) MsgsIcons.Pause else MsgsIcons.Play,
                contentDescription = null,
                tint = if (fromMe) mine else Ink.Bg,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Waveform(key, progress, Ink.Text, Modifier.weight(1f).height(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (shown > 0) formatDuration(shown) else "0:00",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Ink.Text,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun MediaBlock(media: Media, onOpenImage: (Media) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (media.isImage || media.isVideo) {
        val bmp by produceState<ImageBitmap?>(null, media.cacheKey) { value = MediaRepo.thumbnail(media) }
        val ratio = if (media.width > 0 && media.height > 0) (media.width.toFloat() / media.height).coerceIn(0.6f, 1.8f) else 1.25f
        Box(
            Modifier
                .width(250.dp)
                .aspectRatio(ratio)
                .background(Ink.Raised2)
                .clickable {
                    if (media.isImage) onOpenImage(media) else scope.launch { MediaRepo.open(context, media) }
                },
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(it, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (media.isVideo) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Ink.Bg),
                    contentAlignment = Alignment.Center,
                ) { Icon(MsgsIcons.Play, contentDescription = "Play video", tint = Ink.Text, modifier = Modifier.size(24.dp)) }
            }
        }
    } else {
        Row(
            Modifier
                .widthIn(min = 200.dp)
                .clickable { scope.launch { MediaRepo.open(context, media) } }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MsgsIcons.File, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                media.name.ifBlank { "Attachment" },
                fontSize = 15.sp,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocalImage(uri: String) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeUri(context, Uri.parse(uri)) }
    }
    Box(Modifier.width(250.dp).aspectRatio(1.25f).background(Ink.Raised2)) {
        bmp?.let { Image(it, contentDescription = "Sending photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun decodeUri(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 700 && bounds.outHeight / (sample * 2) >= 700) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }?.asImageBitmap()
}.getOrNull()

@Composable
private fun TypingBubble(conv: Conversation?) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        if (conv?.isGroup == true) Spacer(Modifier.width(40.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.Raised)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { contentDescription = "Typing" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val lit = (phase.toInt() % 3) == i
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (lit) Ink.Text2 else Ink.Mono))
            }
        }
    }
}

@Composable
private fun Composer(
    conv: Conversation?,
    draft: String,
    onDraft: (String) -> Unit,
    replyTo: Message?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onCamera: () -> Unit,
    mine: Color,
    recordingSince: Long?,
    onMic: () -> Unit,
    onSendRecording: () -> Unit,
    onCancelRecording: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.Bg)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
    ) {
        if (replyTo != null && recordingSince == null) {
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ink.Raised)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MsgsIcons.Reply, contentDescription = null, tint = Ink.GreenText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    replyTo.previewText(),
                    fontSize = 14.sp,
                    color = Ink.Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CircleIcon(MsgsIcons.Close, "Cancel reply", onCancelReply, size = 36.dp, iconSize = 16.dp, background = Ink.Raised2)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (recordingSince != null) {
                CircleIcon(MsgsIcons.Trash, "Discard recording", onCancelRecording, size = 48.dp, iconSize = 20.dp, tint = Ink.Danger)
                Spacer(Modifier.width(8.dp))
                RecordingField(recordingSince, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CircleIcon(MsgsIcons.Send, "Send voice message", onSendRecording, size = 48.dp, iconSize = 22.dp, background = mine)
            } else {
                CircleIcon(MsgsIcons.Plus, "Add a photo or video", onAttach, size = 48.dp, iconSize = 22.dp)
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Ink.Raised)
                        .padding(start = 18.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraft,
                        textStyle = TextStyle(fontFamily = Archivo, fontSize = 16.sp, lineHeight = 22.sp, color = Ink.Text),
                        cursorBrush = SolidColor(Ink.GreenText),
                        maxLines = 6,
                        modifier = Modifier.weight(1f).padding(vertical = 13.dp),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) {
                                    Text(
                                        if (conv?.rcs == true) "RCS message" else "Text message",
                                        fontSize = 16.sp,
                                        color = Ink.Text3,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (draft.isEmpty()) {
                        CircleIcon(MsgsIcons.Camera, "Take a photo", onCamera, size = 44.dp, iconSize = 21.dp, background = Color.Transparent, tint = Ink.Text2)
                    } else {
                        Spacer(Modifier.width(14.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (draft.isBlank()) {
                    CircleIcon(MsgsIcons.Mic, "Record a voice message", onMic, size = 48.dp, iconSize = 21.dp, background = mine)
                } else {
                    CircleIcon(MsgsIcons.Send, "Send", onSend, size = 48.dp, iconSize = 22.dp, background = mine)
                }
            }
        }
    }
}

@Composable
private fun RecordingField(since: Long, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val blink = ((now - since) / 600) % 2 == 0L
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Ink.Raised)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (blink) Ink.Danger else Ink.Danger.copy(alpha = 0.35f)))
        Spacer(Modifier.width(10.dp))
        Text("Recording", fontSize = 16.sp, color = Ink.Text, modifier = Modifier.weight(1f))
        Text(
            formatDuration((now - since).toInt()),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Ink.Text,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
fun ImageViewer(media: Media, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val thumb by produceState<ImageBitmap?>(null, media.cacheKey) { value = MediaRepo.thumbnail(media) }
        val full by produceState<ImageBitmap?>(null, media.mediaId) { value = MediaRepo.fullImage(media) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(Modifier.fillMaxSize().background(Ink.Bg)) {
            (full ?: thumb)?.let {
                Image(
                    it,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            }
            CircleIcon(
                MsgsIcons.Close,
                "Close",
                onClose,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
            )
        }
    }
}

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Engine
import com.msgs.app.engine.Media
import com.msgs.app.engine.MediaRepo
import com.msgs.app.engine.Message
import com.msgs.app.engine.Participant
import com.msgs.app.engine.Reaction
import com.msgs.app.engine.VoicePlayer
import com.msgs.app.engine.VoiceRecorder
import com.msgs.app.engine.audioDurationMs
import com.msgs.app.engine.deliveryText
import com.msgs.app.engine.previewText
import com.msgs.app.engine.tombstoneText
import com.msgs.app.ui.theme.Ink
import com.msgs.app.ui.theme.Sans
import com.msgs.app.ui.theme.SansCondensed
import com.msgs.app.ui.theme.Serif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val reactionChoices = listOf("❤️", "👍", "👎", "😂", "😮", "😥")

/** How wide the transcript margin is: names, times and receipts live here, words to the right. */
private val Margin = 76.dp

/**
 * The transcript is a list of lines. A line is one message; [first] lines start a new run and
 * carry the sender and time in the margin. A short text sent right after a photo becomes its
 * [caption]. [receipt] is the message whose delivery state shows in this line's margin.
 */
private sealed class ChatItem(val key: String) {
    class Day(val ts: Long, id: String) : ChatItem("day:$id")
    class Line(val m: Message, val first: Boolean, val caption: Message?, val receipt: Message?) : ChatItem(m.id)
    class Notice(val text: String, id: String) : ChatItem("notice:$id")
}

private const val RUN_GAP = 5 * 60_000L
private const val CAPTION_GAP = 2 * 60_000L
private const val CAPTION_MAX = 90

private fun sameSender(a: Message, b: Message) =
    !a.tombstone && !b.tombstone && a.fromMe == b.fromMe && (a.fromMe || a.participantId == b.participantId)

/** A photo or video, or one that's still sending. */
private fun isPlate(m: Message) =
    m.media.any { it.isImage || it.isVideo } || m.localPreview?.let { !it.startsWith("audio:") } == true

private fun canCaption(plate: Message, next: Message?): Boolean =
    next != null && plate.text.isBlank() && sameSender(plate, next) && next.ts - plate.ts in 0..CAPTION_GAP &&
        next.media.isEmpty() && next.localPreview == null && next.replyTo.isBlank() &&
        next.text.isNotBlank() && next.text.length <= CAPTION_MAX && '\n' !in next.text

private fun buildItems(messages: List<Message>): List<ChatItem> {
    val visible = messages.filter { !it.deleted && (!it.tombstone || it.tombstoneText() != null) }
    val lastMine = visible.lastOrNull { it.fromMe && !it.tombstone }
    val out = ArrayList<ChatItem>(visible.size + 8)
    var prev: Message? = null
    var i = 0
    while (i < visible.size) {
        val m = visible[i]
        val p = prev
        if (p == null || !sameDay(p.ts, m.ts)) out.add(ChatItem.Day(m.ts, m.id))
        if (m.tombstone) {
            out.add(ChatItem.Notice(m.tombstoneText().orEmpty(), m.id))
            prev = m
            i++
            continue
        }
        val next = visible.getOrNull(i + 1)
        val caption = if (isPlate(m) && canCaption(m, next)) next else null
        val joins = p != null && sameDay(p.ts, m.ts) && sameSender(p, m) && m.ts - p.ts < RUN_GAP
        val receipt = when {
            m === lastMine -> m
            caption != null && caption === lastMine -> caption
            else -> null
        }
        out.add(ChatItem.Line(m, first = !joins, caption = caption, receipt = receipt))
        prev = caption ?: m
        i += if (caption != null) 2 else 1
    }
    return out
}

/** What the mic button can do: a tap starts a recording you send later, a hold records until you let go. */
private class MicActions(
    val onTap: () -> Unit,
    val onHoldStart: () -> Boolean,
    val onHoldMove: (armed: Boolean) -> Unit,
    val onHoldEnd: (cancelled: Boolean) -> Unit,
)

@Composable
fun ChatScreen(convId: String, showBack: Boolean, onBack: () -> Unit, onContact: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val convMap by Engine.conversations.collectAsState()
    val conv = convMap[convId]
    val messages = Engine.messages.collectAsState().value[convId].orEmpty()
    val typingMap = Engine.typing.collectAsState().value[convId].orEmpty()
    val loading = Engine.loading.collectAsState().value.contains(convId)
    val hasOlder = Engine.hasOlder.collectAsState().value[convId] == true
    val othersUnread = remember(convMap, convId) { convMap.values.count { it.id != convId && it.isVisible && it.unread } }
    var draft by rememberSaveable(convId) { mutableStateOf("") }
    var replyTo by remember(convId) { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<Media?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var recordingSince by remember(convId) { mutableStateOf<Long?>(null) }
    var holding by remember(convId) { mutableStateOf(false) }
    var cancelArmed by remember(convId) { mutableStateOf(false) }
    var recordAfterGrant by remember { mutableStateOf(false) }
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

    fun hasMic() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun beginRecording(): Boolean {
        val ok = recorder.start()
        if (ok) recordingSince = System.currentTimeMillis() else Engine.toast("Couldn't start recording")
        return ok
    }

    fun endRecording(send: Boolean) {
        recordingSince = null
        holding = false
        cancelArmed = false
        if (!send) {
            recorder.cancel()
            return
        }
        val file = recorder.finish()
        if (file != null) Engine.sendVoice(context, convId, file) else Engine.toast("That was too short to send")
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Engine.toast("Msgs needs the microphone to record voice messages")
        else if (recordAfterGrant) beginRecording()
        recordAfterGrant = false
    }

    val mic = MicActions(
        onTap = {
            if (hasMic()) {
                beginRecording()
            } else {
                recordAfterGrant = true
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onHoldStart = {
            if (!hasMic()) {
                recordAfterGrant = false
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                false
            } else {
                holding = true
                val ok = beginRecording()
                if (ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress) else holding = false
                ok
            }
        },
        onHoldMove = { armed ->
            if (armed != cancelArmed) {
                cancelArmed = armed
                if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        onHoldEnd = { cancelled -> endRecording(send = !cancelled) },
    )

    val chatItems = remember(messages) { buildItems(messages) }
    val now = remember(tick, typingMap) { System.currentTimeMillis() }
    val typists = typingMap.filterValues { it > now }.keys
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

    // With the keyboard up there's no room for the big name, so the header folds to one line.
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val keyboardUp by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }

    Column(Modifier.fillMaxSize().background(Ink.Bg)) {
        ChatHeader(conv, typists, keyboardUp, showBack, othersUnread, onBack, onContact)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
            ) {
                if (typists.isNotEmpty()) item(key = "typing") { TypingRow(conv, typists) }
                items(chatItems.asReversed(), key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Day -> DayLine(item.ts)
                        is ChatItem.Notice -> NoticeLine(item.text)
                        is ChatItem.Line -> TranscriptLine(
                            item = item,
                            conv = conv,
                            all = messages,
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
                            fontFamily = Serif,
                            fontSize = 17.sp,
                            color = Ink.Text2,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
        if (conv?.readOnly == true) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Hairline()
                Text(
                    "You can't reply to this conversation.",
                    fontFamily = Serif,
                    fontSize = 16.sp,
                    color = Ink.Text2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }
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
                recordingSince = recordingSince,
                holding = holding,
                cancelArmed = cancelArmed,
                mic = mic,
                onSendRecording = { endRecording(send = true) },
                onDiscardRecording = { endRecording(send = false) },
            )
        }
    }

    viewer?.let { ImageViewer(it) { viewer = null } }
}

/** Big names get smaller as they get longer, so "Mike" can fill the width and a group still fits. */
private fun nameSize(length: Int): Float = when {
    length <= 8 -> 64f
    length <= 12 -> 52f
    length <= 18 -> 42f
    else -> 34f
}

private fun memberLine(c: Conversation): String {
    val names = c.others.map { firstNameOf(it, it.displayName) }
    return when {
        names.size <= 4 -> names.joinToString(", ")
        else -> names.take(3).joinToString(", ") + " and ${names.size - 3} more"
    }
}

private fun typistName(conv: Conversation?, typists: Set<String>): String? {
    if (conv == null) return null
    val p = typists.firstNotNullOfOrNull { n -> conv.others.firstOrNull { it.number == n || it.rawNumber == n } }
    return p?.let { firstNameOf(it, it.displayName) }
}

@Composable
private fun ChatHeader(
    conv: Conversation?,
    typists: Set<String>,
    compact: Boolean,
    showBack: Boolean,
    othersUnread: Int,
    onBack: () -> Unit,
    onContact: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val typing = typists.isNotEmpty()
    val name = conv?.let { shortName(it) }.orEmpty()
    val status = when {
        conv == null -> ""
        typing && conv.isGroup -> "${typistName(conv, typists) ?: "Someone"} is typing"
        typing -> "Typing now"
        conv.isGroup -> memberLine(conv)
        conv.title != name -> if (conv.rcs) "${conv.title}, RCS chat" else "${conv.title}, text message"
        conv.rcs -> "RCS chat"
        else -> "Text message"
    }
    val number = conv?.other?.let { it.rawNumber.ifBlank { it.number } }
    val collage = rememberCollage(conv)
    Column(Modifier.fillMaxWidth().background(Ink.Bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                BackLink(if (compact) null else "Messages", onBack, news = othersUnread)
            } else {
                Spacer(Modifier.width(14.dp))
            }
            if (compact) {
                Text(
                    name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClickLabel = "Open details", onClick = onContact)
                        .padding(vertical = 12.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (conv?.isGroup == false && !number.isNullOrBlank()) {
                TextAction("Call", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
                })
            }
            Box {
                GlyphButton(MsgsIcons.More, "More options", { menu = true }, iconSize = 20.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(MsgsIcons.Info, contentDescription = null) },
                        onClick = { menu = false; onContact() },
                    )
                    if (conv != null) {
                        DropdownMenuItem(
                            text = { Text(if (conv.isArchived) "Unarchive" else "Archive") },
                            leadingIcon = { Icon(MsgsIcons.Archive, contentDescription = null) },
                            onClick = {
                                menu = false
                                val archiving = !conv.isArchived
                                Engine.setArchived(conv.id, archiving)
                                if (archiving && showBack) onBack()
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !compact,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Open details",
                                onClick = onContact,
                            ),
                    ) {
                        val size = nameSize(name.length)
                        Text(
                            name,
                            fontFamily = SansCondensed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = size.sp,
                            lineHeight = (size * 0.9f).sp,
                            letterSpacing = (size * -0.025f).sp,
                            color = Ink.Text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            status,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (typing) Ink.GreenText else Ink.Text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    val portrait = Modifier
                        .clip(PortraitShape)
                        .clickable(role = Role.Button, onClickLabel = "Open details", onClick = onContact)
                    if (collage) {
                        Avatar(conv, 72.dp, modifier = portrait)
                    } else {
                        AvatarFill(conv, portrait.width(72.dp).height(96.dp), initialsSize = 36.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Hairline(Modifier.padding(horizontal = 20.dp))
    }
}

@Composable
private fun DayLine(ts: Long) {
    Text(
        dayName(ts),
        fontFamily = Serif,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = Ink.Text2,
        modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
    )
}

@Composable
private fun NoticeLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Spacer(Modifier.width(Margin))
        Text(text, fontFamily = Serif, fontSize = 15.sp, lineHeight = 19.sp, color = Ink.Text3)
    }
}

/** One message in the transcript: the margin on the left, the words on the right. */
@Composable
private fun TranscriptLine(
    item: ChatItem.Line,
    conv: Conversation?,
    all: List<Message>,
    onReply: (Message) -> Unit,
    onOpenImage: (Media) -> Unit,
    onContact: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val m = item.m
    val sender = remember(conv, m.participantId) { conv?.participants?.firstOrNull { it.id == m.participantId } }
    val group = conv?.isGroup == true
    var menuFor by remember { mutableStateOf<Message?>(null) }

    Box(Modifier.fillMaxWidth().padding(top = if (item.first) 22.dp else 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(Margin).padding(top = 3.dp, end = 8.dp)) {
                if (item.first) {
                    Text(
                        if (m.fromMe) "You" else firstNameOf(sender, m.senderName.ifBlank { conv?.title ?: "Them" }),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (m.fromMe) Ink.GreenText else Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (group && !m.fromMe) Modifier.clickable(onClick = onContact) else Modifier,
                    )
                    Text(
                        timeOnly(context, m.ts),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink.Text3,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                }
                item.receipt?.let { Receipt(it) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MessageBody(m, conv, all, onOpenImage, onLongPress = { menuFor = m })
                item.caption?.let { cap -> Caption(cap, onLongPress = { menuFor = cap }) }
                val reactions = m.reactions + item.caption?.reactions.orEmpty()
                if (reactions.isNotEmpty()) {
                    Text(
                        reactionNote(reactions, conv),
                        fontFamily = Serif,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        color = Ink.Text2,
                    )
                }
            }
        }
        val target = menuFor
        DropdownMenu(expanded = target != null, onDismissRequest = { menuFor = null }) {
            if (target != null) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    reactionChoices.forEach { e ->
                        Text(
                            e,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { Engine.react(target, e); menuFor = null }
                                .padding(6.dp),
                        )
                    }
                }
                HorizontalDivider(color = Ink.Line2)
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(MsgsIcons.Reply, contentDescription = null) },
                    onClick = { onReply(target); menuFor = null },
                )
                if (target.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(MsgsIcons.Copy, contentDescription = null) },
                        onClick = { clipboard.setText(AnnotatedString(target.text)); menuFor = null },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = Ink.Danger) },
                    leadingIcon = { Icon(MsgsIcons.Trash, contentDescription = null, tint = Ink.Danger) },
                    onClick = { Engine.deleteMessage(target); menuFor = null },
                )
            }
        }
    }
}

/** A message made only of emoji is set large, like a stamp. */
private fun emojiOnly(text: String): Boolean =
    text.isNotBlank() && text.length <= 12 && text.codePoints().allMatch {
        it >= 0x2190 || it == 0x200D || it in 0xFE00..0xFE0F || Character.isWhitespace(it)
    } && text.codePoints().anyMatch { it >= 0x2190 }

@Composable
private fun MessageBody(
    m: Message,
    conv: Conversation?,
    all: List<Message>,
    onOpenImage: (Media) -> Unit,
    onLongPress: () -> Unit,
) {
    val ink = if (m.fromMe) Ink.GreenText else Ink.Text
    val local = m.localPreview
    // A photo sent with a few words of its own gets them as a caption.
    val ownCaption = isPlate(m) && m.text.isNotBlank() && m.text.length <= CAPTION_MAX
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (m.pending) 0.6f else 1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (m.failed) Engine.retry(m) },
                onLongClick = onLongPress,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (m.replyTo.isNotBlank()) {
            all.firstOrNull { it.id == m.replyTo }?.let { QuotedLine(it, conv) }
        }
        if (local != null) {
            if (local.startsWith("audio:")) {
                val path = local.removePrefix("audio:")
                VoiceNote(key = "local:$path", color = ink) { File(path).takeIf { it.exists() } }
            } else {
                LocalPlate(local)
            }
        }
        m.media.forEach { media ->
            if (media.isAudio) {
                VoiceNote(key = media.cacheKey, color = ink) { MediaRepo.fullFile(media) }
            } else {
                MediaPlate(media, onOpenImage, onLongPress)
            }
        }
        if (m.text.isNotBlank()) {
            when {
                ownCaption -> Text(m.text, fontFamily = Serif, fontSize = 16.sp, lineHeight = 20.sp, color = Ink.Text2)
                emojiOnly(m.text) -> Text(m.text, fontSize = 40.sp, lineHeight = 46.sp)
                else -> Text(m.text, fontSize = 17.sp, lineHeight = 25.sp, color = ink)
            }
        }
    }
}

@Composable
private fun Caption(m: Message, onLongPress: () -> Unit) {
    Text(
        m.text,
        fontFamily = Serif,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = Ink.Text2,
        modifier = Modifier
            .alpha(if (m.pending) 0.6f else 1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (m.failed) Engine.retry(m) },
                onLongClick = onLongPress,
            ),
    )
}

/** What a reply refers to, set as a short excerpt with a rule beside it. */
@Composable
private fun QuotedLine(q: Message, conv: Conversation?) {
    val who = if (q.fromMe) "You" else firstNameOf(conv?.participants?.firstOrNull { it.id == q.participantId }, q.senderName.ifBlank { "Them" })
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(Ink.Line2))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(who, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text3)
            Text(
                q.previewText(),
                fontFamily = Serif,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                color = Ink.Text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How your last message went, under the time in the margin. */
@Composable
private fun Receipt(m: Message) {
    if (m.failed) {
        Column(
            Modifier
                .padding(top = 2.dp)
                .clickable(role = Role.Button, onClickLabel = "Retry") { Engine.retry(m) },
        ) {
            Text("Not sent", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink.Danger)
            Text("Tap to retry", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Ink.Text3)
        }
        return
    }
    val text = m.deliveryText() ?: return
    Text(text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Ink.Text3, maxLines = 1)
}

/** "Mike reacted 🔥" in a one-to-one chat; emoji and counts in a group, yours in sage. */
private fun reactionNote(rs: List<Reaction>, conv: Conversation?): AnnotatedString = buildAnnotatedString {
    if (conv?.isGroup == true) {
        rs.forEachIndexed { i, r ->
            if (i > 0) append("   ")
            val text = if (r.count > 1) "${r.emoji} ${r.count}" else r.emoji
            if (r.mine) withStyle(SpanStyle(color = Ink.GreenText)) { append(text) } else append(text)
        }
        return@buildAnnotatedString
    }
    val them = conv?.let { shortName(it) } ?: "They"
    val parts = rs.map { r ->
        when {
            r.mine && r.count > 1 -> "you and $them reacted ${r.emoji}"
            r.mine -> "you reacted ${r.emoji}"
            else -> "$them reacted ${r.emoji}"
        }
    }
    append(parts.joinToString(", ").replaceFirstChar { it.uppercase() })
}

/** A photo or video set as a plate across the text column. */
@Composable
private fun MediaPlate(media: Media, onOpenImage: (Media) -> Unit, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (media.isImage || media.isVideo) {
        val bmp by produceState<ImageBitmap?>(null, media.cacheKey) { value = MediaRepo.thumbnail(media) }
        val ratio = if (media.width > 0 && media.height > 0) (media.width.toFloat() / media.height).coerceIn(0.8f, 1.9f) else 1.5f
        Box(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(PortraitShape)
                .background(Ink.Raised2)
                .combinedClickable(
                    onClickLabel = if (media.isImage) "View photo" else "Play video",
                    onClick = { if (media.isImage) onOpenImage(media) else scope.launch { MediaRepo.open(context, media) } },
                    onLongClick = onLongPress,
                )
                .semantics { contentDescription = if (media.isVideo) "Video" else "Photo" },
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (media.isVideo) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Bg.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(MsgsIcons.Play, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(20.dp)) }
            }
        }
    } else {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = { scope.launch { MediaRepo.open(context, media) } },
                    onLongClick = onLongPress,
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MsgsIcons.File, contentDescription = null, tint = Ink.Text2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                media.name.ifBlank { "Attachment" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawBehind {
                    val y = size.height + 3.dp.toPx()
                    drawLine(Ink.Green, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                },
            )
        }
    }
}

@Composable
private fun LocalPlate(uri: String) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeUri(context, Uri.parse(uri)) }
    }
    Box(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(PortraitShape)
            .background(Ink.Raised2),
    ) {
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

/** A voice message: an outlined key, bars that fill as it plays, and its length. Tap anywhere on it. */
@Composable
private fun VoiceNote(key: String, color: Color, loadFile: suspend () -> File?) {
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
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = if (playing) "Pause" else "Play") {
                scope.launch {
                    val f = file ?: loadFile()?.also { file = it }
                    if (f != null) VoicePlayer.toggle(key, f) else Engine.toast("Couldn't load that voice message")
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = (if (playing) "Pause voice message, " else "Play voice message, ") +
                    formatDuration(if (shown > 0) shown else 0)
            }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).border(1.5.dp, color, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) MsgsIcons.Pause else MsgsIcons.Play, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(12.dp))
        Waveform(key, progress, color, Modifier.width(150.dp).height(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            if (shown > 0) formatDuration(shown) else "0:00",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun TypingRow(conv: Conversation?, typists: Set<String>) {
    val name = when {
        conv == null -> ""
        conv.isGroup -> typistName(conv, typists) ?: "Someone"
        else -> shortName(conv)
    }
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val lead = phase.toInt() % 3
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$name is typing" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(Margin).padding(end = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                val alpha = when ((i - lead + 3) % 3) {
                    0 -> 1f
                    1 -> 0.55f
                    else -> 0.28f
                }
                Box(Modifier.size(6.dp).clip(CircleShape).background(Ink.Text2.copy(alpha = alpha)))
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
    recordingSince: Long?,
    holding: Boolean,
    cancelArmed: Boolean,
    mic: MicActions,
    onSendRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
) {
    val to = conv?.let { shortName(it) }.orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.Bg)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Hairline()
        if (replyTo != null && recordingSince == null) {
            val who = when {
                replyTo.fromMe -> "your message"
                conv?.isGroup == true -> firstNameOf(conv.participants.firstOrNull { it.id == replyTo.participantId }, replyTo.senderName.ifBlank { "them" })
                else -> to
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(2.dp).height(36.dp).background(Ink.Green))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Replying to $who", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text2)
                    Text(
                        replyTo.previewText(),
                        fontFamily = Serif,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        color = Ink.Text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlyphButton(MsgsIcons.Close, "Cancel reply", onCancelReply, iconSize = 18.dp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (recordingSince != null) {
                RecordingLine(recordingSince, holding, cancelArmed, onDiscardRecording, Modifier.weight(1f))
            } else {
                GlyphButton(MsgsIcons.Plus, "Add a photo or video", onAttach)
                if (draft.isEmpty()) GlyphButton(MsgsIcons.Camera, "Take a photo", onCamera)
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 24.sp, color = Ink.GreenText),
                    cursorBrush = SolidColor(Ink.GreenText),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 6,
                    modifier = Modifier.weight(1f).padding(start = 6.dp, top = 12.dp, bottom = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    if (to.isNotBlank()) "Write to $to" else "Write a message",
                                    fontSize = 17.sp,
                                    lineHeight = 24.sp,
                                    color = Ink.Text3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            when {
                recordingSince != null && !holding -> InkLink("Send", onSendRecording, fontSize = 15.sp)
                draft.isBlank() -> MicButton(mic, holding)
                else -> InkLink("Send", onSend, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Hold to talk, let go to send, slide left to cancel. A plain tap starts a recording that waits
 * for Send, for anyone who'd rather not hold.
 */
@Composable
private fun MicButton(actions: MicActions, holding: Boolean) {
    val current by rememberUpdatedState(actions)
    val cancelPx = with(LocalDensity.current) { 96.dp.toPx() }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (holding) Ink.Green else Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var settled = false
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation().also { settled = true }
                    }
                    if (settled) {
                        if (up != null) {
                            up.consume()
                            current.onTap()
                        }
                        return@awaitEachGesture
                    }
                    if (!current.onHoldStart()) return@awaitEachGesture
                    var cancelled = false
                    var ended = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp() || !change.pressed) {
                                change.consume()
                                break
                            }
                            cancelled = change.position.x - down.position.x < -cancelPx
                            current.onHoldMove(cancelled)
                            change.consume()
                        }
                        ended = true
                        current.onHoldEnd(cancelled)
                    } finally {
                        // Interrupted mid-hold: throw the recording away rather than send half a thought.
                        if (!ended) current.onHoldEnd(true)
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "Record a voice message. Hold to talk, or tap to start and stop."
                onClick(label = "Start recording") { current.onTap(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MsgsIcons.Mic, contentDescription = null, tint = if (holding) Ink.Text else Ink.Text2, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RecordingLine(since: Long, holding: Boolean, armed: Boolean, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val blink = ((now - since) / 600) % 2 == 0L
    Row(modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (holding) {
            Spacer(Modifier.width(14.dp))
        } else {
            GlyphButton(MsgsIcons.Trash, "Discard recording", onDiscard, tint = Ink.Danger)
        }
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (blink) Ink.Danger else Ink.Danger.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            formatDuration((now - since).toInt()),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink.Text,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            when {
                holding && armed -> "Release to cancel"
                holding -> "Slide left to cancel"
                else -> "Recording"
            },
            fontSize = 15.sp,
            color = if (armed) Ink.Danger else Ink.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
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

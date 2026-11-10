@file:OptIn(ExperimentalFoundationApi::class)

package com.msgs.app.ui

import android.content.Context
import android.content.Intent
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
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Engine
import com.msgs.app.engine.Media
import com.msgs.app.engine.MediaRepo
import com.msgs.app.engine.Message
import com.msgs.app.engine.Reaction
import com.msgs.app.engine.deliveryText
import com.msgs.app.engine.previewText
import com.msgs.app.engine.tombstoneText
import com.msgs.app.ui.theme.Danger
import com.msgs.app.ui.theme.Geist
import com.msgs.app.ui.theme.LocalPalette
import com.msgs.app.ui.theme.Palette
import com.msgs.app.ui.theme.TextPrimary
import com.msgs.app.ui.theme.TextSecondary
import com.msgs.app.ui.theme.TextTertiary
import com.msgs.app.ui.theme.barGlass
import com.msgs.app.ui.theme.nameStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val conv = Engine.conversations.collectAsState().value[convId]
    val messages = Engine.messages.collectAsState().value[convId].orEmpty()
    val typingMap = Engine.typing.collectAsState().value[convId].orEmpty()
    val loading = Engine.loading.collectAsState().value.contains(convId)
    val hasOlder = Engine.hasOlder.collectAsState().value[convId] == true
    var draft by rememberSaveable(convId) { mutableStateOf("") }
    var replyTo by remember(convId) { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<Media?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    DisposableEffect(convId) {
        Engine.openConversationId = convId
        Engine.loadMessages(convId)
        Engine.markConversationRead(convId)
        onDispose { if (Engine.openConversationId == convId) Engine.openConversationId = null }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Engine.sendMedia(context, convId, uri, replyTo?.id)
            replyTo = null
        }
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

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        ChatHeader(conv, typing, showBack, onBack, onContact)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
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
                            color = TextSecondary,
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
                color = TextSecondary,
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
            )
        }
    }

    viewer?.let { ImageViewer(it) { viewer = null } }
}

@Composable
private fun ChatHeader(conv: Conversation?, typing: Boolean, showBack: Boolean, onBack: () -> Unit, onContact: () -> Unit) {
    val context = LocalContext.current
    val subtitle = when {
        conv == null -> ""
        typing -> "typing…"
        conv.isGroup -> conv.others.joinToString(", ") { it.first.ifBlank { it.displayName } } + " and you"
        conv.rcs -> "RCS chat"
        else -> "Text message"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (showBack) 14.dp else 20.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            GlassIconButton(MsgsIcons.Back, "Back to inbox", onBack)
            Spacer(Modifier.width(10.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onContact)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(conv, 44.dp)
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(conv?.title.orEmpty(), style = nameStyle(19), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 13.sp, color = if (typing) LocalPalette.current.accent else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val number = conv?.other?.let { it.rawNumber.ifBlank { it.number } }
        if (conv?.isGroup == false && !number.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            GlassIconButton(MsgsIcons.Phone, "Call", {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            })
        }
    }
}

@Composable
private fun DayLabel(ts: Long) {
    val context = LocalContext.current
    val (day, time) = remember(ts) { chatDayLabel(context, ts) }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextSecondary)) { append(day) }
            append("  $time")
        },
        fontSize = 12.5.sp,
        color = TextTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun NoticeLine(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = TextTertiary,
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
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (failed) Danger else TextTertiary,
            modifier = Modifier
                .padding(top = 3.dp, end = 6.dp)
                .then(if (failed) Modifier.clickable { Engine.retry(item.m) } else Modifier),
        )
    }
}

private fun nameColor(seed: String, p: Palette): Color {
    val colors = listOf(p.accent, Color(0xFFF2B38A), Color(0xFF9CC8F2), Color(0xFFE6A2C6), Color(0xFFF2D27A), Color(0xFFB7A3F0))
    return colors[(seed.hashCode() and 0x7fffffff) % colors.size]
}

@Composable
private fun MessageBubble(
    item: ChatItem.Bubble,
    conv: Conversation?,
    all: List<Message>,
    onReply: (Message) -> Unit,
    onOpenImage: (Media) -> Unit,
    onContact: () -> Unit,
) {
    val m = item.m
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val big = 22.dp
    val small = 7.dp
    val shape = if (m.fromMe) {
        RoundedCornerShape(topStart = big, topEnd = if (item.first) big else small, bottomEnd = if (item.last) big else small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (item.first) big else small, topEnd = big, bottomEnd = big, bottomStart = if (item.last) big else small)
    }
    val sender = conv?.participants?.firstOrNull { it.id == m.participantId }
    val group = conv?.isGroup == true

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (item.first) 6.dp else 2.dp, bottom = if (m.reactions.isNotEmpty()) 12.dp else 0.dp),
        horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (group && !m.fromMe) {
            if (item.showAvatar) {
                Avatar(conv, 34.dp, person = sender, modifier = Modifier.clip(CircleShape).clickable(onClick = onContact))
            } else {
                Spacer(Modifier.width(34.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Box {
            androidx.compose.foundation.layout.Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .alpha(if (m.pending) 0.75f else 1f)
                    .clip(shape)
                    .background(if (m.fromMe) p.ink else Color.White.copy(alpha = 0.11f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .combinedClickable(
                        onClick = { if (m.failed) Engine.retry(m) },
                        onLongClick = { menu = true },
                    ),
            ) {
                if (item.showName) {
                    Text(
                        sender?.first?.ifBlank { null } ?: sender?.displayName ?: m.senderName.ifBlank { "Someone" },
                        style = nameStyle(14).copy(color = nameColor(m.participantId, p)),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 9.dp),
                    )
                }
                if (m.replyTo.isNotBlank()) {
                    val quoted = all.firstOrNull { it.id == m.replyTo }
                    if (quoted != null) {
                        val who = if (quoted.fromMe) "You" else conv?.participants?.firstOrNull { it.id == quoted.participantId }?.displayName ?: quoted.senderName
                        androidx.compose.foundation.layout.Column(
                            Modifier
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(who, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            Text(quoted.previewText(), fontSize = 13.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                m.localPreview?.let { LocalImage(it) }
                m.media.forEach { media -> MediaBlock(media, onOpenImage) }
                if (m.text.isNotBlank()) {
                    Text(
                        m.text,
                        fontSize = 16.5.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            if (m.reactions.isNotEmpty()) {
                Row(
                    Modifier
                        .align(if (m.fromMe) Alignment.BottomStart else Alignment.BottomEnd)
                        .offset(y = 14.dp)
                        .padding(horizontal = 10.dp),
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
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
                    text = { Text("Delete", color = Danger) },
                    leadingIcon = { Icon(MsgsIcons.Trash, contentDescription = null, tint = Danger) },
                    onClick = { Engine.deleteMessage(m); menu = false },
                )
            }
        }
    }
}

@Composable
private fun ReactionPill(r: Reaction) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Color(0xE6181C16))
            .border(1.dp, if (r.mine) p.accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.emoji, fontSize = 13.sp)
        if (r.count > 1) {
            Spacer(Modifier.width(4.dp))
            Text(r.count.toString(), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
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
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable {
                    if (media.isImage) onOpenImage(media) else scope.launch { MediaRepo.open(context, media) }
                },
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(it, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (media.isVideo) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(MsgsIcons.Play, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(26.dp)) }
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
            Icon(if (media.isAudio) MsgsIcons.Play else MsgsIcons.File, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                media.name.ifBlank { if (media.isAudio) "Voice message" else "Attachment" },
                fontSize = 15.sp,
                color = TextPrimary,
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
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeUri(context, Uri.parse(uri)) }
    }
    Box(Modifier.width(250.dp).aspectRatio(1.25f).background(Color.Black.copy(alpha = 0.25f))) {
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
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        if (conv?.isGroup == true) Spacer(Modifier.width(42.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.11f))
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { i ->
                val lit = (phase.toInt() % 3) == i
                Box(Modifier.size(8.dp).clip(CircleShape).background(TextPrimary.copy(alpha = if (lit) 0.9f else 0.35f)))
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
) {
    val p = LocalPalette.current
    androidx.compose.foundation.layout.Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
    ) {
        if (replyTo != null) {
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .barGlass(RoundedCornerShape(18.dp))
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MsgsIcons.Reply, contentDescription = null, tint = p.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    replyTo.previewText(),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(MsgsIcons.Close, "Cancel reply", onCancelReply, size = 34.dp, iconSize = 16.dp)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            GlassIconButton(MsgsIcons.Plus, "Add a photo or video", onAttach, size = 50.dp, iconSize = 22.dp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .barGlass(RoundedCornerShape(25.dp))
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = TextStyle(fontFamily = Geist, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
                    cursorBrush = SolidColor(p.accent),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    if (conv?.rcs == true) "RCS message" else "Text message",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextTertiary,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            AccentIconButton(MsgsIcons.Send, "Send", onSend, size = 50.dp, enabled = draft.isNotBlank())
        }
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
        Box(Modifier.fillMaxSize().background(Color.Black)) {
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
            GlassIconButton(
                MsgsIcons.Close,
                "Close",
                onClose,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
            )
        }
    }
}

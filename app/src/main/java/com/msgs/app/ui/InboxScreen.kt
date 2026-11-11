@file:OptIn(ExperimentalFoundationApi::class)

package com.msgs.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.data.Prefs
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Engine
import com.msgs.app.engine.EngineStatus
import com.msgs.app.engine.Participant
import com.msgs.app.ui.theme.Ink
import com.msgs.app.ui.theme.Sans
import com.msgs.app.ui.theme.SansCondensed
import com.msgs.app.ui.theme.Serif
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The front page: the newest thing someone said to you, set as a cover line, the next two
 * underneath, whoever is typing, and then the index of every other conversation.
 */
private class FrontPage(
    val cover: Conversation?,
    val fresh: Boolean,
    val alsoNew: List<Conversation>,
    val typingLine: Conversation?,
) {
    val featured: Set<String> = buildSet {
        cover?.let { add(it.id) }
        alsoNew.forEach { add(it.id) }
        typingLine?.let { add(it.id) }
    }
    val isEmpty: Boolean get() = cover == null && alsoNew.isEmpty() && typingLine == null

    companion object {
        val None = FrontPage(null, false, emptyList(), null)
    }
}

private fun isNew(c: Conversation) = c.unread && !c.previewFromMe

/**
 * [held] keeps the cover on a conversation while you're replying to it from here, so a
 * message arriving elsewhere doesn't pull the cover out from under your thumbs.
 */
private fun frontPage(visible: List<Conversation>, typing: Set<String>, held: String?): FrontPage {
    val unread = visible.filter(::isNew)
    val cover = held?.let { id -> visible.firstOrNull { it.id == id } }
        ?: unread.firstOrNull()
        ?: visible.firstOrNull { !it.previewFromMe && it.preview.isNotBlank() }
    val alsoNew = unread.filter { it.id != cover?.id }.take(2)
    val shown = setOfNotNull(cover?.id) + alsoNew.map { it.id }
    val typingLine = visible.firstOrNull { it.id in typing && it.id !in shown }
    return FrontPage(cover, cover != null && isNew(cover), alsoNew, typingLine)
}

@Composable
fun InboxScreen(
    selectedId: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val convMap by Engine.conversations.collectAsState()
    val pins by Prefs.pins.collectAsState()
    val status by Engine.status.collectAsState()
    val refreshing by Engine.refreshing.collectAsState()
    val typingMap by Engine.typing.collectAsState()
    val typingIds = rememberTypingIds()
    val keyboard = LocalSoftwareKeyboardController.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var replyingTo by rememberSaveable { mutableStateOf<String?>(null) }
    var replyDraft by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }

    BackHandler(enabled = showArchived) { showArchived = false }
    BackHandler(enabled = searching) { searching = false; query = "" }
    BackHandler(enabled = replyingTo != null) { replyingTo = null }

    val sorted = remember(convMap) { convMap.values.sortedByDescending { it.lastTs } }
    val visible = remember(sorted, showArchived) { sorted.filter { if (showArchived) it.isArchived else it.isVisible } }
    val favorites = if (showArchived) emptyList() else pins.mapNotNull { convMap[it] }.filter { it.isVisible }
    val onFront = !showArchived && filter == 0 && query.isBlank()
    val page = if (onFront) frontPage(visible, typingIds, replyingTo) else FrontPage.None
    val index = visible
        .filter { it.id !in page.featured }
        .filter {
            when (filter) {
                1 -> it.unread
                2 -> it.isGroup
                else -> true
            }
        }
        .filter { c ->
            query.isBlank() || c.title.contains(query, true) || c.preview.contains(query, true) ||
                c.participants.any { it.number.contains(query) }
        }

    fun typistOf(c: Conversation): Participant? {
        val now = System.currentTimeMillis()
        val number = typingMap[c.id].orEmpty().entries.firstOrNull { it.value > now }?.key ?: return null
        return c.others.firstOrNull { it.number == number || it.rawNumber == number }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = navBottom + 80.dp)) {
            item(key = "masthead") {
                Masthead(
                    archived = showArchived,
                    filter = filter,
                    onFilter = { filter = if (filter == it) 0 else it },
                    onBack = { showArchived = false },
                    onArchived = { showArchived = true; replyingTo = null },
                    onSettings = onSettings,
                    favorites = favorites,
                    typing = typingIds,
                    onOpen = onOpen,
                )
            }
            item(key = "status") { ConnectionLine(status) }
            if (filter != 0 && !showArchived) {
                item(key = "filter") { FilterNote(filter) { filter = 0 } }
            }
            page.cover?.let { c ->
                item(key = "cover") {
                    CoverLine(
                        c = c,
                        fresh = page.fresh,
                        typing = c.id in typingIds,
                        replying = replyingTo == c.id,
                        draft = replyDraft,
                        onDraft = { replyDraft = it },
                        onReply = { replyingTo = c.id },
                        onCloseReply = { replyingTo = null },
                        onSend = {
                            val text = replyDraft
                            keyboard?.hide()
                            Engine.markConversationRead(c.id)
                            Engine.sendText(c.id, text)
                            replyDraft = ""
                            replyingTo = null
                        },
                        onOpen = { onOpen(c.id) },
                        onDelete = { confirmDelete = c },
                    )
                }
            }
            page.alsoNew.forEachIndexed { i, c ->
                item(key = "also:${c.id}") {
                    AlsoNewLine(
                        c = c,
                        first = i == 0,
                        typing = c.id in typingIds,
                        onOpen = { onOpen(c.id) },
                        onDelete = { confirmDelete = c },
                    )
                }
            }
            page.typingLine?.let { c ->
                item(key = "typing:${c.id}") {
                    TypingLine(c, typistOf(c), first = page.alsoNew.isEmpty(), onOpen = { onOpen(c.id) })
                }
            }
            if (!page.isEmpty) item(key = "gap") { Spacer(Modifier.height(10.dp)) }
            items(index, key = { it.id }) { c ->
                IndexRow(
                    c = c,
                    selected = c.id == selectedId,
                    typing = c.id in typingIds,
                    onOpen = { onOpen(c.id) },
                    onDelete = { confirmDelete = c },
                    modifier = Modifier.animateItem(),
                )
            }
            if (index.isEmpty() && page.isEmpty) {
                item(key = "empty") {
                    Text(
                        when {
                            refreshing || (status.state == "connecting" && convMap.isEmpty()) -> "Loading your conversations…"
                            query.isNotBlank() -> "Nothing matches “$query”."
                            showArchived -> "Nothing archived."
                            filter == 1 -> "You're all caught up."
                            filter == 2 -> "No group chats yet."
                            else -> "No conversations yet. Tap Write to start one."
                        },
                        fontFamily = Serif,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        color = Ink.Text2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
                    )
                }
            }
        }

        BottomBar(
            searching = searching,
            query = query,
            onQuery = { query = it },
            onSearch = { searching = true; replyingTo = null },
            onCancelSearch = { searching = false; query = "" },
            onWrite = onNew,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this conversation?") },
            text = { Text("This deletes “${c.title}” from Google Messages on your phone too.") },
            confirmButton = {
                TextButton(onClick = { Engine.deleteConversation(c.id); confirmDelete = null }) {
                    Text("Delete", color = Ink.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = Ink.Text) }
            },
        )
    }
}

private fun todayLine(): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")
    return SimpleDateFormat(pattern, locale).format(Date())
}

@Composable
private fun Masthead(
    archived: Boolean,
    filter: Int,
    onFilter: (Int) -> Unit,
    onBack: () -> Unit,
    onArchived: () -> Unit,
    onSettings: () -> Unit,
    favorites: List<Conversation>,
    typing: Set<String>,
    onOpen: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val hour = System.currentTimeMillis() / 3_600_000L
    val today = remember(hour) { todayLine() }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (archived) 8.dp else 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (archived) {
                BackLink("Messages", onBack)
                Spacer(Modifier.weight(1f))
                Text("Archived", fontFamily = Serif, fontSize = 16.sp, color = Ink.Text2, modifier = Modifier.padding(end = 14.dp))
            } else {
                Text("Messages", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, color = Ink.Text)
                Spacer(Modifier.weight(1f))
                Text(today, fontFamily = Serif, fontSize = 16.sp, color = Ink.Text2, maxLines = 1)
                Box {
                    GlyphButton(MsgsIcons.More, "More options", { menu = true }, iconSize = 20.dp)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Unread only") },
                            trailingIcon = { if (filter == 1) Icon(MsgsIcons.Check, contentDescription = "On", tint = Ink.GreenText) },
                            onClick = { menu = false; onFilter(1) },
                        )
                        DropdownMenuItem(
                            text = { Text("Groups only") },
                            trailingIcon = { if (filter == 2) Icon(MsgsIcons.Check, contentDescription = "On", tint = Ink.GreenText) },
                            onClick = { menu = false; onFilter(2) },
                        )
                        DropdownMenuItem(
                            text = { Text("Archived") },
                            leadingIcon = { Icon(MsgsIcons.Archive, contentDescription = null) },
                            onClick = { menu = false; onArchived() },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(MsgsIcons.Sliders, contentDescription = null) },
                            onClick = { menu = false; onSettings() },
                        )
                    }
                }
            }
        }
        Hairline(Modifier.padding(horizontal = 20.dp))
        if (favorites.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(favorites, key = { it.id }) { c -> FavoriteName(c, c.id in typing, onOpen) }
            }
            Hairline(Modifier.padding(horizontal = 20.dp))
        }
    }
}

/** One name in the favorites line. Paper with an ember mark when they've written, sage while they type. */
@Composable
private fun FavoriteName(c: Conversation, typing: Boolean, onOpen: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val hot = isNew(c)
    Box {
        Box(
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(role = Role.Button, onClick = { onOpen(c.id) }, onLongClick = { menu = true })
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                shortName(c),
                fontSize = 15.sp,
                fontWeight = if (hot) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    typing -> Ink.GreenText
                    hot -> Ink.Text
                    else -> Ink.Text2
                },
                maxLines = 1,
            )
            if (hot) EmberMark(Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = 13.dp), size = 6.dp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Unpin") },
                leadingIcon = { Icon(MsgsIcons.Pin, contentDescription = null) },
                onClick = { Prefs.setPinned(c.id, false); menu = false },
            )
        }
    }
}

@Composable
private fun ConnectionLine(status: EngineStatus) {
    val (text, action) = when (status.state) {
        "connecting", "reconnecting" -> "Connecting to your phone…" to null
        "phone_not_responding" -> "Your phone isn't responding. Check it's online." to null
        "inactive" -> "Messages is open on another device. Tap to use it here." to { Engine.setActiveHere() }
        "error" -> "Can't reach Google Messages. Tap to retry." to { Engine.connect() }
        else -> null to null
    }
    if (text == null) return
    val mark = when (status.state) {
        "phone_not_responding", "error" -> Ink.Danger
        "inactive" -> Ink.GreenText
        else -> Ink.Text2
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (action != null) Modifier.clickable(role = Role.Button, onClick = action) else Modifier)
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(mark))
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 14.sp, lineHeight = 19.sp, color = Ink.Text)
        }
        Hairline()
    }
}

@Composable
private fun FilterNote(filter: Int, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (filter == 1) "Showing unread" else "Showing groups",
            fontFamily = Serif,
            fontSize = 17.sp,
            color = Ink.Text2,
            modifier = Modifier.weight(1f),
        )
        InkLink("Show all", onClear, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- the front page

private class Quote(val body: String, val quoted: Boolean)

private val placeholders = mapOf(
    "photo" to "Sent a photo.",
    "image" to "Sent a photo.",
    "picture" to "Sent a photo.",
    "video" to "Sent a video.",
    "voice message" to "Sent a voice message.",
    "audio" to "Sent a voice message.",
    "attachment" to "Sent an attachment.",
    "sticker" to "Sent a sticker.",
    "gif" to "Sent a GIF.",
    "location" to "Shared a location.",
    "contact" to "Shared a contact.",
)

/** The words for a cover line: quoted when they're words, plain when it was a photo or a file. */
private fun quoteOf(preview: String): Quote {
    val clean = preview.replace(Regex("\\s+"), " ").trim()
    placeholders[clean.lowercase(Locale.ROOT)]?.let { return Quote(it, false) }
    if (clean.isEmpty()) return Quote("Sent a message.", false)
    val body = if (clean.length > 140) clean.take(138).trimEnd() + "…" else clean
    return Quote(body, true)
}

/** Who wrote the latest message in a group, if we can tell. */
private fun senderOf(c: Conversation): Participant? {
    if (!c.isGroup || c.previewFromMe) return null
    val name = c.previewSender.trim()
    if (name.isEmpty()) return null
    return c.others.firstOrNull {
        it.displayName.equals(name, true) || it.name.equals(name, true) || it.first.equals(name, true)
    } ?: c.others.firstOrNull { it.first.isNotBlank() && name.startsWith(it.first, true) }
}

/** "now" for the last minute, otherwise the inbox time. */
private fun bylineTime(context: Context, ts: Long): String =
    if (System.currentTimeMillis() - ts in 0..59_999) "now" else inboxTime(context, ts)

private fun whoOf(c: Conversation, person: Participant?): String = when {
    !c.isGroup -> shortName(c)
    person != null -> "${firstNameOf(person, c.previewSender)} in ${c.title}"
    c.previewSender.isNotBlank() -> "${c.previewSender.substringBefore(' ')} in ${c.title}"
    else -> c.title
}

/** "Mike, 9:46 AM", "Tyler in Fantasy League, Yesterday". */
private fun byline(context: Context, c: Conversation, sender: Participant?, typing: Boolean): String =
    if (typing) "${whoOf(c, sender)} is typing" else "${whoOf(c, sender)}, ${bylineTime(context, c.lastTs)}"

/** Cover type steps down as the message gets longer, so a short line can be enormous. */
private fun coverSize(length: Int): Float = when {
    length <= 22 -> 62f
    length <= 48 -> 46f
    length <= 90 -> 34f
    else -> 28f
}

/** The cover quote, with its opening mark hung in the margin so the words line up with the page. */
@Composable
private fun CoverQuote(q: Quote, scale: Float, color: Color) {
    val size = coverSize(q.body.length) * scale
    val style = TextStyle(
        fontFamily = SansCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        lineHeight = (size * 0.92f).sp,
        letterSpacing = (size * -0.022f).sp,
        color = color,
    )
    Box {
        if (q.quoted) {
            Text(
                "“",
                style = style,
                modifier = Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                    layout(0, 0) { p.place(-p.width, 0) }
                },
            )
        }
        Text(if (q.quoted) q.body + "”" else q.body, style = style)
    }
}

@Composable
private fun CoverLine(
    c: Conversation,
    fresh: Boolean,
    typing: Boolean,
    replying: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onReply: () -> Unit,
    onCloseReply: () -> Unit,
    onSend: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val quote = remember(c.preview) { quoteOf(c.preview) }
    val sender = remember(c) { senderOf(c) }
    val collage = rememberCollage(c)
    val ink = if (fresh) Ink.Text else Ink.Text2
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Box {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The quote column is 248dp on a 412dp phone; the type keeps that proportion.
                val column = maxWidth - 40.dp - 16.dp - 108.dp
                val scale = (column / 248.dp).coerceIn(0.78f, 1.3f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        CoverQuote(quote, scale, ink)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                byline(context, c, sender, typing),
                                fontFamily = Serif,
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                color = if (typing) Ink.GreenText else ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (!c.readOnly) {
                                Spacer(Modifier.width(4.dp))
                                if (replying) {
                                    TextAction("Cancel", onCloseReply, color = Ink.Text2)
                                } else {
                                    InkLink("Reply", onReply, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    val person = sender ?: if (c.isGroup && collage) c.others.firstOrNull() else null
                    AvatarFill(
                        c,
                        Modifier.width(108.dp).height(144.dp).clip(PortraitShape),
                        person = person,
                        initialsSize = 54.sp,
                    )
                }
            }
            ConversationMenu(c, menu, { menu = false }, onDelete)
        }
        if (replying) InlineReply(shortName(c), draft, onDraft, onSend)
    }
}

/** Reply from the front page without opening the conversation. */
@Composable
private fun InlineReply(to: String, draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
        Hairline(Modifier.padding(end = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                textStyle = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 24.sp, color = Ink.GreenText),
                cursorBrush = SolidColor(Ink.GreenText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text("Write to $to", fontSize = 17.sp, lineHeight = 24.sp, color = Ink.Text3)
                        inner()
                    }
                },
            )
            InkLink("Send", onSend, enabled = draft.isNotBlank(), fontSize = 15.sp)
        }
    }
}

/** The next unread messages, set smaller under the cover. */
@Composable
private fun AlsoNewLine(c: Conversation, first: Boolean, typing: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val sender = remember(c) { senderOf(c) }
    val quote = remember(c.preview) { quoteOf(c.preview) }
    Column(Modifier.fillMaxWidth()) {
        if (first) Spacer(Modifier.height(26.dp))
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                    .padding(horizontal = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            byline(context, c, sender, typing),
                            fontFamily = Serif,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            color = if (typing) Ink.GreenText else Ink.Text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (quote.quoted) "“${quote.body}”" else quote.body,
                            fontFamily = SansCondensed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp,
                            lineHeight = 27.sp,
                            letterSpacing = (-0.4).sp,
                            color = Ink.Text,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    EmberMark(Modifier.padding(top = 6.dp), size = 8.dp)
                }
                Hairline()
            }
            ConversationMenu(c, menu, { menu = false }, onDelete)
        }
    }
}

/** Someone typing, pulled up to the front page while they do. */
@Composable
private fun TypingLine(c: Conversation, typist: Participant?, first: Boolean, onOpen: () -> Unit) {
    val who = when {
        !c.isGroup -> shortName(c)
        typist != null -> "${firstNameOf(typist, c.title)} in ${c.title}"
        else -> c.title
    }
    Column(Modifier.fillMaxWidth()) {
        if (first) Spacer(Modifier.height(26.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.padding(top = 14.dp, bottom = 16.dp)) {
                Text(
                    "$who, now",
                    fontFamily = Serif,
                    fontSize = 16.sp,
                    color = Ink.Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "is typing",
                    fontFamily = SansCondensed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.3).sp,
                    color = Ink.GreenText,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Hairline()
        }
    }
}

// ---------------------------------------------------------------- the index

private fun previewLine(c: Conversation): String {
    val text = c.preview.replace('\n', ' ')
    return when {
        c.previewFromMe -> "You: $text"
        c.isGroup && c.previewSender.isNotBlank() && !text.startsWith(c.previewSender) ->
            "${c.previewSender.substringBefore(' ')}: $text"
        else -> text
    }
}

/** Every other conversation: the time in the margin, the name, one line of what was said. */
@Composable
private fun IndexRow(
    c: Conversation,
    selected: Boolean,
    typing: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().background(if (selected) Ink.Raised else Ink.Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 20.dp)
                .heightIn(min = 62.dp)
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(Ink.Line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                inboxTime(context, c.lastTs),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (c.unread) Ink.Text2 else Ink.Text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.width(76.dp).padding(end = 8.dp),
            )
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    c.title,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = if (c.unread) FontWeight.Bold else FontWeight.SemiBold,
                    color = Ink.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                if (typing) {
                    Text("is typing", fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = Ink.GreenText)
                } else {
                    Text(
                        previewLine(c),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = if (c.unread) Ink.Unread else Ink.Text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (c.unread) {
                Spacer(Modifier.width(12.dp))
                EmberMark(size = 8.dp)
            }
        }
        ConversationMenu(c, menu, { menu = false }, onDelete)
    }
}

@Composable
private fun ConversationMenu(c: Conversation, expanded: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val pins by Prefs.pins.collectAsState()
    val pinned = c.id in pins
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (pinned) "Unpin" else "Pin to top") },
            leadingIcon = { Icon(MsgsIcons.Pin, contentDescription = null) },
            onClick = { Prefs.setPinned(c.id, !pinned); onDismiss() },
        )
        if (c.unread) {
            DropdownMenuItem(
                text = { Text("Mark as read") },
                leadingIcon = { Icon(MsgsIcons.DoubleCheck, contentDescription = null) },
                onClick = { Engine.markConversationRead(c.id); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text(if (c.isArchived) "Unarchive" else "Archive") },
            leadingIcon = { Icon(MsgsIcons.Archive, contentDescription = null) },
            onClick = { Engine.setArchived(c.id, !c.isArchived); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Delete", color = Ink.Danger) },
            leadingIcon = { Icon(MsgsIcons.Trash, contentDescription = null, tint = Ink.Danger) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}

// ---------------------------------------------------------------- the bar

@Composable
private fun BottomBar(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Ink.Bg)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Icon(
                    MsgsIcons.Search,
                    contentDescription = null,
                    tint = Ink.Text3,
                    modifier = Modifier.padding(start = 12.dp).size(19.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Sans, fontSize = 16.sp, color = Ink.Text),
                    cursorBrush = SolidColor(Ink.GreenText),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("Search names and messages", fontSize = 16.sp, color = Ink.Text3, maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
                TextAction("Cancel", onCancelSearch, color = Ink.Text2)
            } else {
                Row(
                    Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onSearch)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MsgsIcons.Search, contentDescription = null, tint = Ink.Text2, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Search", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text)
                }
                Spacer(Modifier.weight(1f))
                InkLink("Write", onWrite)
            }
        }
    }
}

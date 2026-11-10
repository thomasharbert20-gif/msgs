@file:OptIn(ExperimentalFoundationApi::class)

package com.msgs.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.data.Prefs
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Engine
import com.msgs.app.engine.EngineStatus
import com.msgs.app.ui.theme.Archivo
import com.msgs.app.ui.theme.ArchivoExpanded
import com.msgs.app.ui.theme.ArchivoWide
import com.msgs.app.ui.theme.Ink

private val filterLabels = listOf("All conversations", "Unread", "Groups")

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
    val typingIds = rememberTypingIds()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }

    val sorted = remember(convMap) { convMap.values.sortedByDescending { it.lastTs } }
    val unreadCount = remember(convMap) { convMap.values.count { it.isVisible && it.unread } }
    val pinned = if (showArchived) emptyList() else pins.mapNotNull { convMap[it] }.filter { it.isVisible }
    val list = sorted
        .filter { if (showArchived) it.isArchived else it.isVisible }
        .filter { showArchived || query.isNotBlank() || it.id !in pins }
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

    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item(key = "header") {
                InboxHeader(
                    archived = showArchived,
                    unread = unreadCount,
                    filter = filter,
                    onFilter = { filter = it },
                    onBack = { showArchived = false },
                    onArchived = { showArchived = true },
                    onSettings = onSettings,
                )
            }
            item(key = "status") { ConnectionBanner(status) }
            if (pinned.isNotEmpty() && query.isBlank()) {
                item(key = "pins") { PinnedCards(pinned, typingIds, onOpen) }
            }
            if (filter != 0 && !showArchived) {
                item(key = "filter") { FilterNote(filter) { filter = 0 } }
            }
            item(key = "gap") { Spacer(Modifier.height(10.dp)) }
            items(list, key = { it.id }) { c ->
                ConversationRow(
                    c = c,
                    selected = c.id == selectedId,
                    pinned = c.id in pins,
                    typing = c.id in typingIds,
                    onOpen = { onOpen(c.id) },
                    onDelete = { confirmDelete = c },
                    modifier = Modifier.animateItem(),
                )
            }
            if (list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        when {
                            refreshing || (status.state == "connecting" && convMap.isEmpty()) -> "Loading your conversations…"
                            query.isNotBlank() -> "Nothing matches “$query”"
                            showArchived -> "No archived conversations"
                            filter == 1 -> "You're all caught up"
                            filter == 2 -> "No group chats yet"
                            pinned.isNotEmpty() -> "Everything else shows up here"
                            else -> "No conversations yet. Tap New to start one."
                        },
                        color = Ink.Text2,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    )
                }
            }
        }

        BottomBar(
            query = query,
            onQuery = { query = it },
            onNew = onNew,
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

@Composable
private fun InboxHeader(
    archived: Boolean,
    unread: Int,
    filter: Int,
    onFilter: (Int) -> Unit,
    onBack: () -> Unit,
    onArchived: () -> Unit,
    onSettings: () -> Unit,
) {
    var filterMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (archived) CircleIcon(MsgsIcons.Back, "Back to messages", onBack)
            Spacer(Modifier.weight(1f))
            if (!archived) {
                Box {
                    CircleIcon(
                        MsgsIcons.Filter,
                        "Filter conversations",
                        { filterMenu = true },
                        background = if (filter != 0) Ink.Green else Ink.Raised,
                        iconSize = 20.dp,
                    )
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        filterLabels.forEachIndexed { i, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = {
                                    if (i == filter) Icon(MsgsIcons.Check, contentDescription = "Selected", tint = Ink.GreenText)
                                },
                                onClick = { onFilter(i); filterMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    CircleIcon(MsgsIcons.More, "More options", { moreMenu = true }, iconSize = 20.dp)
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Archived") },
                            leadingIcon = { Icon(MsgsIcons.Archive, contentDescription = null) },
                            onClick = { moreMenu = false; onArchived() },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(MsgsIcons.Sliders, contentDescription = null) },
                            onClick = { moreMenu = false; onSettings() },
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
            Text(
                if (archived) "Archived" else "Messages",
                fontFamily = ArchivoExpanded,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = Ink.Text,
                maxLines = 1,
                softWrap = false,
            )
            if (!archived && unread > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    unread.toString(),
                    fontFamily = Archivo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Ink.GreenText,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .semantics { contentDescription = "$unread unread" },
                )
            }
        }
    }
}

@Composable
private fun ConnectionBanner(status: EngineStatus) {
    val (text, action) = when (status.state) {
        "connecting", "reconnecting" -> "Connecting to your phone…" to null
        "phone_not_responding" -> "Your phone isn't responding. Check it's online." to null
        "inactive" -> "Messages is open on another device. Tap to use it here." to { Engine.setActiveHere() }
        "error" -> "Can't reach Google Messages. Tap to retry." to { Engine.connect() }
        else -> null to null
    }
    if (text == null) return
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Raised)
            .then(if (action != null) Modifier.clickable(role = Role.Button, onClick = action) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (action != null) Ink.GreenText else Ink.Warn))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = Ink.Text)
    }
}

/** Pinned people as photo cards: the photo on top, a name bar below that turns green when there's news. */
@Composable
private fun PinnedCards(pinned: List<Conversation>, typing: Set<String>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
    ) {
        items(pinned, key = { it.id }) { c ->
            val isTyping = c.id in typing
            val hot = c.unread || isTyping
            Column(
                Modifier
                    .width(108.dp)
                    .height(164.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (hot) Ink.Green else Ink.Raised)
                    .clickable(role = Role.Button) { onOpen(c.id) },
            ) {
                CardPhoto(c, Modifier.fillMaxWidth().height(116.dp))
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        c.title,
                        fontFamily = ArchivoWide,
                        fontWeight = if (hot) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            isTyping -> "typing…"
                            c.unread -> "New message"
                            else -> inboxTime(context, c.lastTs)
                        },
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = if (hot) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (hot) Ink.Text else Ink.Text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardPhoto(c: Conversation, modifier: Modifier) {
    if (rememberCollage(c)) {
        Box(modifier.background(Ink.Mono), contentAlignment = Alignment.Center) {
            Avatar(c, 76.dp, ring = Ink.Mono)
        }
    } else {
        AvatarFill(c, modifier, initialsSize = 40.sp)
    }
}

@Composable
private fun FilterNote(filter: Int, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (filter == 1) "Showing unread" else "Showing groups",
            fontFamily = ArchivoWide,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Ink.Text2,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Show all",
            fontFamily = ArchivoWide,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Ink.GreenText,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClear)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

private fun previewLine(c: Conversation): String {
    val text = c.preview.replace('\n', ' ')
    return when {
        c.previewFromMe -> "You: $text"
        c.isGroup && c.previewSender.isNotBlank() && !text.startsWith(c.previewSender) ->
            "${c.previewSender.substringBefore(' ')}: $text"
        else -> text
    }
}

@Composable
private fun ConversationRow(
    c: Conversation,
    selected: Boolean,
    pinned: Boolean,
    typing: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val rowBg = if (selected) Ink.Raised else Ink.Bg
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(rowBg)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(c, 56.dp, ring = rowBg)
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(Ink.Line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title,
                        fontFamily = ArchivoWide,
                        fontWeight = if (c.unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        inboxTime(context, c.lastTs),
                        fontSize = 13.sp,
                        fontWeight = if (c.unread) FontWeight.Bold else FontWeight.Normal,
                        color = if (c.unread) Ink.GreenText else Ink.Text3,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.Top) {
                    if (typing) {
                        Text(
                            "typing…",
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink.GreenText,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            previewLine(c),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = if (c.unread) Ink.Unread else Ink.Text2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (c.unread) {
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .padding(top = 5.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Ink.GreenText)
                                .semantics { contentDescription = "Unread" },
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (pinned) "Unpin" else "Pin to top") },
                leadingIcon = { Icon(MsgsIcons.Pin, contentDescription = null) },
                onClick = { Prefs.setPinned(c.id, !pinned); menu = false },
            )
            if (c.unread) {
                DropdownMenuItem(
                    text = { Text("Mark as read") },
                    leadingIcon = { Icon(MsgsIcons.DoubleCheck, contentDescription = null) },
                    onClick = { Engine.markConversationRead(c.id); menu = false },
                )
            }
            DropdownMenuItem(
                text = { Text(if (c.isArchived) "Unarchive" else "Archive") },
                leadingIcon = { Icon(MsgsIcons.Archive, contentDescription = null) },
                onClick = { Engine.setArchived(c.id, !c.isArchived); menu = false },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = Ink.Danger) },
                leadingIcon = { Icon(MsgsIcons.Trash, contentDescription = null, tint = Ink.Danger) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun BottomBar(query: String, onQuery: (String) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Ink.Bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query, onQuery, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Ink.Green)
                    .clickable(role = Role.Button, onClick = onNew)
                    .padding(start = 16.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MsgsIcons.NewMessage, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text("New", fontFamily = ArchivoWide, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink.Text)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Ink.Raised)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MsgsIcons.Search, contentDescription = null, tint = Ink.Text3, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Archivo, fontSize = 16.sp, color = Ink.Text),
            cursorBrush = SolidColor(Ink.GreenText),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Search", fontSize = 16.sp, color = Ink.Text3)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            CircleIcon(
                MsgsIcons.Close,
                "Clear search",
                { onChange("") },
                size = 36.dp,
                iconSize = 16.dp,
                background = Ink.Raised2,
                tint = Ink.Text2,
            )
        }
    }
}

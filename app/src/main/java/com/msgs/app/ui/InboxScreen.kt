@file:OptIn(ExperimentalFoundationApi::class)

package com.msgs.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import com.msgs.app.ui.theme.Danger
import com.msgs.app.ui.theme.Geist
import com.msgs.app.ui.theme.LocalPalette
import com.msgs.app.ui.theme.TextPrimary
import com.msgs.app.ui.theme.TextSecondary
import com.msgs.app.ui.theme.TextTertiary
import com.msgs.app.ui.theme.TitleFont
import com.msgs.app.ui.theme.barGlass
import com.msgs.app.ui.theme.glass
import com.msgs.app.ui.theme.nameStyle

@Composable
fun InboxScreen(
    selectedId: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val convMap by Engine.conversations.collectAsState()
    val pins by Prefs.pins.collectAsState()
    val status by Engine.status.collectAsState()
    val refreshing by Engine.refreshing.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }

    val sorted = remember(convMap) { convMap.values.sortedByDescending { it.lastTs } }
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp)) {
            item(key = "header") {
                InboxHeader(
                    archived = showArchived,
                    onBack = { showArchived = false },
                    onArchived = { showArchived = true },
                    onSettings = onSettings,
                )
            }
            item(key = "status") { ConnectionBanner(status) }
            if (pinned.isNotEmpty() && query.isBlank()) {
                item(key = "pins") { PinnedRow(pinned, onOpen) }
            }
            if (!showArchived) {
                item(key = "chips") { FilterChips(filter) { filter = it } }
            }
            items(list, key = { it.id }) { c ->
                ConversationRow(
                    c = c,
                    selected = c.id == selectedId,
                    pinned = c.id in pins,
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
                            else -> "No conversations yet. Tap the pencil to start one."
                        },
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, p.bottom.copy(alpha = 0.94f)))),
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            AccentIconButton(MsgsIcons.Compose, "New message", onNew, size = 54.dp)
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this conversation?") },
            text = { Text("This deletes “${c.title}” from Google Messages on your phone too.") },
            confirmButton = {
                TextButton(onClick = { Engine.deleteConversation(c.id); confirmDelete = null }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = TextPrimary) } },
        )
    }
}

@Composable
private fun InboxHeader(archived: Boolean, onBack: () -> Unit, onArchived: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (archived) {
            GlassIconButton(MsgsIcons.Back, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            if (archived) "ARCHIVED" else "MESSAGES",
            fontFamily = TitleFont,
            fontSize = if (archived) 36.sp else 46.sp,
            lineHeight = 50.sp,
            letterSpacing = 0.5.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!archived) {
            GlassIconButton(MsgsIcons.Archive, "Archived conversations", onArchived)
            Spacer(Modifier.width(8.dp))
        }
        GlassIconButton(MsgsIcons.Sliders, "Settings", onSettings)
    }
}

@Composable
private fun ConnectionBanner(status: EngineStatus) {
    val p = LocalPalette.current
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), fill = 0.08f)
            .then(if (action != null) Modifier.clickable(role = Role.Button, onClick = action) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (action != null) p.accent else Color(0xFFF2C14E)))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = TextPrimary)
    }
}

@Composable
private fun PinnedRow(pinned: List<Conversation>, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    ) {
        items(pinned, key = { it.id }) { c ->
            Column(
                Modifier
                    .width(74.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { onOpen(c.id) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(70.dp)
                        .border(
                            if (c.unread) 2.dp else 1.dp,
                            if (c.unread) p.accent else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(25.dp),
                        )
                        .padding(4.dp),
                ) {
                    Avatar(c, 62.dp, shape = RoundedCornerShape(21.dp))
                    if (c.unread) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(p.top)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(p.accent),
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    c.title,
                    style = nameStyle(14),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FilterChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("All", "Unread", "Groups").forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(36.dp)
                    .glass(RoundedCornerShape(18.dp), fill = if (on) 0.18f else 0.06f, edge = if (on) 0.18f else 0.09f)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) TextPrimary else TextSecondary,
                )
            }
        }
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
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) Color.White.copy(alpha = 0.07f) else Color.Transparent)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(c, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title,
                        style = nameStyle(18),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        inboxTime(context, c.lastTs),
                        fontSize = 13.sp,
                        fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (c.unread) p.accent else TextTertiary,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.previewFromMe && c.previewStatus == 11) {
                        Icon(MsgsIcons.DoubleCheck, contentDescription = "Read", tint = p.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        previewLine(c),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = if (c.unread) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (c.unread) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(p.accent))
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
                text = { Text("Delete", color = Danger) },
                leadingIcon = { Icon(MsgsIcons.Trash, contentDescription = null, tint = Danger) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier
            .height(54.dp)
            .barGlass(RoundedCornerShape(27.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MsgsIcons.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Geist, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Search", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                MsgsIcons.Close,
                contentDescription = "Clear search",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
            )
        }
    }
}

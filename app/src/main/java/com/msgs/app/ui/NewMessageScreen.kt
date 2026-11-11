package com.msgs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.engine.Engine
import com.msgs.app.engine.Participant
import com.msgs.app.ui.theme.Sans
import com.msgs.app.ui.theme.SansCondensed
import com.msgs.app.ui.theme.Serif

import com.msgs.app.ui.theme.Ink
import kotlinx.coroutines.launch

@Composable
fun NewMessageScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Participant>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        contacts = Engine.listContacts()
    }

    val digits = query.filter { it.isDigit() }
    val filtered = contacts.orEmpty().filter { c ->
        query.isBlank() || c.name.contains(query, true) || (digits.length >= 3 && c.number.filter { it.isDigit() }.contains(digits))
    }

    fun start(number: String) {
        if (busy || number.isBlank()) return
        busy = true
        scope.launch {
            val id = Engine.startConversation(listOf(number))
            busy = false
            if (id != null) onOpen(id)
        }
    }

    Column(Modifier.fillMaxSize().background(Ink.Bg).imePadding()) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 16.dp, top = 4.dp)) {
            BackLink("Messages", onBack)
            Spacer(Modifier.height(8.dp))
            Text(
                "New message",
                fontFamily = SansCondensed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 46.sp,
                lineHeight = 44.sp,
                letterSpacing = (-1.1).sp,
                color = Ink.Text,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 18.dp)
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("To", fontFamily = Serif, fontSize = 19.sp, color = Ink.Text2)
            Spacer(Modifier.width(14.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Sans, fontSize = 17.sp, color = Ink.Text),
                cursorBrush = SolidColor(Ink.GreenText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (digits.length >= 3) start(query) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Name or number", fontSize = 17.sp, color = Ink.Text3)
                        inner()
                    }
                },
            )
        }
        Hairline(Modifier.padding(horizontal = 20.dp))
        if (busy) {
            Text("Opening the conversation…", fontFamily = Serif, color = Ink.Text2, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (digits.length >= 3) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { start(query) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Green), contentAlignment = Alignment.Center) {
                            Icon(MsgsIcons.Send, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text("Send to $query", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (contacts == null) {
                item { Text("Loading contacts…", fontFamily = Serif, color = Ink.Text2, fontSize = 17.sp, modifier = Modifier.padding(20.dp)) }
            }
            items(filtered) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { start(c.rawNumber.ifBlank { c.number }) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(null, 50.dp, person = c)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.displayName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.name.isNotBlank()) Text(c.number, fontSize = 14.sp, color = Ink.Text2)
                    }
                }
            }
        }
    }
}

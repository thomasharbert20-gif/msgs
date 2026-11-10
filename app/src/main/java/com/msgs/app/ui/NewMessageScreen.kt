package com.msgs.app.ui

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
import com.msgs.app.ui.theme.Geist
import com.msgs.app.ui.theme.LocalPalette
import com.msgs.app.ui.theme.TextPrimary
import com.msgs.app.ui.theme.TextSecondary
import com.msgs.app.ui.theme.TextTertiary
import com.msgs.app.ui.theme.TitleFont
import com.msgs.app.ui.theme.barGlass
import com.msgs.app.ui.theme.nameStyle
import kotlinx.coroutines.launch

@Composable
fun NewMessageScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
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

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(MsgsIcons.Back, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text("NEW MESSAGE", fontFamily = TitleFont, fontSize = 32.sp, letterSpacing = 0.5.sp, color = TextPrimary)
        }
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(54.dp)
                .barGlass(RoundedCornerShape(27.dp))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("To", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Geist, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (digits.length >= 3) start(query) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Name or number", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
                        inner()
                    }
                },
            )
        }
        if (busy) {
            Text("Opening the conversation…", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (digits.length >= 3) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { start(query) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).barGlass(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                            Icon(MsgsIcons.Send, contentDescription = null, tint = p.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text("Send to $query", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (contacts == null) {
                item { Text("Loading contacts…", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
            }
            items(filtered) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { start(c.rawNumber.ifBlank { c.number }) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(null, 48.dp, person = c)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.displayName, style = nameStyle(17), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.name.isNotBlank()) Text(c.number, fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

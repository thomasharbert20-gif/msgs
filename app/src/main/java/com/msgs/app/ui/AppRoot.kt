package com.msgs.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.engine.Engine
import com.msgs.app.ui.theme.AmbientBackground
import com.msgs.app.ui.theme.MsgsTheme
import com.msgs.app.ui.theme.TextSecondary
import com.msgs.app.update.Updater

sealed interface Route {
    data object Inbox : Route
    data class Chat(val id: String) : Route
    data class Contact(val id: String) : Route
    data object Settings : Route
    data object NewMessage : Route
}

@Composable
fun AppRoot(pendingConversation: String?, onPendingHandled: () -> Unit) {
    MsgsTheme {
        val context = LocalContext.current
        val status by Engine.status.collectAsState()
        LaunchedEffect(Unit) {
            Engine.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
        LaunchedEffect(status.paired) {
            if (status.paired) Updater.check(context)
        }
        AmbientBackground(Modifier.fillMaxSize()) {
            if (!status.paired) {
                PairingScreen()
            } else {
                Navigator(pendingConversation, onPendingHandled)
            }
        }
    }
}

@Composable
private fun Navigator(pendingConversation: String?, onPendingHandled: () -> Unit) {
    val stack = remember { mutableStateListOf<Route>(Route.Inbox) }
    fun push(r: Route) {
        stack.add(r)
    }
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun openChat(id: String) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(Route.Chat(id))
    }

    LaunchedEffect(pendingConversation) {
        if (pendingConversation != null) {
            openChat(pendingConversation)
            onPendingHandled()
        }
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val listWidth = if (maxWidth >= 840.dp) 400.dp else 360.dp
        val top = stack.last()
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) {
                    InboxScreen(
                        selectedId = (top as? Route.Chat)?.id ?: (top as? Route.Contact)?.id,
                        onOpen = { openChat(it) },
                        onNew = { push(Route.NewMessage) },
                        onSettings = { push(Route.Settings) },
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.07f)))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Detail(top, wide = true, ::push, ::pop, ::openChat)
                }
            }
        } else {
            AnimatedContent(
                targetState = top,
                transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut() },
                label = "screens",
            ) { route ->
                Detail(route, wide = false, ::push, ::pop, ::openChat)
            }
        }
    }
}

@Composable
private fun Detail(
    route: Route,
    wide: Boolean,
    push: (Route) -> Unit,
    pop: () -> Unit,
    openChat: (String) -> Unit,
) {
    when (route) {
        Route.Inbox -> if (wide) {
            EmptyDetail()
        } else {
            InboxScreen(
                selectedId = null,
                onOpen = openChat,
                onNew = { push(Route.NewMessage) },
                onSettings = { push(Route.Settings) },
            )
        }
        is Route.Chat -> ChatScreen(
            convId = route.id,
            showBack = !wide,
            onBack = pop,
            onContact = { push(Route.Contact(route.id)) },
        )
        is Route.Contact -> ContactScreen(convId = route.id, onBack = pop)
        Route.Settings -> SettingsScreen(onBack = pop)
        Route.NewMessage -> NewMessageScreen(onBack = pop, onOpen = openChat)
    }
}

@Composable
private fun EmptyDetail() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(MsgsIcons.Bubble, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Pick a conversation", color = TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

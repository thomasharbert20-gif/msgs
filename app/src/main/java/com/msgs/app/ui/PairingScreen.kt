package com.msgs.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.msgs.app.engine.Engine
import com.msgs.app.notify.Notifications
import com.msgs.app.ui.theme.Archivo
import com.msgs.app.ui.theme.ArchivoExpanded
import com.msgs.app.ui.theme.ArchivoWide
import com.msgs.app.ui.theme.Ink
import gmbridge.Gmbridge
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val SIGN_IN_URL = "https://accounts.google.com/AccountChooser?continue=https://messages.google.com/web/config"

/**
 * One-time setup: sign in to Google inside the app (so Msgs gets a Messages-for-web
 * session), then tap the matching emoji in Google Messages to approve the pairing.
 */
@Composable
fun PairingScreen() {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var emoji by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun begin(cookiesJson: String) {
        step = 2
        emoji = ""
        error = null
        scope.launch {
            try {
                emoji = Engine.startPairing(cookiesJson)
                Engine.finishPairing()
            } catch (e: Exception) {
                error = e.message?.substringAfterLast("Exception: ")?.replaceFirstChar { it.uppercase() } ?: "Pairing failed"
                step = 3
            }
        }
    }

    when (step) {
        0 -> Intro(onStart = { step = 1 }, onPaste = { begin(it) })
        1 -> GoogleSignIn(onCookies = { begin(it) }, onCancel = { step = 0 })
        2 -> EmojiStep(emoji, onCancel = {
            Engine.cancelPairing()
            step = 0
        })
        else -> Failed(error ?: "Pairing failed", onRetry = { step = 1 }, onBack = { step = 0 })
    }
}

@Composable
private fun Intro(onStart: () -> Unit, onPaste: (String) -> Unit) {
    var showPaste by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            "Messages",
            fontFamily = ArchivoExpanded,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 48.sp,
            lineHeight = 52.sp,
            letterSpacing = (-0.5).sp,
            color = Ink.Text,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Msgs runs on top of Google Messages, the same way Messages for web does. Google Messages keeps doing the RCS work in the background; you just never have to open it.",
            fontSize = 16.sp,
            lineHeight = 23.sp,
            color = Ink.Text2,
        )
        Spacer(Modifier.height(28.dp))
        Step(1, "Sign in to Google", "Use the same account Google Messages on this phone is signed in to.")
        Step(2, "Tap the matching emoji", "Google Messages will ask you to confirm. Tap the emoji Msgs shows you.")
        Step(3, "That's it", "Your chats show up here, with full RCS.")
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Ink.Green)
                .clickable(role = Role.Button, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Text("Sign in with Google", color = Ink.Text, fontFamily = ArchivoWide, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (showPaste) "Hide manual sign-in" else "Google won't let me sign in",
            color = Ink.Text2,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { showPaste = !showPaste }.padding(8.dp),
        )
        if (showPaste) {
            Text(
                "Sign in at messages.google.com/web on a computer, open the browser's developer tools, and copy the Cookie header from any request to messages.google.com. Paste it here.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Ink.Text3,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.Raised)
                    .padding(14.dp),
            ) {
                BasicTextField(
                    value = pasted,
                    onValueChange = { pasted = it; pasteError = null },
                    textStyle = TextStyle(fontFamily = Archivo, fontSize = 13.sp, color = Ink.Text),
                    cursorBrush = SolidColor(Ink.GreenText),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (pasted.isEmpty()) Text("SID=…; HSID=…; SSID=…", fontSize = 13.sp, color = Ink.Text3)
                            inner()
                        }
                    },
                )
            }
            pasteError?.let { Text(it, color = Ink.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(10.dp))
            PillButton("Pair with these cookies", accent = pasted.isNotBlank()) {
                val json = cookiesFromText(pasted)
                val missing = Gmbridge.missingCookies(json)
                if (missing.isNotEmpty()) pasteError = "Missing: ${missing.replace(",", ", ")}" else onPaste(json)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Ink.Raised), contentAlignment = Alignment.Center) {
            Text(n.toString(), fontFamily = ArchivoWide, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Ink.GreenText)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontFamily = ArchivoWide, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = Ink.Text2)
        }
    }
}

/** Turns a pasted Cookie header ("a=b; c=d") or a JSON object into a JSON object string. */
private fun cookiesFromText(text: String): String {
    val trimmed = text.trim().removePrefix("Cookie:").removePrefix("cookie:").trim()
    if (trimmed.startsWith("{")) return runCatching { JSONObject(trimmed).toString() }.getOrDefault("{}")
    val o = JSONObject()
    trimmed.split(';').map { it.trim() }.filter { it.contains('=') }.forEach {
        val i = it.indexOf('=')
        o.put(it.substring(0, i).trim(), it.substring(i + 1).trim())
    }
    return o.toString()
}

private fun cookiesFor(url: String): String = cookiesFromText(CookieManager.getInstance().getCookie(url).orEmpty())

private fun chromeLikeUserAgent(context: Context): String =
    WebSettings.getDefaultUserAgent(context)
        .replace("; wv", "")
        .replace(Regex("Version/[\\d.]+ "), "")

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GoogleSignIn(onCookies: (String) -> Unit, onCancel: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    val done = remember { booleanArrayOf(false, false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(MsgsIcons.Back, "Back", onCancel)
            Spacer(Modifier.width(12.dp))
            Text("Sign in to Google", fontFamily = ArchivoWide, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Ink.Text2)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = chromeLikeUserAgent(ctx)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                        }
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (done[0] || url == null || !url.startsWith("https://messages.google.com/")) return
                                cookies.flush()
                                val json = cookiesFor("https://messages.google.com/web/config")
                                val missing = Gmbridge.missingCookies(json)
                                when {
                                    missing.isEmpty() -> {
                                        done[0] = true
                                        onCookies(json)
                                    }
                                    missing == "OSID" && !done[1] -> {
                                        done[1] = true
                                        view?.loadUrl("https://messages.google.com/web/authentication")
                                    }
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                            }
                        }
                        loadUrl(SIGN_IN_URL)
                    }
                },
            )
        }
    }
}

@Composable
private fun EmojiStep(emoji: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (emoji.isEmpty()) {
            CircularProgressIndicator(color = Ink.GreenText)
            Spacer(Modifier.height(20.dp))
            Text("Connecting to your Google account…", fontSize = 16.sp, color = Ink.Text2)
        } else {
            Box(Modifier.size(150.dp).clip(CircleShape).background(Ink.Raised), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 84.sp)
            }
            Spacer(Modifier.height(28.dp))
            Text("Tap this emoji in Google Messages", fontFamily = ArchivoWide, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Google Messages on this phone is asking you to confirm. Open it, pick the same emoji, and come back here.",
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Ink.Text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PillButton("Open Google Messages", accent = true) {
                context.packageManager.getLaunchIntentForPackage(Notifications.GOOGLE_MESSAGES)?.let { context.startActivity(it) }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Ink.Text2)
                Spacer(Modifier.width(10.dp))
                Text("Waiting for you to tap it…", fontSize = 14.sp, color = Ink.Text3)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Cancel", color = Ink.Text2, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onCancel).padding(10.dp))
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pairing didn't finish", fontFamily = ArchivoWide, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(message, fontSize = 15.sp, lineHeight = 22.sp, color = Ink.Text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PillButton("Try again", accent = true, onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        Text("Back", color = Ink.Text2, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
    }
}

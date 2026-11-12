package com.msgs.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.msgs.app.engine.Engine
import com.msgs.app.ui.theme.Ink
import com.msgs.app.ui.theme.SansCondensed
import kotlinx.coroutines.CancellationException

/**
 * R4 pairing: QR code only. Msgs never sees a Google password or Google account
 * cookies. The Fold can't scan its own screen, so the code is shown on a second
 * screen (Samsung DeX or screen mirroring) and scanned with Google Messages.
 */
@Composable
fun PairingScreen(onDiagnostics: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    when (step) {
        0 -> Intro(onStart = { step = 1 }, onDiagnostics = onDiagnostics)
        1 -> QrStep(
            onCancel = { step = 0 },
            onFailed = { error = it; step = 2 },
        )
        else -> Failed(error, onRetry = { step = 1 }, onBack = { step = 0 }, onDiagnostics = onDiagnostics)
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) = Text(
    text,
    fontFamily = SansCondensed,
    fontWeight = FontWeight.SemiBold,
    fontSize = 40.sp,
    lineHeight = 42.sp,
    color = Ink.Text,
)

@Composable
private fun Body(text: String, color: androidx.compose.ui.graphics.Color = Ink.Text2) =
    Text(text, fontSize = 16.sp, lineHeight = 23.sp, color = color)

@Composable
private fun Intro(onStart: () -> Unit, onDiagnostics: () -> Unit) = Page {
    Title("Pair by QR code")
    Body("Msgs connects to Google Messages the way Messages for web does. Google Messages stays your default texting app. Msgs never asks for your Google password.")
    Body("You'll need a second screen, because the Fold can't scan its own display:", Ink.Text)
    Body("1. Connect the Fold to a monitor or PC with Samsung DeX (USB-C cable) or Smart View, so this screen is mirrored.")
    Body("2. Tap Show QR code below.")
    Body("3. Open Google Messages, tap your profile picture, then Device pairing, then QR code scanner, and scan the code on the other screen.")
    Body("Don't photograph or screenshot the code. It's single-use and changes every 30 seconds.", Ink.Text3)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Show QR code", accent = true, onClick = onStart)
        PillButton("Diagnostics", onClick = onDiagnostics)
    }
}

@Composable
private fun QrStep(onCancel: () -> Unit, onFailed: (String) -> Unit) {
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var note by remember { mutableStateOf("Getting a code from Google…") }
    var refreshes by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { Engine.cancelPairing() } }
    LaunchedEffect(Unit) {
        try {
            qr = qrBitmap(Engine.startQrPairing())
            note = "Waiting for Google Messages to scan the code…"
            while (true) {
                if (Engine.waitForPairing(28_000)) {
                    note = "Paired. Connecting…"
                    return@LaunchedEffect
                }
                if (++refreshes > 10) {
                    onFailed("The code wasn't scanned after about five minutes. Start again when you're ready.")
                    return@LaunchedEffect
                }
                qr = qrBitmap(Engine.refreshQr())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailed(e.message?.substringAfterLast("Exception: ")?.replaceFirstChar { it.uppercase() } ?: "Pairing failed")
        }
    }
    Page {
        Title("Scan this code")
        Body("Google Messages › profile picture › Device pairing › QR code scanner")
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .size(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(14.dp)
                    .semantics { contentDescription = "Pairing QR code" },
                contentAlignment = Alignment.Center,
            ) {
                qr?.let { Image(it, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
            }
        }
        Body(note, Ink.Text)
        if (refreshes > 0) Body("New code issued ($refreshes). Older codes no longer work.", Ink.Text3)
        PillButton("Cancel", onClick = onCancel)
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit, onBack: () -> Unit, onDiagnostics: () -> Unit) = Page {
    Title("Pairing didn't finish")
    Body(message, Ink.Danger)
    Body("Nothing was saved. You can try again; Google may slow down repeated attempts.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Try again", accent = true, onClick = onRetry)
        PillButton("Back", onClick = onBack)
        PillButton("Diagnostics", onClick = onDiagnostics)
    }
}

private fun qrBitmap(contents: String): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L)
    val m = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, 0, 0, hints)
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        bmp.setPixel(x, y, if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }
    return bmp.asImageBitmap()
}

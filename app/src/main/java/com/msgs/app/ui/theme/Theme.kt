@file:OptIn(ExperimentalTextApi::class)

package com.msgs.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.msgs.app.R

/**
 * Blackout: true OLED black, white type, and racing green for you and anything new.
 * Every color in the app comes from here.
 */
object Ink {
    val Bg = Color(0xFF000000)
    val Raised = Color(0xFF161817)
    val Raised2 = Color(0xFF1F2220)
    val Mono = Color(0xFF24282A)
    val Line = Color(0xFF1E2120)
    val Line2 = Color(0xFF2A2D2B)
    val Text = Color(0xFFFFFFFF)
    val Text2 = Color(0xFFA2A9A4)
    val Text3 = Color(0xFF7E8580)
    val Unread = Color(0xFFE2E6E3)
    val Green = Color(0xFF0B7A4B)
    val GreenText = Color(0xFF2FAE6F)
    val Danger = Color(0xFFFF6B5E)
    val Warn = Color(0xFFF2C14E)
}

/** Colors you can pick for your own bubbles, app-wide or per chat. */
data class BubbleColor(val name: String, val color: Color)

val BubbleColors = listOf(
    BubbleColor("Racing green", Color(0xFF0B7A4B)),
    BubbleColor("Cobalt", Color(0xFF1F4FB8)),
    BubbleColor("Oxblood", Color(0xFF8E2A2A)),
    BubbleColor("Gunmetal", Color(0xFF3D434A)),
)

fun bubbleColor(name: String?): Color = BubbleColors.firstOrNull { it.name == name }?.color ?: Ink.Green

private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Body and interface text. */
val Archivo = FontFamily(
    archivo(400, 100f),
    archivo(500, 100f),
    archivo(600, 100f),
    archivo(700, 100f),
    archivo(800, 100f),
)

/** Names and buttons: a touch wider, so they read as labels. */
val ArchivoWide = FontFamily(
    archivo(600, 112f),
    archivo(700, 112f),
    archivo(800, 112f),
)

/** Screen titles: fully expanded and heavy. */
val ArchivoExpanded = FontFamily(
    archivo(700, 125f),
    archivo(800, 125f),
)

private fun TextStyle.archivo() = copy(fontFamily = Archivo)

@Composable
fun MsgsTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Ink.Green,
        onPrimary = Ink.Text,
        secondary = Ink.GreenText,
        onSecondary = Ink.Bg,
        background = Ink.Bg,
        onBackground = Ink.Text,
        surface = Ink.Raised,
        onSurface = Ink.Text,
        surfaceVariant = Ink.Raised2,
        onSurfaceVariant = Ink.Text2,
        surfaceContainerLowest = Ink.Bg,
        surfaceContainerLow = Ink.Raised,
        surfaceContainer = Ink.Raised,
        surfaceContainerHigh = Ink.Raised2,
        surfaceContainerHighest = Ink.Raised2,
        outline = Ink.Line2,
        outlineVariant = Ink.Line,
        error = Ink.Danger,
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.archivo(),
        displayMedium = base.displayMedium.archivo(),
        displaySmall = base.displaySmall.archivo(),
        headlineLarge = base.headlineLarge.archivo(),
        headlineMedium = base.headlineMedium.archivo(),
        headlineSmall = base.headlineSmall.archivo(),
        titleLarge = base.titleLarge.archivo(),
        titleMedium = base.titleMedium.archivo(),
        titleSmall = base.titleSmall.archivo(),
        bodyLarge = base.bodyLarge.archivo(),
        bodyMedium = base.bodyMedium.archivo(),
        bodySmall = base.bodySmall.archivo(),
        labelLarge = base.labelLarge.archivo(),
        labelMedium = base.labelMedium.archivo(),
        labelSmall = base.labelSmall.archivo(),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        ProvideTextStyle(TextStyle(fontFamily = Archivo, color = Ink.Text, fontSize = 16.sp), content)
    }
}

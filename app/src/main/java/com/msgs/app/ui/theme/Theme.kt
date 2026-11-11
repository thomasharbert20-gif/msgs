@file:OptIn(ExperimentalTextApi::class)

package com.msgs.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.msgs.app.R

/**
 * Loden: olive-charcoal ground and warm paper type. Hunter green and sage are you,
 * ember is anything new. Every color in the app comes from here.
 */
object Ink {
    /** The page. Never pure black. */
    val Bg = Color(0xFF131410)
    val Raised = Color(0xFF1C1D18)
    val Raised2 = Color(0xFF262721)
    /** Behind a portrait that has no photo. */
    val Mono = Color(0xFF24251F)
    val Line = Color(0xFF2B2C26)
    val Line2 = Color(0xFF3A3B33)
    /** Paper: names and their words. */
    val Text = Color(0xFFECE8DE)
    /** Stone: previews, bylines, glyphs. */
    val Text2 = Color(0xFFA6A397)
    /** Ash: times and receipts. */
    val Text3 = Color(0xFF858378)
    val Unread = Color(0xFFD4D0C5)
    /** Hunter: your keys and the underline on your actions. */
    val Green = Color(0xFF2F5641)
    /** Sage: your words, and live states like typing. */
    val GreenText = Color(0xFF9DB08F)
    /** Ember: new. Nothing else uses it. */
    val Ember = Color(0xFFC9804F)
    val Danger = Color(0xFFDB6E5C)
    val Warn = Color(0xFFC9804F)
}

/** Warm fills for portraits without a photo, picked per person. */
val PortraitTones = listOf(Color(0xFF43372D), Color(0xFF333A3C), Color(0xFF39402F), Color(0xFF2F3B33))

private fun instrument(weight: Int, width: Float) = Font(
    R.font.instrument_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Instrument Sans: names, messages and interface text. */
val Sans = FontFamily(
    instrument(400, 100f),
    instrument(500, 100f),
    instrument(600, 100f),
    instrument(700, 100f),
)

/** Instrument Sans at its narrowest: cover lines, quotes and big names. */
val SansCondensed = FontFamily(
    instrument(500, 75f),
    instrument(600, 75f),
    instrument(700, 75f),
)

/** Instrument Serif Italic: bylines, datelines and captions. Never for body text. */
val Serif = FontFamily(Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Normal))

/**
 * Prints a photo in Loden duotone: shadows go to the olive-charcoal ground, highlights to
 * warm paper, with a little extra contrast. Every face in the app goes through it.
 */
val Duotone: ColorFilter = run {
    val shadow = floatArrayOf(19f, 20f, 16f)
    val high = floatArrayOf(232f, 227f, 214f)
    val contrast = 1.15f
    val bias = 0.5f * (1f - contrast)
    val m = FloatArray(20)
    for (i in 0..2) {
        val d = high[i] - shadow[i]
        val k = d * contrast / 255f
        m[i * 5] = k * 0.299f
        m[i * 5 + 1] = k * 0.587f
        m[i * 5 + 2] = k * 0.114f
        m[i * 5 + 4] = shadow[i] + d * bias
    }
    m[18] = 1f
    ColorFilter.colorMatrix(ColorMatrix(m))
}

private fun TextStyle.sans() = copy(fontFamily = Sans)

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
        displayLarge = base.displayLarge.sans(),
        displayMedium = base.displayMedium.sans(),
        displaySmall = base.displaySmall.sans(),
        headlineLarge = base.headlineLarge.sans(),
        headlineMedium = base.headlineMedium.sans(),
        headlineSmall = base.headlineSmall.sans(),
        titleLarge = base.titleLarge.sans(),
        titleMedium = base.titleMedium.sans(),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(),
        labelMedium = base.labelMedium.sans(),
        labelSmall = base.labelSmall.sans(),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        ProvideTextStyle(TextStyle(fontFamily = Sans, color = Ink.Text, fontSize = 16.sp), content)
    }
}

@file:OptIn(ExperimentalTextApi::class)

package com.msgs.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.R
import com.msgs.app.data.Prefs

/** One ambient color finish. Moss is the default; Ember and Steel are switchable in Settings. */
data class Palette(
    val name: String,
    val top: Color,
    val bottom: Color,
    val glow1: Color,
    val glow2: Color,
    val accent: Color,
    val onAccent: Color,
    val ink: Color,
)

val Moss = Palette(
    name = "Moss",
    top = Color(0xFF1D2719), bottom = Color(0xFF080A08),
    glow1 = Color(0x947C9C5A), glow2 = Color(0x803A5634),
    accent = Color(0xFFA6DA7C), onAccent = Color(0xFF0F1A0B), ink = Color(0xFF070907),
)
val Ember = Palette(
    name = "Ember",
    top = Color(0xFF33241D), bottom = Color(0xFF0C0908),
    glow1 = Color(0x94C48458), glow2 = Color(0x7A704832),
    accent = Color(0xFFFF9150), onAccent = Color(0xFF1C0D04), ink = Color(0xFF0A0706),
)
val Steel = Palette(
    name = "Steel",
    top = Color(0xFF1A222D), bottom = Color(0xFF08090C),
    glow1 = Color(0x856C8EBE), glow2 = Color(0x7A364864),
    accent = Color(0xFF90BAFF), onAccent = Color(0xFF081326), ink = Color(0xFF06080B),
)
val Palettes = listOf(Moss, Ember, Steel)

val LocalPalette = staticCompositionLocalOf { Moss }

val TextPrimary = Color(0xFFF3F5EF)
val TextSecondary = Color(0x9EF3F5EF)
val TextTertiary = Color(0x73F3F5EF)
val Danger = Color(0xFFFF8A80)

/** Body and interface text. */
val Geist = FontFamily(
    Font(R.font.geist, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.geist, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.geist, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.geist, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** The big "Messages" title. */
val TitleFont = FontFamily(Font(R.font.anton))

/** Contact and group names. Swap this one line to change the name font everywhere. */
val NameFont = FontFamily(Font(R.font.kanit_bold_italic, FontWeight.Bold, FontStyle.Italic))

fun nameStyle(size: Int) = TextStyle(
    fontFamily = NameFont,
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    fontSize = size.sp,
    color = TextPrimary,
)

/** Frosted glass: a light translucent fill with a hairline edge. */
fun Modifier.glass(shape: Shape, fill: Float = 0.09f, edge: Float = 0.12f): Modifier =
    this
        .clip(shape)
        .background(Color.White.copy(alpha = fill), shape)
        .border(1.dp, Color.White.copy(alpha = edge), shape)

/** Darker glass for bars that float over content. */
fun Modifier.barGlass(shape: Shape): Modifier =
    this
        .clip(shape)
        .background(Color(0xCC1E241B), shape)
        .border(1.dp, Color.White.copy(alpha = 0.13f), shape)

/** The ambient-light background: a base gradient plus two soft glows. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, variant: Int = 0, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val big = maxOf(w, h)
            val base = Brush.verticalGradient(0f to p.top, 0.8f to p.bottom, 1f to p.bottom)
            val c1 = when (variant) {
                1 -> Offset(w * 0.92f, -h * 0.04f)
                2 -> Offset(w * 0.5f, -h * 0.08f)
                else -> Offset(w * 0.08f, -h * 0.06f)
            }
            val c2 = if (variant == 1) Offset(-w * 0.1f, h * 0.55f) else Offset(w * 1.08f, h * 0.38f)
            val g1 = Brush.radialGradient(listOf(p.glow1, Color.Transparent), center = c1, radius = big * 0.62f)
            val g2 = Brush.radialGradient(listOf(p.glow2, Color.Transparent), center = c2, radius = big * 0.5f)
            onDrawBehind {
                drawRect(base)
                drawRect(g1)
                drawRect(g2)
            }
        },
        content = content,
    )
}

@Composable
fun MsgsTheme(content: @Composable () -> Unit) {
    val name by Prefs.palette.collectAsState()
    val palette = Palettes.firstOrNull { it.name == name } ?: Moss
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        background = palette.bottom,
        onBackground = TextPrimary,
        surface = Color(0xFF161B13),
        onSurface = TextPrimary,
        surfaceVariant = Color(0xFF232A1F),
        onSurfaceVariant = TextSecondary,
        surfaceContainer = Color(0xFF1E241B),
        surfaceContainerHigh = Color(0xFF252C21),
        surfaceContainerHighest = Color(0xFF2C3428),
        outline = Color(0x33FFFFFF),
        error = Danger,
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Geist),
        displayMedium = base.displayMedium.copy(fontFamily = Geist),
        displaySmall = base.displaySmall.copy(fontFamily = Geist),
        headlineLarge = base.headlineLarge.copy(fontFamily = Geist),
        headlineMedium = base.headlineMedium.copy(fontFamily = Geist),
        headlineSmall = base.headlineSmall.copy(fontFamily = Geist),
        titleLarge = base.titleLarge.copy(fontFamily = Geist),
        titleMedium = base.titleMedium.copy(fontFamily = Geist),
        titleSmall = base.titleSmall.copy(fontFamily = Geist),
        bodyLarge = base.bodyLarge.copy(fontFamily = Geist),
        bodyMedium = base.bodyMedium.copy(fontFamily = Geist),
        bodySmall = base.bodySmall.copy(fontFamily = Geist),
        labelLarge = base.labelLarge.copy(fontFamily = Geist),
        labelMedium = base.labelMedium.copy(fontFamily = Geist),
        labelSmall = base.labelSmall.copy(fontFamily = Geist),
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            ProvideTextStyle(TextStyle(fontFamily = Geist, color = TextPrimary, fontSize = 16.sp), content)
        }
    }
}

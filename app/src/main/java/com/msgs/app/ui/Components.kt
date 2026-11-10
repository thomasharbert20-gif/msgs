package com.msgs.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.engine.AvatarRepo
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Participant
import com.msgs.app.ui.theme.Geist
import com.msgs.app.ui.theme.LocalPalette
import com.msgs.app.ui.theme.TextPrimary
import com.msgs.app.ui.theme.glass
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val avatarGradients = listOf(
    Color(0xFFD29374) to Color(0xFF6E3B31),
    Color(0xFF62917D) to Color(0xFF1D3A32),
    Color(0xFF86A271) to Color(0xFF2C432E),
    Color(0xFF939AA6) to Color(0xFF363C46),
    Color(0xFFB0915F) to Color(0xFF4D3B20),
    Color(0xFFBC6B5F) to Color(0xFF4A2420),
    Color(0xFF74869A) to Color(0xFF29323E),
    Color(0xFFA086B6) to Color(0xFF3B2E4E),
    Color(0xFF6294AE) to Color(0xFF1F3B4B),
    Color(0xFFCFA77E) to Color(0xFF67472A),
)

fun gradientFor(seed: String): Brush {
    val (a, b) = avatarGradients[(seed.hashCode() and 0x7fffffff) % avatarGradients.size]
    return Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset.Infinite)
}

fun initials(name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) return "?"
    if (clean.first().isDigit() || clean.first() == '+') return "#"
    val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetter() }
    return when {
        words.isEmpty() -> clean.take(1).uppercase()
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** A contact or group picture, falling back to initials on a gradient. */
@Composable
fun Avatar(
    conv: Conversation?,
    size: Dp,
    modifier: Modifier = Modifier,
    person: Participant? = null,
    shape: Shape = CircleShape,
) {
    val key = remember(conv?.id, conv?.groupAvatarUrl, person?.id) { AvatarRepo.keyFor(conv, person) }
    val version by AvatarRepo.version.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, key, version) { value = AvatarRepo.load(key) }
    val label = person?.displayName ?: conv?.title ?: "?"
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(gradientFor(label)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                initials(label),
                color = Color.White,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 21.dp,
    tint: Color = TextPrimary,
) {
    Box(
        modifier
            .size(size)
            .glass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun AccentIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) p.accent else p.accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun CountBadge(count: String, modifier: Modifier = Modifier, ring: Color? = null) {
    val p = LocalPalette.current
    Box(
        modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .then(if (ring != null) Modifier.border(2.dp, ring, RoundedCornerShape(11.dp)) else Modifier)
            .clip(RoundedCornerShape(11.dp))
            .background(p.accent)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count, color = p.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Geist)
    }
}

/** "8:41 AM", "Yesterday", "Sunday", or a short date. */
fun inboxTime(context: Context, ts: Long): String {
    if (ts <= 0) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val days = daysBetween(then, now)
    return when {
        days == 0 -> android.text.format.DateFormat.getTimeFormat(context).format(Date(ts))
        days == 1 -> "Yesterday"
        days in 2..6 -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(Date(ts))
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts))
    }
}

/** The separator shown between message groups: "Today 8:16 AM", "Yesterday 6:02 PM", "Mon, Nov 3 9:12 PM". */
fun chatDayLabel(context: Context, ts: Long): Pair<String, String> {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(ts))
    val days = daysBetween(then, now)
    val day = when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days in 2..6 -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(Date(ts))
        else -> java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(Date(ts))
    }
    return day to time
}

fun timeOnly(context: Context, ts: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date(ts))

private fun daysBetween(a: Calendar, b: Calendar): Int {
    val x = (a.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val y = (b.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return ((y.timeInMillis - x.timeInMillis) / 86_400_000L).toInt()
}

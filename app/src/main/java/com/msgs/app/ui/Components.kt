package com.msgs.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msgs.app.engine.AvatarRepo
import com.msgs.app.engine.Conversation
import com.msgs.app.engine.Engine
import com.msgs.app.engine.Participant
import com.msgs.app.ui.theme.Duotone
import com.msgs.app.ui.theme.Ink
import com.msgs.app.ui.theme.PortraitTones
import com.msgs.app.ui.theme.Sans
import com.msgs.app.ui.theme.SansCondensed
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin

/** The corner every portrait in Loden shares: a print with barely-softened corners. */
val PortraitShape = RoundedCornerShape(3.dp)

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

private fun looksLikeNumber(label: String): Boolean {
    val t = label.trim()
    return t.isNotEmpty() && (t.first().isDigit() || t.first() == '+')
}

/** "Mike" for Mike Russo; a group keeps its name; a bare number stays a number. */
fun shortName(c: Conversation): String {
    if (c.isGroup) return c.title
    val p = c.other ?: return c.title
    return p.first.ifBlank { if (looksLikeNumber(c.title)) c.title else c.title.substringBefore(' ') }
}

/** A participant's first name for bylines and the transcript margin. */
fun firstNameOf(p: Participant?, fallback: String): String {
    if (p == null) return fallback.substringBefore(' ').ifBlank { fallback }
    return p.first.ifBlank { p.displayName.substringBefore(' ') }.ifBlank { fallback }
}

/** True when a group has no picture of its own, so it shows two members' photos instead. */
@Composable
fun rememberCollage(conv: Conversation?): Boolean {
    val version by AvatarRepo.version.collectAsState()
    return remember(conv?.id, conv?.groupAvatarUrl, conv?.participants?.size, version) {
        conv != null && conv.isGroup && conv.groupAvatarUrl.isBlank() && conv.others.size >= 2 &&
            !AvatarRepo.hasCustom(AvatarRepo.keyFor(conv))
    }
}

/**
 * A portrait that fills the box [modifier] gives it, printed in Loden duotone. With no photo
 * it shows the initial on a warm tone, or a person glyph for a bare number.
 */
@Composable
fun AvatarFill(
    conv: Conversation?,
    modifier: Modifier,
    person: Participant? = null,
    initialsSize: TextUnit = 20.sp,
    duotone: Boolean = true,
) {
    val key = remember(conv?.id, conv?.groupAvatarUrl, person?.id) { AvatarRepo.keyFor(conv, person) }
    val version by AvatarRepo.version.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, key, version) { value = AvatarRepo.load(key) }
    val label = person?.displayName ?: conv?.title ?: "?"
    val tone = remember(label) { PortraitTones[(label.hashCode() and 0x7fffffff) % PortraitTones.size] }
    val b = bitmap
    Box(modifier.background(if (b != null) Ink.Mono else tone), contentAlignment = Alignment.Center) {
        when {
            b != null -> Image(
                b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (duotone) Duotone else null,
                modifier = Modifier.fillMaxSize(),
            )
            looksLikeNumber(label) -> Icon(MsgsIcons.Person, contentDescription = null, tint = Ink.Text2, modifier = Modifier.fillMaxSize(0.46f))
            else -> Text(
                initials(label).take(1),
                color = Ink.Text,
                fontFamily = SansCondensed,
                fontWeight = FontWeight.SemiBold,
                fontSize = initialsSize,
                maxLines = 1,
            )
        }
    }
}

/** A square portrait. Groups without a picture show two members, overlapped. */
@Composable
fun Avatar(
    conv: Conversation?,
    size: Dp,
    modifier: Modifier = Modifier,
    person: Participant? = null,
    shape: Shape = PortraitShape,
    ring: Color = Ink.Bg,
) {
    val groupWithoutPhoto = rememberCollage(conv)
    if (person == null && groupWithoutPhoto && conv != null) {
        val others = conv.others
        Box(modifier.size(size)) {
            AvatarFill(
                conv,
                Modifier.align(Alignment.TopStart).size(size * 0.68f).clip(shape),
                person = others[0],
                initialsSize = (size.value * 0.3f).sp,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.56f)
                    .clip(shape)
                    .background(ring)
                    .padding(size * 0.05f),
            ) {
                AvatarFill(conv, Modifier.fillMaxSize().clip(shape), person = others[1], initialsSize = (size.value * 0.24f).sp)
            }
        }
    } else {
        AvatarFill(conv, modifier.size(size).clip(shape), person = person, initialsSize = (size.value * 0.44f).sp)
    }
}

/** The small ember square that marks something new. */
@Composable
fun EmberMark(modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Ink.Ember)
            .semantics { contentDescription = "New" },
    )
}

/**
 * Loden's action: a word with a hunter underline, like a marked-up proof. Used for Write,
 * Reply and Send instead of filled buttons.
 */
@Composable
fun InkLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 16.sp,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = if (enabled) Ink.Text else Ink.Text3,
            modifier = Modifier.drawBehind {
                val y = size.height + 4.dp.toPx()
                drawLine(
                    color = if (enabled) Ink.Green else Ink.Line2,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                )
            },
        )
    }
}

/** A plain word action, quieter than [InkLink]: Cancel, Discard, Call. */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink.Text,
    fontSize: TextUnit = 15.sp,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = fontSize, color = color)
    }
}

/** The way back: a chevron and where it goes, in stone. A small ember square means news there. */
@Composable
fun BackLink(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    news: Int = 0,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(if (label != null) "Back to $label" else "Back")
                    if (news > 0) append(", $news unread")
                }
            }
            .padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MsgsIcons.Back, contentDescription = null, tint = Ink.Text2, modifier = Modifier.size(22.dp))
        if (label != null) {
            Spacer(Modifier.width(2.dp))
            Text(label, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink.Text2, maxLines = 1)
        }
        if (news > 0) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(Ink.Ember))
        }
    }
}

/** A one-pixel rule in the page's line color. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Ink.Line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A bare glyph with a full-size touch target. Only keys get a fill in Loden. */
@Composable
fun GlyphButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Text2,
    iconSize: Dp = 21.dp,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A solid round icon button, kept for the secondary screens. */
@Composable
fun CircleIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 21.dp,
    background: Color = Ink.Raised,
    tint: Color = Ink.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PillButton(label: String, accent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) Ink.Green else Ink.Raised2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
    }
}

/** The quiet panel settings sit in. */
fun Modifier.panel(): Modifier = this.clip(RoundedCornerShape(18.dp)).background(Ink.Raised)

@Composable
fun SettingRow(
    icon: ImageVector,
    label: String,
    color: Color = Ink.Text,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = color, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.padding(start = 51.dp).fillMaxWidth().height(1.dp).background(Ink.Line2))
}

@Composable
fun DangerRow(icon: ImageVector, label: String, onClick: () -> Unit) = SettingRow(icon, label, color = Ink.Danger, onClick = onClick)

/** A voice message's bars. The shape is fixed per message; bars light up as it plays. */
@Composable
fun Waveform(seed: String, progress: Float, color: Color, modifier: Modifier = Modifier) {
    val bars = remember(seed) { waveBars(seed, 28) }
    Canvas(modifier) {
        val step = size.width / bars.size
        val barW = step * 0.42f
        bars.forEachIndexed { i, h ->
            val barH = size.height * h
            val played = (i + 0.5f) / bars.size <= progress
            drawRoundRect(
                color = color.copy(alpha = if (played) 1f else 0.35f),
                topLeft = Offset(i * step, (size.height - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

private fun waveBars(seed: String, n: Int): List<Float> {
    val r = java.util.Random(seed.hashCode().toLong())
    return List(n) { i ->
        val envelope = 0.55f + 0.45f * sin(i / (n - 1f) * PI).toFloat()
        ((0.25f + 0.75f * r.nextFloat()) * envelope).coerceIn(0.2f, 1f)
    }
}

/** "0:07", "1:42". */
fun formatDuration(ms: Int): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Ids of the chats where someone is typing right now. Updates when an indicator runs out. */
@Composable
fun rememberTypingIds(): Set<String> {
    val typing by Engine.typing.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val now = remember(typing, tick) { System.currentTimeMillis() }
    LaunchedEffect(typing, tick) {
        val next = typing.values.flatMap { it.values }.filter { it > System.currentTimeMillis() }.minOrNull()
            ?: return@LaunchedEffect
        delay(next - System.currentTimeMillis() + 50)
        tick++
    }
    return remember(typing, now) { typing.filterValues { m -> m.values.any { it > now } }.keys }
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

/** The margin date for a day break: "Today", "Yesterday", "Sunday", "3 November". */
fun dayName(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val days = daysBetween(then, now)
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days in 2..6 -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(Date(ts))
        then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault()).format(Date(ts))
        else -> java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault()).format(Date(ts))
    }
}

fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

/** The separator shown between message groups: "Today" and the time. */
fun chatDayLabel(context: Context, ts: Long): Pair<String, String> =
    dayName(ts) to android.text.format.DateFormat.getTimeFormat(context).format(Date(ts))

fun timeOnly(context: Context, ts: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date(ts))

private fun daysBetween(a: Calendar, b: Calendar): Int {
    val x = (a.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val y = (b.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return ((y.timeInMillis - x.timeInMillis) / 86_400_000L).toInt()
}

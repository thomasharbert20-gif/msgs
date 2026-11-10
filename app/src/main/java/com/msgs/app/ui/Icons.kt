package com.msgs.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Rounded outline icons, drawn from the same paths as the design mockups. */
object MsgsIcons {
    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun filled(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { b.addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        return b.build()
    }

    private const val CIRCLE_R2 = "a2 2 0 1 1 -4 0a2 2 0 1 1 4 0z"

    val Back = stroke("back", 2.4f, "M15 5l-7 7 7 7")
    val Chevron = stroke("chevron", 2.2f, "M9 5l7 7 -7 7")
    val Search = stroke("search", 2.2f, "M18 11a7 7 0 1 1 -14 0a7 7 0 1 1 14 0", "M20 20l-3.5 -3.5")
    val Compose = stroke("compose", 2.2f, "M4 20h4L19 9l-4 -4L4 16v4z", "M13.5 6.5l4 4")
    val Plus = stroke("plus", 2.3f, "M12 5v14", "M5 12h14")
    val Send = stroke("send", 2.6f, "M12 19V5", "M6 11l6 -6 6 6")
    val Camera = stroke("camera", 2f, "M4 8h3l2 -3h6l2 3h3v11H4z", "M15.5 13a3.5 3.5 0 1 1 -7 0a3.5 3.5 0 1 1 7 0")
    val Filter = stroke("filter", 2f, "M4 7h16", "M7 12h10", "M10 17h4")
    val Phone = stroke("phone", 2f, "M5 4h4l2 5 -2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1 -1 1A16 16 0 0 1 4 5a1 1 0 0 1 1 -1")
    val Bell = stroke("bell", 2f, "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4")
    val BellOff = stroke("bellOff", 2f, "M6 16V11a6 6 0 0 1 9.6 -4.8", "M18 11v5l2 2H8", "M10 21h4", "M4 4l16 16")
    val Pin = stroke("pin", 2f, "M9 4h6l-1 6 4 3v2H6v-2l4 -3z", "M12 15v6")
    val Image = stroke("image", 2f, "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V8a3 3 0 0 1 3 -3z", "M11 10a2 2 0 1 1 -4 0a2 2 0 1 1 4 0", "M21 16l-5 -5 -8 8")
    val Block = stroke("block", 2f, "M20.5 12a8.5 8.5 0 1 1 -17 0a8.5 8.5 0 1 1 17 0", "M6 6l12 12")
    val DoubleCheck = stroke("doubleCheck", 2.2f, "M2 13l4 4 8 -9", "M10 15l2 2 9 -10")
    val Close = stroke("close", 2.3f, "M6 6l12 12", "M18 6L6 18")
    val Reply = stroke("reply", 2f, "M9 14L4 9l5 -5", "M4 9h10a6 6 0 0 1 6 6v4")
    val Copy = stroke("copy", 2f, "M9 9h11v11H9z", "M5 15H4V4h11v1")
    val Trash = stroke("trash", 2f, "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2 -2l1 -12", "M9 7V4h6v3")
    val Archive = stroke("archive", 2f, "M4 5h16v4H4z", "M6 9v10h12V9", "M10 13h4")
    val Sliders = stroke("sliders", 2f, "M4 6h9", "M17 6h3", "M4 12h3", "M11 12h9", "M4 18h11", "M19 18h1", "M15 4v4", "M9 10v4", "M17 16v4")
    val Refresh = stroke("refresh", 2f, "M20 11a8 8 0 1 0 -2.3 5.7", "M20 4v7h-7")
    val Download = stroke("download", 2f, "M12 4v12", "M6 11l6 6 6 -6", "M5 20h14")
    val Info = stroke("info", 2f, "M21 12a9 9 0 1 1 -18 0a9 9 0 1 1 18 0", "M12 11v6", "M12 7.5v0.5")
    val Play = filled("play", "M8 5.5v13a1 1 0 0 0 1.5 0.9l10 -6.5a1 1 0 0 0 0 -1.8l-10 -6.5A1 1 0 0 0 8 5.5z")
    val File = stroke("file", 2f, "M7 3h7l5 5v13H7z", "M14 3v5h5")
    val Key = stroke("key", 2f, "M10 14a4 4 0 1 1 3.5 -2", "M13.5 12H21v3", "M18 12v3")
    val Unlink = stroke("unlink", 2f, "M9 15l-2 2a3 3 0 0 1 -4 -4l3 -3a3 3 0 0 1 4 0", "M15 9l2 -2a3 3 0 0 1 4 4l-3 3a3 3 0 0 1 -4 0", "M4 4l16 16")
    val More = filled("more", "M7 12$CIRCLE_R2", "M14 12$CIRCLE_R2", "M21 12$CIRCLE_R2")
    val MoreVert = filled("moreVert", "M14 5$CIRCLE_R2", "M14 12$CIRCLE_R2", "M14 19$CIRCLE_R2")
    val Bubble = stroke("bubble", 2f, "M8 5h8a5 5 0 0 1 5 5v1a5 5 0 0 1 -5 5h-4l-5 4v-4a5 5 0 0 1 -4 -5v-1a5 5 0 0 1 5 -5z")
}

package com.cohenjikan.xhslite

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.widget.TextView

/** Same palette and type scale as Claude-Remote ui/Theme.kt, for plain Views. */
data class Palette(
    val bg: Int,
    val fg: Int,
    val muted: Int,
    val line: Int,
    val soft: Int,
    val accent: Int = 0xFFD77757.toInt(),
    val dark: Boolean,
)

val LightPalette = Palette(bg = Color.WHITE, fg = 0xFF0D0D0D.toInt(), muted = 0xFF6E6E6E.toInt(), line = 0xFFE8E8E8.toInt(), soft = 0xFFF3F3F3.toInt(), dark = false)
val DarkPalette = Palette(bg = Color.BLACK, fg = 0xFFF5F5F5.toInt(), muted = 0xFF9A9A9A.toInt(), line = 0xFF232323.toInt(), soft = 0xFF161616.toInt(), dark = true)

fun Context.palette(): Palette =
    if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) DarkPalette else LightPalette

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()

/** sizes in sp, hierarchy from size and gray only (system font, MiSans on HyperOS) */
object Type {
    const val TITLE = 17f
    const val BODY = 15f
    const val SMALL = 13f
    const val TINY = 11f
}

fun Context.text(s: CharSequence, size: Float = Type.BODY, color: Int = palette().fg): TextView = TextView(this).apply {
    text = s
    setTextColor(color)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    lineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size * 1.45f, resources.displayMetrics).toInt()
}

package com.havaduru.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Web sürümündeki renk tokenları. */
class Palette(
    val dark: Boolean,
    val bg: Int, val surface: Int, val surface2: Int, val line: Int,
    val fg: Int, val muted: Int, val accent: Int, val accentSoft: Int,
    val sun: Int, val good: Int, val warn: Int, val bad: Int
) {
    companion object {
        val LIGHT = Palette(false,
            0xFFEDF1F6.toInt(), 0xFFFFFFFF.toInt(), 0xFFF4F7FB.toInt(), 0xFFDCE3EC.toInt(),
            0xFF101B2D.toInt(), 0xFF5C6B80.toInt(), 0xFF2466E0.toInt(), 0xFFDCE8FF.toInt(),
            0xFFE08A0B.toInt(), 0xFF1F9D62.toInt(), 0xFFC98A04.toInt(), 0xFFD4492F.toInt())
        val DARK = Palette(true,
            0xFF0A111E.toInt(), 0xFF111B2D.toInt(), 0xFF172338.toInt(), 0xFF24324A.toInt(),
            0xFFE8EEF7.toInt(), 0xFF93A2B8.toInt(), 0xFF6EA2FF.toInt(), 0xFF1B2D4F.toInt(),
            0xFFF5B041.toInt(), 0xFF4CC38A.toInt(), 0xFFE3B341.toInt(), 0xFFF0705A.toInt())

        fun current(ctx: Context): Palette = when (Prefs.theme) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> if ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
        }
    }
}

/** Bricolage Grotesque (başlıklar) ve Instrument Sans (metin); değişken fontlardan ağırlık seçilir. */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    private fun load(ctx: Context, file: String, axes: String): Typeface = cache.getOrPut("$file|$axes") {
        try {
            Typeface.Builder(ctx.assets, "fonts/$file").setFontVariationSettings(axes).build() ?: Typeface.DEFAULT
        } catch (e: Exception) { Typeface.DEFAULT }
    }

    fun display(ctx: Context, weight: Int = 600): Typeface = load(ctx, "bricolage.ttf", "'wght' $weight, 'opsz' 48")
    fun body(ctx: Context, weight: Int = 400): Typeface = load(ctx, "instrument.ttf", "'wght' $weight")
}

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dpi(v: Number): Int = dp(v.toFloat()).toInt()

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = radius; if (strokeW > 0) setStroke(strokeW, stroke) }

fun withAlpha(c: Int, a: Float): Int = Color.argb((a * 255).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
    (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

/** Kısa görünüm kurucuları. */
class Ui(val ctx: Context, val p: Palette) {
    fun dp(v: Number) = ctx.dpi(v)

    fun text(s: CharSequence = "", size: Float = 15f, color: Int = p.fg, weight: Int = 400, display: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, size); setTextColor(color)
            typeface = if (display) Fonts.display(ctx, weight) else Fonts.body(ctx, weight)
            includeFontPadding = false
        }

    fun label(s: String, color: Int = p.muted): TextView = text(s.uppercase(if (L.lang == "tr") java.util.Locale("tr") else java.util.Locale.ENGLISH), 11.5f, color, 600).apply { letterSpacing = 0.07f }

    fun col(gap: Int = 0, vararg views: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        views.forEachIndexed { i, v -> addView(v, lp(topMargin = if (i > 0) dp(gap) else 0)) }
    }

    fun row(gap: Int = 0, vararg views: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) marginStart = dp(gap) }) }
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, topMargin: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { this.topMargin = topMargin }

    /** Web'deki .panel: yuvarlak köşeli, gölgeli yüzey. */
    fun card(child: View, pad: Int = 20, color: Int = p.surface, radius: Int = 22): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color, ctx.dp(radius.toFloat()))
        elevation = ctx.dp(if (p.dark) 0f else 2f)
        setPadding(dp(pad), dp(pad - 2), dp(pad), dp(pad))
        addView(child, lp())
    }
}

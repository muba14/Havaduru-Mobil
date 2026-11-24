package com.havaduru.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ortak çizim yardımcıları. */
abstract class PView(ctx: Context, protected val p: Palette) : View(ctx) {
    protected val d = resources.displayMetrics.density
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.body(ctx, 400) }
    protected fun dp(v: Float) = v * d
    protected fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, weight: Int = 400, display: Boolean = false) {
        txt.textSize = size * resources.displayMetrics.scaledDensity; txt.color = color; txt.textAlign = align
        txt.typeface = if (display) Fonts.display(context, weight) else Fonts.body(context, weight)
        c.drawText(s, x, y, txt)
    }
    protected fun icon(c: Canvas, res: Int, cx: Float, cy: Float, size: Float) {
        val dr = context.getDrawable(res) ?: return
        val h = size / 2
        dr.setBounds((cx - h).toInt(), (cy - h).toInt(), (cx + h).toInt(), (cy + h).toInt()); dr.draw(c)
    }
}

/** 24 saatlik / 7 günlük sıcaklık grafiği (dokununca değer balonu). */
@SuppressLint("ViewConstructor")
class ChartView(ctx: Context, p: Palette) : PView(ctx, p) {
    var w: Weather? = null; set(v) { field = v; hover = -1; invalidate() }
    var daily = false; set(v) { field = v; hover = -1; invalidate() }
    private var hover = -1

    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(MeasureSpec.getSize(wm), dp(250f).toInt())

    private data class Pt(val t: Long, val a: Double, val b: Double?, val code: Int, val day: Boolean, val pop: Double?)

    private fun pts(): List<Pt> {
        val wx = w ?: return emptyList()
        return if (daily) wx.daily.take(7).map { Pt(it.t, Wx.tc(it.max), Wx.tc(it.min), it.code, true, it.pop) }
        else wx.hourly.map { Pt(it.t, Wx.tc(it.temp), null, it.code, it.day, it.pop) }
    }

    override fun onDraw(c: Canvas) {
        val ps = pts(); if (ps.size < 2) return
        val W = width.toFloat(); val H = height.toFloat()
        val Lx = dp(34f); val R = dp(10f); val T = dp(58f); val B = dp(26f)
        val all = ps.flatMap { listOfNotNull(it.a, it.b) }
        var lo = all.min(); var hi = all.max()
        val step = listOf(1.0, 2.0, 5.0, 10.0, 20.0).firstOrNull { (hi - lo) / it <= 4 } ?: 20.0
        lo = floor((lo - .5) / step) * step; hi = ceil((hi + .5) / step) * step
        val n = ps.size
        fun X(i: Int) = Lx + (W - Lx - R) * i / (n - 1)
        fun Y(v: Double) = (T + (H - T - B) * (1 - (v - lo) / (hi - lo))).toFloat()
        // ızgara
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = p.line
        var v = lo
        while (v <= hi + 1e-9) {
            paint.pathEffect = if (v == lo) null else DashPathEffect(floatArrayOf(dp(3f), dp(5f)), 0f)
            c.drawLine(Lx, Y(v), W - R, Y(v), paint)
            text(c, "${v.roundToInt()}°", Lx - dp(6f), Y(v) + dp(4f), 11f, p.muted, Paint.Align.RIGHT)
            v += step
        }
        paint.pathEffect = null
        fun curve(get: (Pt) -> Double): Path {
            val path = Path(); path.moveTo(X(0), Y(get(ps[0])))
            for (i in 0 until n - 1) {
                val p0 = get(ps[max(0, i - 1)]); val p1 = get(ps[i]); val p2 = get(ps[i + 1]); val p3 = get(ps[min(n - 1, i + 2)])
                val dx = (X(1) - X(0)) / 3
                path.cubicTo(X(i) + dx, Y(p1 + (p2 - p0) / 6), X(i + 1) - dx, Y(p2 - (p3 - p1) / 6), X(i + 1), Y(p2))
            }
            return path
        }
        val mainCol = if (daily) p.sun else p.accent
        val area = curve { it.a }.apply { lineTo(X(n - 1), Y(lo)); lineTo(X(0), Y(lo)); close() }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, T, 0f, H - B, withAlpha(mainCol, .28f), withAlpha(mainCol, 0f), Shader.TileMode.CLAMP)
        c.drawPath(area, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2.6f); paint.strokeCap = Paint.Cap.ROUND
        paint.color = mainCol; c.drawPath(curve { it.a }, paint)
        if (daily) { paint.color = p.accent; c.drawPath(curve { it.b!! }, paint) }
        // etiketler + simgeler
        val every = if (daily) 1 else if (W < dp(520f)) 6 else 3
        for (i in 0 until n) {
            if (i % every != 0) continue
            val x = X(i); val pt = ps[i]
            val align = if (i == 0) Paint.Align.LEFT else if (i == n - 1) Paint.Align.RIGHT else Paint.Align.CENTER
            val lab = if (daily) (if (i == 0) L.s("today") else L.list("days")[Wx.dow(pt.t)]) else (if (i == 0) L.s("now") else Wx.hm(pt.t))
            text(c, lab, x, H - dp(6f), 11f, p.muted, align)
            val ix = x.coerceIn(Lx + dp(10f), W - R - dp(10f))
            icon(c, Wx.iconRes(Wx.kindOf(pt.code), pt.day), ix, dp(16f), dp(22f))
            text(c, "${pt.a.roundToInt()}°", ix, dp(44f), 12f, p.fg, Paint.Align.CENTER, 600)
        }
        // ilk nokta(lar)
        listOfNotNull(ps[0].a to mainCol, ps[0].b?.let { it to p.accent }).forEach { (vv, col) ->
            paint.style = Paint.Style.FILL; paint.color = p.surface; c.drawCircle(X(0), Y(vv), dp(4.5f), paint)
            paint.style = Paint.Style.STROKE; paint.color = col; c.drawCircle(X(0), Y(vv), dp(4.5f), paint)
        }
        // dokunma balonu
        if (hover in 0 until n) {
            val x = X(hover); val pt = ps[hover]
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = p.muted; c.drawLine(x, T - dp(4f), x, H - B, paint)
            paint.style = Paint.Style.FILL; paint.color = mainCol; c.drawCircle(x, Y(pt.a), dp(5f), paint)
            pt.b?.let { paint.color = p.accent; c.drawCircle(x, Y(it), dp(5f), paint) }
            val rain = pt.pop?.let { " · ${L.s("rain")} ${Wx.pct(it.roundToInt())}" } ?: ""
            val s = if (daily) "${L.list("daysL")[Wx.dow(pt.t)]} · ${pt.a.roundToInt()}° / ${pt.b!!.roundToInt()}°$rain" else "${Wx.hm(pt.t)} · ${String.format(java.util.Locale.US, "%.1f", pt.a)}°$rain"
            txt.textSize = 12.5f * resources.displayMetrics.scaledDensity; txt.typeface = Fonts.body(context, 600)
            val tw = txt.measureText(s) + dp(20f); val bx = (x - tw / 2).coerceIn(0f, W - tw)
            paint.color = p.fg; c.drawRoundRect(RectF(bx, T - dp(36f), bx + tw, T - dp(10f)), dp(10f), dp(10f), paint)
            text(c, s, bx + tw / 2, T - dp(18f), 12.5f, p.bg, Paint.Align.CENTER, 600)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val n = pts().size; if (n < 2) return false
        val Lx = dp(34f); val R = dp(10f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(e.actionMasked == MotionEvent.ACTION_MOVE)
                hover = ((e.x - Lx) / (width - Lx - R) * (n - 1)).roundToInt().coerceIn(0, n - 1); invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); postDelayed({ hover = -1; invalidate() }, 2200) }
        }
        return true
    }
}

/** 10 günlük tahmin satırları. */
@SuppressLint("ViewConstructor")
class TenDayView(ctx: Context, p: Palette) : PView(ctx, p) {
    var w: Weather? = null; set(v) { field = v; requestLayout(); invalidate() }
    private val rowH get() = dp(54f)

    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(MeasureSpec.getSize(wm), (rowH * (w?.daily?.size ?: 10)).toInt())

    override fun onDraw(c: Canvas) {
        val wx = w ?: return; val days = wx.daily
        if (days.isEmpty()) return
        val lo = days.minOf { it.min }; val hi = days.maxOf { it.max }; val span = max(1.0, hi - lo)
        val W = width.toFloat(); val narrow = W < dp(420f)
        val cDay = 0f; val cIcon = if (narrow) dp(66f) else dp(118f); val cPop = cIcon + dp(36f)
        val cLo = cPop + dp(if (narrow) 70f else 78f); val cBar = cLo + dp(10f); val cHi = W
        val barEnd = W - dp(44f)
        val sc = Globe.scales.getValue("temp")
        days.forEachIndexed { i, x ->
            val top = i * rowH; val mid = top + rowH / 2
            if (i > 0) { paint.style = Paint.Style.FILL; paint.color = p.line; c.drawRect(0f, top, W, top + dp(1f), paint) }
            val name = if (i == 0) L.s("today") else L.list("days")[Wx.dow(x.t)]
            if (narrow) text(c, name, cDay, mid + dp(5f), 15f, p.fg, weight = 600)
            else { text(c, name, cDay, mid - dp(2f), 15f, p.fg, weight = 600); text(c, Wx.dayMonth(x.t), cDay, mid + dp(14f), 12f, p.muted) }
            icon(c, Wx.iconRes(Wx.kindOf(x.code), true), cIcon + dp(14f), mid, dp(28f))
            val pop = x.pop
            if (pop != null && pop >= 20) {
                icon(c, R.drawable.ic_drop, cPop + dp(6f), mid, dp(11f))
                text(c, Wx.pct(pop.roundToInt()), cPop + dp(14f), mid + dp(4.5f), 13f, p.accent, weight = 600)
            }
            text(c, "${Wx.tc(x.min).roundToInt()}°", cLo, mid + dp(6f), 16.5f, p.muted, Paint.Align.RIGHT, 600, true)
            text(c, "${Wx.tc(x.max).roundToInt()}°", cHi, mid + dp(6f), 16.5f, p.fg, Paint.Align.RIGHT, 600, true)
            val bw = barEnd - cBar
            paint.style = Paint.Style.FILL; paint.color = p.surface2
            c.drawRoundRect(RectF(cBar, mid - dp(3f), barEnd, mid + dp(3f)), dp(3f), dp(3f), paint)
            val a = cBar + ((x.min - lo) / span * bw).toFloat(); val b = cBar + ((x.max - lo) / span * bw).toFloat()
            paint.shader = LinearGradient(a, 0f, b, 0f, sc.color(x.min), sc.color(x.max), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(a, mid - dp(3f), max(b, a + dp(6f)), mid + dp(3f)), dp(3f), dp(3f), paint); paint.shader = null
            if (i == 0) {
                val nx = cBar + ((wx.cur.t - lo) / span * bw).toFloat().coerceIn(0f, bw)
                paint.color = p.surface; c.drawCircle(nx, mid, dp(6f), paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f); paint.color = p.fg; c.drawCircle(nx, mid, dp(4.5f), paint); paint.style = Paint.Style.FILL
            }
        }
    }
}

/** Gün doğumu / batımı yayı. */
@SuppressLint("ViewConstructor")
class SunView(ctx: Context, p: Palette) : PView(ctx, p) {
    var w: Weather? = null; set(v) { field = v; invalidate() }
    override fun onMeasure(wm: Int, hm: Int) { val wd = MeasureSpec.getSize(wm); setMeasuredDimension(wd, (min(wd.toFloat(), dp(420f)) * 162 / 320).toInt()) }

    override fun onDraw(c: Canvas) {
        val wx = w ?: return; val td = wx.daily.firstOrNull() ?: return
        val k = min(width.toFloat(), dp(420f)) / 320f; val ox = (width - 320 * k) / 2
        fun X(v: Float) = ox + v * k
        fun Y(v: Float) = v * k
        val cx = 160f; val cy = 132f; val r = 120f
        val f = ((wx.now - td.sunrise).toDouble() / (td.sunset - td.sunrise)).coerceIn(0.0, 1.0)
        val up = wx.now > td.sunrise && wx.now < td.sunset
        val a = PI * (1 - f); val sx = cx + r * cos(a).toFloat(); val sy = cy - r * sin(a).toFloat()
        val oval = RectF(X(cx - r), Y(cy - r), X(cx + r), Y(cy + r))
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * k; paint.color = p.line; paint.strokeCap = Paint.Cap.ROUND
        paint.pathEffect = DashPathEffect(floatArrayOf(2 * k, 7 * k), 0f); c.drawArc(oval, 180f, 180f, false, paint); paint.pathEffect = null
        if (f > 0) {
            val area = Path().apply { moveTo(X(cx - r), Y(cy)); arcTo(oval, 180f, (180 * f).toFloat()); lineTo(X(sx), Y(cy)); close() }
            paint.style = Paint.Style.FILL; paint.shader = LinearGradient(0f, Y(cy - r), 0f, Y(cy), withAlpha(p.sun, .22f), withAlpha(p.sun, 0f), Shader.TileMode.CLAMP)
            c.drawPath(area, paint); paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * k; paint.color = p.sun; c.drawArc(oval, 180f, (180 * f).toFloat(), false, paint)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * k; paint.color = p.line; c.drawLine(X(16f), Y(cy), X(304f), Y(cy), paint)
        paint.style = Paint.Style.FILL; paint.color = p.muted; c.drawCircle(X(cx - r), Y(cy), 3.5f * k, paint); c.drawCircle(X(cx + r), Y(cy), 3.5f * k, paint)
        if (up) { paint.color = withAlpha(p.sun, .22f); c.drawCircle(X(sx), Y(sy), 15 * k, paint); paint.color = p.sun; c.drawCircle(X(sx), Y(sy), 8.5f * k, paint) }
        text(c, Wx.hm(td.sunrise), X(cx - r), Y(cy + 20), 11f, p.muted, Paint.Align.CENTER)
        text(c, Wx.hm(td.sunset), X(cx + r), Y(cy + 20), 11f, p.muted, Paint.Align.CENTER)
        text(c, L.s("horizon"), X(cx), Y(cy + 20), 11f, p.muted, Paint.Align.CENTER)
    }
}

/** Ay evresi çizimi (büyük ay ya da 8 evre şeridi). */
@SuppressLint("ViewConstructor")
class MoonView(ctx: Context, p: Palette, private val phaseRow: Boolean) : PView(ctx, p) {
    var phase = 0.0; set(v) { field = v; invalidate() }

    override fun onMeasure(wm: Int, hm: Int) = if (phaseRow) setMeasuredDimension(MeasureSpec.getSize(wm), dp(52f).toInt()) else setMeasuredDimension(dp(104f).toInt(), dp(104f).toInt())

    fun drawMoon(c: Canvas, cx: Float, cy: Float, r: Float, ph: Double, lit: Int) {
        paint.style = Paint.Style.FILL; paint.color = p.surface2; c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = p.line; c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL; paint.color = lit
        if (ph < .012 || ph > .988) return
        if (abs(ph - .5) < .012) { c.drawCircle(cx, cy, r, paint); return }
        val rx = (abs(cos(2 * PI * ph)) * r).toFloat()
        val wax = ph < .5; val crescent = ph < .25 || ph > .75
        val path = Path()
        path.moveTo(cx, cy - r)
        path.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, if (wax) 180f else -180f)
        path.arcTo(RectF(cx - rx, cy - r, cx + rx, cy + r), 90f, if (wax == crescent) -180f else 180f)
        path.close(); c.drawPath(path, paint)
    }

    override fun onDraw(c: Canvas) {
        if (!phaseRow) { drawMoon(c, width / 2f, height / 2f, width / 2f - dp(1.5f), phase, p.sun); return }
        val idx = (phase * 8).roundToInt() % 8
        val names = L.list("moonS"); val cw = width / 8f
        for (i in 0 until 8) {
            val cx = cw * i + cw / 2; val on = i == idx
            drawMoon(c, cx, dp(14f), dp(12f), i / 8.0, if (on) p.sun else withAlpha(p.muted, .55f))
            text(c, names.getOrElse(i) { "" }, cx, dp(44f), 10.5f, if (on) p.fg else withAlpha(p.muted, .7f), Paint.Align.CENTER, if (on) 600 else 400)
        }
    }
}

/** İnce seviye çubuğu (nem, yağış ihtimali). */
@SuppressLint("ViewConstructor")
class MeterView(ctx: Context, p: Palette) : PView(ctx, p) {
    var value = 0f; set(v) { field = v; invalidate() }
    var color = p.accent
    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(MeasureSpec.getSize(wm), dp(5f).toInt())
    override fun onDraw(c: Canvas) {
        paint.color = p.surface2; c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), height / 2f, height / 2f, paint)
        paint.color = color; c.drawRoundRect(RectF(0f, 0f, width * value.coerceIn(0f, 1f), height.toFloat()), height / 2f, height / 2f, paint)
    }
}

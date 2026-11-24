package com.havaduru.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Hareketli gökyüzü sahnesi: güneş, ay, bulut, yağmur, kar, sis, şimşek. Uygulama, widget ve paylaşım kartı aynı kodu kullanır. */
class SkyScene {
    class Cloud(var x: Float, val y: Float, val s: Float, val v: Float, val a: Float)
    class Drop(var x: Float, var y: Float, val l: Float, val v: Float, val ph: Float)
    class Star(val x: Float, val y: Float, val r: Float, val ph: Float)

    var kind = Kind.CLEAR; private set
    var day = true; private set
    private var clouds = listOf<Cloud>()
    private var drops = listOf<Drop>()
    private var stars = listOf<Star>()
    private var flash = 0f
    private var next = 1.5f
    private var bolt: List<FloatArray>? = null
    private var t = 0f
    private var built = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun set(k: Kind, d: Boolean): Boolean {
        if (built && k == kind && d == day) return false
        built = true; kind = k; day = d; flash = 0f; next = 1f; bolt = null
        val r = Demo.Rng(k.name + d)
        val nC = when (k) { Kind.CLEAR -> 1; Kind.PARTLY -> 4; Kind.CLOUDY -> 8; Kind.FOG -> 5; Kind.RAIN -> 8; Kind.SNOW -> 7; Kind.STORM -> 9 }
        clouds = List(nC) { Cloud((.3 + r.next() * .8).toFloat(), (.08 + r.next() * (if (k == Kind.PARTLY) .3 else .42)).toFloat(), (.6 + r.next() * .8).toFloat(), (.004 + r.next() * .008).toFloat(), if (k == Kind.CLEAR) .35f else (.55 + r.next() * .4).toFloat()) }
        val nD = when (k) { Kind.RAIN -> 110; Kind.STORM -> 150; Kind.SNOW -> 90; else -> 0 }
        drops = List(nD) { Drop(r.next().toFloat(), r.next().toFloat(), (.6 + r.next() * .8).toFloat(), (.7 + r.next() * .6).toFloat(), (r.next() * 6.28).toFloat()) }
        stars = List(70) { Star(r.next().toFloat(), (r.next() * .8).toFloat(), (.4 + r.next() * 1.2).toFloat(), (r.next() * 6.28).toFloat()) }
        return true
    }

    /** [mul]: küçük ekranda 1, büyük çıktıda (paylaşım kartı) ölçek. [still]: tek kare (widget, kart). */
    fun draw(c: Canvas, w: Float, h: Float, dtIn: Float, mul: Float = 1f, still: Boolean = false, density: Float = 1f) {
        val dt = if (still) 0f else dtIn
        t += dt
        val m = mul * density
        val col = (if (day) DAY else NIGHT).getValue(kind)
        paint.shader = LinearGradient(0f, 0f, 0f, h, col[0], col[1], Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL; paint.alpha = 255
        c.drawRect(0f, 0f, w, h, paint); paint.shader = null

        val sx = w * .8f; val sy = h * .3f; val sr = min(h * .17f, 70f * density) * mul
        if (!day && (kind == Kind.CLEAR || kind == Kind.PARTLY)) {
            paint.color = 0xFFFFFFFF.toInt()
            stars.forEach { s -> paint.alpha = ((.35f + .5f * abs(sin(t * .8f + s.ph))) * 255).toInt(); c.drawCircle(s.x * w, s.y * h, s.r * m, paint) }
            paint.alpha = 255
        }
        if (kind == Kind.CLEAR || kind == Kind.PARTLY) {
            val pul = 1 + .04f * sin(t * 1.2f)
            paint.shader = if (day) RadialGradient(sx, sy, sr * 3.2f * pul, intArrayOf(0xF2FFECAA.toInt(), 0x73FFD66E, 0x00FFD66E), floatArrayOf(.1f, .33f, 1f), Shader.TileMode.CLAMP)
            else RadialGradient(sx, sy, sr * 3.2f * pul, intArrayOf(0x80DCE6FF.toInt(), 0x00DCE6FF), floatArrayOf(.1f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint); paint.shader = null
            if (day) {
                paint.color = 0xFFFFD766.toInt(); c.drawCircle(sx, sy, sr, paint)
                paint.color = 0xFFFFF3C4.toInt(); c.drawCircle(sx, sy, sr * .72f, paint)
            } else {
                paint.color = 0xFFEEF2FF.toInt()
                val a = Path().apply { addCircle(sx, sy, sr, Path.Direction.CW) }
                val b = Path().apply { addCircle(sx + sr * .42f, sy - sr * .22f, sr * .86f, Path.Direction.CW) }
                a.op(b, Path.Op.DIFFERENCE); c.drawPath(a, paint)
            }
        }
        val dark = kind == Kind.RAIN || kind == Kind.STORM
        clouds.forEach { cl ->
            cl.x += cl.v * dt; if (cl.x > 1.25f) cl.x = .15f
            val a = cl.a * (if (day) 1f else .6f)
            val base = if (dark) (if (day) 0x465268 else 0x283044) else if (day) 0xFFFFFF else 0x96A5C6
            paint.color = (base or 0xFF000000.toInt()); paint.alpha = (a * 255).toInt()
            cloud(c, cl.x * w, cl.y * h, cl.s * (if (mul > 1) mul * density else max(.7f * density, h / 330f)))
        }
        paint.alpha = 255
        if (kind == Kind.FOG) {
            for (i in 0 until 6) {
                val y = h * (.2f + i * .14f); val o = sin(t * .25f + i) * w * .05f
                val cc = if (day) 0xEBEEF3 else 0x969EAC
                paint.shader = LinearGradient(0f, y, 0f, y + h * .12f, intArrayOf(cc, (cc or 0x80000000.toInt()), cc), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(o - w * .1f, y, o + w * 1.1f, y + h * .12f, paint)
            }
            paint.shader = null
        }
        if (kind == Kind.RAIN || kind == Kind.STORM) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f * m; paint.strokeCap = Paint.Cap.ROUND
            paint.color = 0x99CDE1FF.toInt()
            val sp = if (kind == Kind.STORM) 1.5f else 1.1f
            path.reset()
            drops.forEach { p ->
                p.y += p.v * sp * dt; p.x -= p.v * sp * dt * .14f
                if (p.y > 1.05f) { p.y = -.05f; p.x = (Math.random() * 1.1).toFloat() }
                val x = p.x * w; val y = p.y * h; val l = p.l * 16 * m
                path.moveTo(x, y); path.lineTo(x - l * .22f, y + l)
            }
            c.drawPath(path, paint); paint.style = Paint.Style.FILL
        }
        if (kind == Kind.SNOW) {
            paint.color = 0xE6FFFFFF.toInt()
            drops.forEach { p ->
                p.y += p.v * .11f * dt
                if (p.y > 1.05f) { p.y = -.05f; p.x = Math.random().toFloat() }
                c.drawCircle((p.x + sin(t * .8f + p.ph) * .012f) * w, p.y * h, (1.2f + p.l * 1.6f) * m, paint)
            }
        }
        if (kind == Kind.STORM) {
            next -= dt
            if (next <= 0 || (still && bolt == null)) { flash = 1f; bolt = mkBolt(w, h); next = (2.2 + Math.random() * 3.5).toFloat() }
            if (flash > 0) {
                val b = bolt
                if (b != null && flash > .35f) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.6f * m; paint.color = 0xFFFFFFFF.toInt(); paint.strokeJoin = Paint.Join.ROUND
                    paint.setShadowLayer(22f * m, 0f, 0f, 0xFFBFD4FF.toInt())
                    path.reset(); b.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt[0], pt[1]) else path.lineTo(pt[0], pt[1]) }
                    c.drawPath(path, paint); paint.clearShadowLayer(); paint.style = Paint.Style.FILL
                }
                paint.color = 0xFFE1EBFF.toInt(); paint.alpha = (flash * .3f * 255).toInt(); c.drawRect(0f, 0f, w, h, paint); paint.alpha = 255
                flash -= dt * 2.4f
            }
        }
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        val parts = arrayOf(floatArrayOf(0f, 0f, 34f), floatArrayOf(-38f, 10f, 24f), floatArrayOf(36f, 8f, 27f), floatArrayOf(-14f, -16f, 25f), floatArrayOf(18f, -20f, 22f), floatArrayOf(-64f, 18f, 15f), floatArrayOf(64f, 17f, 16f))
        parts.forEach { (dx, dy, r) -> path.addCircle(x + dx * s, y + dy * s, r * s, Path.Direction.CW) }
        path.addRect(x - 64 * s, y + 10 * s, x + 64 * s, y + 33 * s, Path.Direction.CW)
        path.fillType = Path.FillType.WINDING
        c.drawPath(path, paint)
    }

    private fun mkBolt(w: Float, h: Float): List<FloatArray> {
        val pts = mutableListOf<FloatArray>()
        var x = (w * (.52 + Math.random() * .4)).toFloat(); var y = h * .3f
        pts += floatArrayOf(x, y)
        while (y < h * .98f) { y += (h * (.05 + Math.random() * .08)).toFloat(); x += ((Math.random() - .45) * w * .05).toFloat(); pts += floatArrayOf(x, y) }
        return pts
    }

    companion object {
        val DAY = mapOf(Kind.CLEAR to intArrayOf(0xFF2F86FF.toInt(), 0xFFA5D2FF.toInt()), Kind.PARTLY to intArrayOf(0xFF4990EA.toInt(), 0xFFBAD8F6.toInt()),
            Kind.CLOUDY to intArrayOf(0xFF7A8BA3.toInt(), 0xFFBCC6D3.toInt()), Kind.FOG to intArrayOf(0xFF98A2AE.toInt(), 0xFFCDD2D9.toInt()),
            Kind.RAIN to intArrayOf(0xFF44556E.toInt(), 0xFF8696AD.toInt()), Kind.SNOW to intArrayOf(0xFF8CA1BC.toInt(), 0xFFD8E0EB.toInt()),
            Kind.STORM to intArrayOf(0xFF1E2638.toInt(), 0xFF525E7A.toInt()))
        val NIGHT = mapOf(Kind.CLEAR to intArrayOf(0xFF060A1E.toInt(), 0xFF1C2C5A.toInt()), Kind.PARTLY to intArrayOf(0xFF090F26.toInt(), 0xFF27365F.toInt()),
            Kind.CLOUDY to intArrayOf(0xFF141A2A.toInt(), 0xFF363F55.toInt()), Kind.FOG to intArrayOf(0xFF1D232F.toInt(), 0xFF474E5C.toInt()),
            Kind.RAIN to intArrayOf(0xFF0D1321.toInt(), 0xFF2C3752.toInt()), Kind.SNOW to intArrayOf(0xFF19213A.toInt(), 0xFF4C5976.toInt()),
            Kind.STORM to intArrayOf(0xFF07090F.toInt(), 0xFF272D44.toInt()))

        /** Tek karelik görsel (widget arka planı). */
        fun still(kind: Kind, day: Boolean, w: Int, h: Int, density: Float, radius: Float): Bitmap {
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bm)
            val clip = Path().apply { addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, Path.Direction.CW) }
            c.clipPath(clip)
            SkyScene().apply { set(kind, day) }.draw(c, w.toFloat(), h.toFloat(), 0f, 1f, true, density)
            return bm
        }
    }
}

/** Kahraman alandaki gökyüzü: soldan sağa opaklığı artan, hava değişince yumuşak geçişli. */
class SkyView(ctx: Context) : View(ctx) {
    private val scene = SkyScene()
    private var prev: Bitmap? = null
    private var fade = 0f
    private var last = 0L
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val bmPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val reduce: Boolean = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    var mask = true

    private var has = false

    fun set(k: Kind, day: Boolean) {
        val changed = !has || scene.kind != k || scene.day != day
        if (changed && has && width > 0 && height > 0 && !reduce) {
            prev = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { drawScene(Canvas(it), 0f) }
            fade = 1f
        }
        has = true; scene.set(k, day); invalidate()
    }

    private fun drawScene(c: Canvas, dt: Float) {
        scene.draw(c, width.toFloat(), height.toFloat(), dt, 1f, reduce, resources.displayMetrics.density)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else min(.05f, (now - last) / 1e9f); last = now
        val w = width.toFloat(); val h = height.toFloat()
        val sc = canvas.saveLayer(0f, 0f, w, h, null)
        drawScene(canvas, dt)
        prev?.let { bm ->
            bmPaint.alpha = (max(0f, fade) * 255).toInt(); canvas.drawBitmap(bm, 0f, 0f, bmPaint)
            fade -= dt * 1.1f; if (fade <= 0f) { prev = null }
        }
        if (mask) {
            maskPaint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(0x00000000, 0x0F000000, 0x73000000, 0xD9000000.toInt(), 0xFF000000.toInt()), floatArrayOf(0f, .26f, .52f, .76f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, maskPaint)
        }
        canvas.restoreToCount(sc)
        if (!reduce && isShown) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); last = 0L }
}


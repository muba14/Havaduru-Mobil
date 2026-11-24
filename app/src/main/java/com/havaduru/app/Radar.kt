package com.havaduru.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Kaydırılabilir yağış radarı haritası: CARTO zemin + RainViewer radar karoları. */
@SuppressLint("ViewConstructor")
class RadarView(ctx: Context, private val p: Palette) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val tileDp = 256f * d
    var zoom = 6; private set
    private var cx = 0.0; private var cy = 0.0 // dünya pikseli (karo birimi * 256)
    private var markLat = 0.0; private var markLon = 0.0
    var host: String? = null
    var frames: List<Pair<Long, String>> = emptyList()
    var frame = -1; set(v) { field = v; invalidate() }
    var enabledNet = true

    private val cache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
    private val loading = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(4)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f * d; typeface = Fonts.body(ctx, 500) }

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            cx += dx / d; cy += dy / d; clampY(); invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomBy(1); return true }
    })

    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(MeasureSpec.getSize(wm), (360 * d).toInt())

    private fun world(z: Int) = 256.0 * 2.0.pow(z)
    private fun project(lat: Double, lon: Double, z: Int): Pair<Double, Double> {
        val s = world(z); val x = (lon + 180) / 360 * s
        val r = lat.coerceIn(-85.0, 85.0) * PI / 180
        val y = (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * s
        return x to y
    }

    fun center(lat: Double, lon: Double) {
        markLat = lat; markLon = lon
        val (x, y) = project(lat, lon, zoom); cx = x; cy = y; invalidate()
    }

    fun zoomBy(dz: Int) {
        val nz = (zoom + dz).coerceIn(3, 7); if (nz == zoom) return
        val f = 2.0.pow(nz - zoom); cx *= f; cy *= f; zoom = nz; clampY(); invalidate()
    }

    private fun clampY() { val s = world(zoom); cy = cy.coerceIn(0.0, s) }

    private fun tile(key: String, url: String): Bitmap? {
        cache.get(key)?.let { return it }
        if (!enabledNet) return null
        synchronized(loading) { if (!loading.add(key)) return null }
        pool.execute {
            val bm = Api.bitmap(url)
            if (bm != null) cache.put(key, bm)
            synchronized(loading) { loading.remove(key) }
            postInvalidate()
        }
        return null
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(p.surface2)
        val n = 1 shl zoom
        val w = width / d; val h = height / d
        val x0 = cx - w / 2; val y0 = cy - h / 2
        val tx0 = floor(x0 / 256).toInt(); val ty0 = floor(y0 / 256).toInt()
        val tx1 = floor((x0 + w) / 256).toInt(); val ty1 = floor((y0 + h) / 256).toInt()
        val style = if (p.dark) "dark_all" else "light_all"
        val f = frames.getOrNull(frame)
        for (layer in 0..1) {
            if (layer == 1 && f == null) break
            paint.alpha = if (layer == 0) 255 else 205
            for (ty in ty0..ty1) {
                if (ty < 0 || ty >= n) continue
                for (tx in tx0..tx1) {
                    val wx = ((tx % n) + n) % n
                    val url: String; val key: String
                    if (layer == 0) { key = "b/$style/$zoom/$wx/$ty"; url = "https://a.basemaps.cartocdn.com/$style/$zoom/$wx/$ty@2x.png" }
                    else { key = "r/${f!!.second}/$zoom/$wx/$ty"; url = "${host}${f.second}/256/$zoom/$wx/$ty/2/1_1.png" }
                    val bm = tile(key, url) ?: continue
                    val sx = ((tx * 256 - x0) * d).toFloat(); val sy = ((ty * 256 - y0) * d).toFloat()
                    c.drawBitmap(bm, null, RectF(sx, sy, sx + tileDp, sy + tileDp), paint)
                }
            }
        }
        paint.alpha = 255
        // şehir işareti
        val (mx, my) = project(markLat, markLon, zoom)
        var dx = mx - x0; val s = world(zoom); if (dx < -s / 2) dx += s; if (dx > s / 2) dx -= s
        val px = (dx * d).toFloat(); val py = ((my - y0) * d).toFloat()
        paint.style = Paint.Style.FILL; paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(px, py, 9 * d, paint)
        paint.color = 0xFF2466E0.toInt(); c.drawCircle(px, py, 6.5f * d, paint)
        // kaynak bilgisi
        val att = "© OpenStreetMap © CARTO · RainViewer"
        val tw = txt.measureText(att) + 12 * d
        paint.color = withAlpha(p.surface, .85f); c.drawRect(width - tw, height - 18 * d, width.toFloat(), height.toFloat(), paint)
        txt.color = p.muted; c.drawText(att, width - tw + 6 * d, height - 5 * d, txt)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
        return gd.onTouchEvent(e) || true
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); pool.shutdownNow() }

}

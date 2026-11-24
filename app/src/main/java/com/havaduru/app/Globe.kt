package com.havaduru.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.MotionEvent
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Dünya küresi verisi: nokta ızgarası, renk ölçekleri, doku boyama. */
object Globe {
    val lats = (-75..75 step 15).map { it.toDouble() }
    val lons = (-180 until 180 step 15).map { it.toDouble() }
    val points: List<Pair<Double, Double>> = lats.flatMap { la -> lons.map { lo -> la to lo } }

    fun fillNulls(a: List<Double?>): List<Double> {
        val nc = lons.size
        return a.mapIndexed { i, v ->
            if (v != null && v.isFinite()) v else {
                val r = i / nc
                val row = a.subList(r * nc, r * nc + nc).filterNotNull().filter { it.isFinite() }
                if (row.isEmpty()) 0.0 else row.average()
            }
        }
    }

    /** Catmull-Rom ile pürüzsüz ara değer. */
    fun field(vals: DoubleArray): (Double, Double) -> Double {
        val nr = lats.size; val nc = lons.size
        fun g(r: Int, c: Int) = vals[r.coerceIn(0, nr - 1) * nc + ((c % nc) + nc) % nc]
        fun cr(p0: Double, p1: Double, p2: Double, p3: Double, x: Double) = p1 + .5 * x * (p2 - p0 + x * (2 * p0 - 5 * p1 + 4 * p2 - p3 + x * (3 * (p1 - p2) + p3 - p0)))
        return { lat, lon ->
            val fr = (lat - lats[0]) / 15; val fc = (lon - lons[0]) / 15
            val r = floor(fr).toInt(); val c = floor(fc).toInt(); val tr = fr - r; val tc = fc - c
            fun row(k: Int) = cr(g(k, c - 1), g(k, c), g(k, c + 1), g(k, c + 2), tc)
            cr(row(r - 1), row(r), row(r + 1), row(r + 2), tr)
        }
    }

    class Scale(val stops: List<Pair<Double, Int>>, val ticks: List<Double>, val alpha: ((Double) -> Double)? = null) {
        fun color(v: Double): Int {
            if (v <= stops[0].first) return stops[0].second
            for (i in 1 until stops.size) if (v <= stops[i].first) {
                val (a, ca) = stops[i - 1]; val (b, cb) = stops[i]
                return mix(ca, cb, ((v - a) / (b - a)).toFloat())
            }
            return stops.last().second
        }
    }

    private fun c(h: Long) = h.toInt()
    val scales = mapOf(
        "temp" to Scale(listOf(-40.0 to c(0xFF7B3FB8), -25.0 to c(0xFF3B4CC0), -10.0 to c(0xFF3E8FD6), 0.0 to c(0xFF4FC3D9), 10.0 to c(0xFF5CC46A), 20.0 to c(0xFFE6D84A), 28.0 to c(0xFFF29A38), 35.0 to c(0xFFE0452F), 45.0 to c(0xFF8E1B1B)), listOf(-40.0, -20.0, 0.0, 20.0, 40.0)),
        "wind" to Scale(listOf(0.0 to c(0xFF2B3A8C), 10.0 to c(0xFF2E86C1), 20.0 to c(0xFF3FB9A3), 30.0 to c(0xFF8DD35F), 45.0 to c(0xFFF2D04B), 60.0 to c(0xFFF28C38), 80.0 to c(0xFFD9412B), 110.0 to c(0xFFB0247F)), listOf(0.0, 25.0, 50.0, 75.0, 100.0)),
        "rain" to Scale(listOf(0.0 to c(0xFF4FA3E0), 1.0 to c(0xFF4FA3E0), 5.0 to c(0xFF3BC46B), 10.0 to c(0xFFF2D04B), 20.0 to c(0xFFF28C38), 40.0 to c(0xFFD9412B)), listOf(0.0, 10.0, 20.0, 30.0, 40.0)) { v -> ((v - .2) / 1.3).coerceIn(0.0, 1.0) },
        "aqi" to Scale(listOf(0.0 to c(0xFF3BC46B), 20.0 to c(0xFF9AD15A), 40.0 to c(0xFFF2D04B), 60.0 to c(0xFFF28C38), 80.0 to c(0xFFD9412B), 100.0 to c(0xFF8E2A8E)), listOf(0.0, 25.0, 50.0, 75.0, 100.0))
    )

    fun valueText(layer: String, v: Double): String = when (layer) {
        "temp" -> "${Math.round(Wx.tc(v))}${Wx.tUnit()}"
        "wind" -> "${Math.round(Wx.ws(maxOf(0.0, v)))} ${Wx.wUnit()}"
        "rain" -> if (v < 10) String.format(java.util.Locale.US, "%.1f mm", maxOf(0.0, v)) else "${Math.round(v)} mm"
        else -> "${Math.round(maxOf(0.0, v))} AQI"
    }

    fun tickText(layer: String, v: Double): String = when (layer) {
        "temp" -> "${Math.round(Wx.tc(v))}°"
        "wind" -> "${Math.round(Wx.ws(v))}"
        else -> "${v.toInt()}"
    }

    private var coast: List<FloatArray>? = null
    private var bord: List<FloatArray>? = null

    private fun decode(s: String): List<FloatArray> = s.split(';').map { l ->
        val a = l.split(',').map { it.toInt() }
        var x = 0; var y = 0
        FloatArray(a.size) { i -> if (i % 2 == 0) { x += a[i]; x / 10f } else { y += a[i]; y / 10f } }
    }

    @Synchronized
    fun loadLines(ctx: Context) {
        if (coast != null) return
        val o = JSONObject(ctx.assets.open("geo.json").bufferedReader().use { it.readText() })
        coast = decode(o.getString("coast")); bord = decode(o.getString("bord"))
    }

    /** Eşdikdörtgen doku: veri renkleri + kıyı ve sınır çizgileri. */
    fun paint(ctx: Context, layer: String, vals: DoubleArray, tw: Int = 1024): Bitmap {
        loadLines(ctx)
        val th = tw / 2
        val sc = scales.getValue(layer); val f = field(vals)
        val px = IntArray(360 * 180)
        for (y in 0 until 180) {
            val lat = 89.5 - y
            for (x in 0 until 360) {
                val v = f(lat, -179.5 + x)
                val a = (sc.alpha?.invoke(v) ?: 1.0) * .92
                px[y * 360 + x] = withAlpha(sc.color(v), a.toFloat())
            }
        }
        val small = Bitmap.createBitmap(px, 360, 180, Bitmap.Config.ARGB_8888)
        val bm = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        cv.drawColor(0xFF0C1F45.toInt())
        cv.drawBitmap(small, Rect(0, 0, 360, 180), Rect(0, 0, tw, th), Paint(Paint.FILTER_BITMAP_FLAG))
        small.recycle()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        fun lines(ls: List<FloatArray>, color: Int, w: Float) {
            val path = Path()
            ls.forEach { l ->
                var i = 0
                while (i < l.size) {
                    val lo = l[i]; val la = l[i + 1]
                    val x = (lo + 180) / 360f * tw; val y = (90 - la) / 180f * th
                    if (i == 0 || abs(lo - l[i - 2]) > 180) path.moveTo(x, y) else path.lineTo(x, y)
                    i += 2
                }
            }
            p.color = color; p.strokeWidth = w; cv.drawPath(path, p)
        }
        val k = tw / 2048f
        lines(bord!!, 0x4DFFFFFF, 1.3f * k * 1.6f)
        lines(coast!!, 0xCCFFFFFF.toInt(), 2.2f * k * 1.6f)
        return bm
    }
}

/** OpenGL ES 2.0 ile 3B dünya küresi: doku, atmosfer parıltısı, şehir işareti, sürükleyerek döndürme. */
@SuppressLint("ViewConstructor")
class GlobeView(ctx: Context, private val onTap: (lat: Double, lon: Double, x: Float, y: Float) -> Unit) : GLSurfaceView(ctx) {
    private val r = GlobeRenderer()
    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f; private var moved = false

    init {
        r.pointSize = 15f * resources.displayMetrics.density
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(r)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setTexture(bm: Bitmap) = queueEvent { r.pending = bm }
    fun focus(lat: Double, lon: Double) = r.focus(lat, lon)
    fun nudge(dx: Float, dy: Float) { r.ty += dx; r.tx = (r.tx + dy).coerceIn(-1.2f, 1.2f); r.idle = 0f }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; moved = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX; val dy = e.y - lastY; lastX = e.x; lastY = e.y
                if (abs(e.x - downX) + abs(e.y - downY) > 12) moved = true
                // yatay sürükleme küreyi döndürür; belirgin dikey sürüklemede sayfa kaydırılabilsin
                if (!moved && abs(e.y - downY) > abs(e.x - downX) * 1.6f && abs(e.y - downY) > 20) parent?.requestDisallowInterceptTouchEvent(false)
                val k = 3.2f / width
                r.ty += dx * k; r.tx = (r.tx + dy * k).coerceIn(-1.2f, 1.2f); r.idle = 0f
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!moved) r.pick(e.x, e.y, width, height)?.let { (la, lo) -> onTap(la, lo, e.x, e.y) }
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}

class GlobeRenderer : GLSurfaceView.Renderer {
    @Volatile var pending: Bitmap? = null
    @Volatile var rx = .3f; @Volatile var ry = 0f
    @Volatile var tx = .3f; @Volatile var ty = 0f
    @Volatile var idle = 0f
    @Volatile var pointSize = 34f
    @Volatile private var markLat = 0.0; @Volatile private var markLon = 0.0
    private var started = false
    private var camZ = 4.25f; private var aspect = 1f
    private val fov = 32f
    private var last = 0L

    private val proj = FloatArray(16); private val view = FloatArray(16); private val model = FloatArray(16)
    private val mv = FloatArray(16); private val mvp = FloatArray(16); private val tmp = FloatArray(16)

    private var progGlobe = 0; private var progAtm = 0; private var progMark = 0
    private var tex = 0
    private lateinit var sphere: Mesh; private lateinit var atm: Mesh
    private val markBuf: FloatBuffer = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asFloatBuffer()

    class Mesh(val pos: FloatBuffer, val uv: FloatBuffer, val idx: ShortBuffer, val count: Int)

    fun focus(lat: Double, lon: Double) {
        markLat = lat; markLon = lon
        val la = lat * PI / 180; val lo = lon * PI / 180
        var target = (-PI / 2 - lo).toFloat()
        val d = ((target - ry) % (2 * PI).toFloat() + 3 * PI.toFloat()) % (2 * PI).toFloat() - PI.toFloat()
        target = ry + d
        ty = target; tx = (la * .85).toFloat().coerceIn(-1.2f, 1.2f); idle = 0f
        if (!started) { started = true; ry = ty - .9f; rx = tx }
    }

    /** Dokunulan noktanın enlem/boylamı (küreye değmiyorsa null). */
    fun pick(x: Float, y: Float, w: Int, h: Int): Pair<Double, Double>? {
        val nx = x / w * 2 - 1; val ny = -(y / h * 2 - 1)
        val t = tan(Math.toRadians(fov / 2.0))
        var dx = nx * t * aspect; var dy = ny * t; var dz = -1.0
        val n = sqrt(dx * dx + dy * dy + dz * dz); dx /= n; dy /= n; dz /= n
        val oz = camZ.toDouble()
        val b = 2 * (dz * oz); val c = oz * oz - 1
        val disc = b * b - 4 * c; if (disc < 0) return null
        val s = (-b - sqrt(disc)) / 2
        var px = dx * s; var py = dy * s; var pz = oz + dz * s
        // ters döndür: önce X(-rx), sonra Y(-ry)
        val cx = cos(-rx.toDouble()); val sx = sin(-rx.toDouble())
        val y1 = py * cx - pz * sx; val z1 = py * sx + pz * cx; py = y1; pz = z1
        val cy = cos(-ry.toDouble()); val sy = sin(-ry.toDouble())
        val x2 = px * cy + pz * sy; val z2 = -px * sy + pz * cy; px = x2; pz = z2
        return Math.toDegrees(asin(py.coerceIn(-1.0, 1.0))) to Math.toDegrees(atan2(-pz, px))
    }

    private fun buildSphere(rad: Float, seg: Int, rings: Int): Mesh {
        val pos = FloatArray((seg + 1) * (rings + 1) * 3); val uv = FloatArray((seg + 1) * (rings + 1) * 2)
        var pi = 0; var ui = 0
        for (r in 0..rings) {
            val lat = PI / 2 - r * PI / rings
            for (s in 0..seg) {
                val lon = -PI + s * 2 * PI / seg
                pos[pi++] = (rad * cos(lat) * cos(lon)).toFloat(); pos[pi++] = (rad * sin(lat)).toFloat(); pos[pi++] = (-rad * cos(lat) * sin(lon)).toFloat()
                uv[ui++] = s.toFloat() / seg; uv[ui++] = r.toFloat() / rings
            }
        }
        val idx = ShortArray(seg * rings * 6); var ii = 0
        for (r in 0 until rings) for (s in 0 until seg) {
            val a = (r * (seg + 1) + s).toShort(); val b = (a + seg + 1).toShort()
            idx[ii++] = a; idx[ii++] = b; idx[ii++] = (a + 1).toShort()
            idx[ii++] = b; idx[ii++] = (b + 1).toShort(); idx[ii++] = (a + 1).toShort()
        }
        return Mesh(fb(pos), fb(uv), ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(idx); position(0) }, idx.size)
    }

    private fun fb(a: FloatArray) = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

    private fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
    private fun program(vs: String, fs: String) = GLES20.glCreateProgram().also {
        GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(it)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.027f, 0.063f, 0.149f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vs = """
            uniform mat4 uMVP; uniform mat4 uMV;
            attribute vec3 aPos; attribute vec2 aUV;
            varying vec2 vUV; varying vec3 vN;
            void main(){ vUV=aUV; vN=normalize((uMV*vec4(aPos,0.0)).xyz); gl_Position=uMVP*vec4(aPos,1.0); }
        """.trimIndent()
        progGlobe = program(vs, """
            precision mediump float;
            uniform sampler2D uTex; varying vec2 vUV; varying vec3 vN;
            void main(){
              vec3 c=texture2D(uTex,vUV).rgb;
              float l=0.88+0.32*max(dot(vN,normalize(vec3(-0.5,0.4,0.75))),0.0);
              float rim=pow(1.0-max(vN.z,0.0),3.5)*0.7;
              gl_FragColor=vec4(c*l+vec3(0.45,0.7,1.0)*rim,1.0);
            }
        """.trimIndent())
        progAtm = program(vs, """
            precision mediump float; varying vec3 vN; varying vec2 vUV;
            void main(){ float d=-vN.z; float i=pow(clamp(d/0.52,0.0,1.0),1.8)*0.85; gl_FragColor=vec4(0.32*i,0.6*i,1.0*i,1.0); }
        """.trimIndent())
        progMark = program("""
            uniform mat4 uMVP; attribute vec3 aPos;
            uniform float uSize;
            void main(){ gl_Position=uMVP*vec4(aPos,1.0); gl_PointSize=uSize; }
        """.trimIndent(), """
            precision mediump float;
            void main(){ float d=length(gl_PointCoord-vec2(0.5));
              if(d<0.22){ gl_FragColor=vec4(1.0); } else if(d<0.48){ gl_FragColor=vec4(0.04,0.075,0.16,0.55); } else { discard; } }
        """.trimIndent())
        sphere = buildSphere(1f, 96, 64); atm = buildSphere(1.16f, 48, 32)
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val blank = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF0C1F45.toInt()) }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, blank, 0); blank.recycle()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        aspect = w.toFloat() / maxOf(1, h)
        camZ = if (aspect < 1) 4.25f / maxOf(.62f, aspect) else 4.25f
        Matrix.perspectiveM(proj, 0, fov, aspect, .1f, 100f)
        Matrix.setLookAtM(view, 0, 0f, 0f, camZ, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    private fun draw(m: Mesh, prog: Int) {
        GLES20.glUseProgram(prog)
        val aPos = GLES20.glGetAttribLocation(prog, "aPos"); val aUV = GLES20.glGetAttribLocation(prog, "aUV")
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uMV"), 1, false, mv, 0)
        GLES20.glEnableVertexAttribArray(aPos); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, m.pos)
        if (aUV >= 0) { GLES20.glEnableVertexAttribArray(aUV); GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, m.uv) }
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.count, GLES20.GL_UNSIGNED_SHORT, m.idx)
        GLES20.glDisableVertexAttribArray(aPos); if (aUV >= 0) GLES20.glDisableVertexAttribArray(aUV)
    }

    override fun onDrawFrame(gl: GL10?) {
        pending?.let { bm ->
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bm, 0); pending = null
        }
        val now = System.nanoTime(); val dt = if (last == 0L) 0f else minOf(.05f, (now - last) / 1e9f); last = now
        idle += dt; if (idle > 5f) ty += dt * .06f
        val k = minOf(1f, dt * 6f); rx += (tx - rx) * k; ry += (ty - ry) * k

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(ry.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0); Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)

        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glEnable(GLES20.GL_CULL_FACE); GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        draw(sphere, progGlobe)

        // atmosfer: arka yüzler, toplamalı karışım
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE); GLES20.glDepthMask(false)
        GLES20.glCullFace(GLES20.GL_FRONT)
        draw(atm, progAtm)
        GLES20.glCullFace(GLES20.GL_BACK); GLES20.glDisable(GLES20.GL_CULL_FACE)

        // şehir işareti
        val la = Math.toRadians(markLat); val lo = Math.toRadians(markLon)
        markBuf.position(0)
        markBuf.put((1.012 * cos(la) * cos(lo)).toFloat()); markBuf.put((1.012 * sin(la)).toFloat()); markBuf.put((-1.012 * cos(la) * sin(lo)).toFloat()); markBuf.position(0)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(progMark)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(progMark, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progMark, "uSize"), pointSize)
        val ap = GLES20.glGetAttribLocation(progMark, "aPos")
        GLES20.glEnableVertexAttribArray(ap); GLES20.glVertexAttribPointer(ap, 3, GLES20.GL_FLOAT, false, 0, markBuf)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1); GLES20.glDisableVertexAttribArray(ap)
        GLES20.glDepthMask(true)
        Matrix.setIdentityM(tmp, 0)
    }
}

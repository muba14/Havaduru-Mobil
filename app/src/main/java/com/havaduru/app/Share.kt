package com.havaduru.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Paylaşılabilir hava kartı (Hikaye 9:16 / Kare 1:1). Web sürümündeki kartla aynı düzen. */
object ShareCard {
    fun render(ctx: Context, city: City, w: Weather, story: Boolean): Bitmap {
        val W = 1080; val H = if (story) 1920 else 1080
        val bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val k = Wx.kindOf(w.cur.code)
        SkyScene().apply { set(k, w.cur.isDay) }.draw(c, W.toFloat(), H.toFloat(), 0f, W / 430f, true, 1f)
        val g = Paint()
        g.shader = LinearGradient(0f, 0f, 0f, H * .22f, 0x59050A18, 0x00050A18, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), g)
        g.shader = LinearGradient(0f, H * (if (story) .32f else .12f), 0f, H.toFloat(), intArrayOf(0x00050A18, 0x80050A18.toInt(), 0xD1050A18.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), g)

        val P = 80f
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(18f, 0f, 0f, 0x47000000) }
        fun font(size: Float, weight: Int, disp: Boolean) { tp.textSize = size; tp.typeface = if (disp) Fonts.display(ctx, weight) else Fonts.body(ctx, weight) }
        // marka
        val by = if (story) 118f else 96f
        val sunP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5B041.toInt(); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
        c.drawCircle(P + 20, by - 14, 15f, sunP)
        for (i in 0 until 8) { val a = i * PI / 4; c.drawLine(P + 20 + cos(a).toFloat() * 24, by - 14 + sin(a).toFloat() * 24, P + 20 + cos(a).toFloat() * 31, by - 14 + sin(a).toFloat() * 31, sunP) }
        font(46f, 700, true); c.drawText("HavaDuru", P + 64, by, tp)
        font(32f, 500, false); tp.textAlign = Paint.Align.RIGHT; tp.alpha = 217; c.drawText(Wx.hm(w.now), W - P, by, tp); tp.textAlign = Paint.Align.LEFT; tp.alpha = 255

        var y = if (story) 820f else 250f
        font(if (story) 68f else 58f, 600, true); c.drawText(Wx.cityName(city), P, y, tp)
        y += if (story) 58 else 50
        font(if (story) 36f else 32f, 500, false); tp.alpha = 217
        c.drawText(Wx.dateLong(w.now) + (if (city.country.isNotEmpty()) " · " + Wx.countryName(city) else ""), P, y, tp); tp.alpha = 255
        val ts = if (story) 330f else 250f
        y += if (story) 300 else 230
        font(ts, 700, true); val tv = Wx.tc(w.cur.t).roundToInt().toString(); c.drawText(tv, P - 8, y, tp)
        val tw = tp.measureText(tv)
        font(ts * .3f, 600, true); tp.alpha = 204; c.drawText(Wx.tUnit(), P - 8 + tw + 12, y - ts * .58f, tp); tp.alpha = 255
        y += if (story) 96 else 72
        font(if (story) 64f else 52f, 600, true); c.drawText(L.code(w.cur.code), P, y, tp)
        y += if (story) 56 else 46
        val td = w.daily.first()
        font(if (story) 34f else 30f, 500, false); tp.alpha = 224
        c.drawText("${L.s("feels")} ${Wx.deg(w.cur.feels)} · ${L.s("cardHi")} ${Wx.deg(td.max)} / ${L.s("cardLo")} ${Wx.deg(td.min)}", P, y, tp); tp.alpha = 255
        // ölçüm çipleri
        tp.clearShadowLayer()
        y += if (story) 64 else 44
        var x = P
        val chipP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FFFFFF }
        font(if (story) 30f else 27f, 600, false)
        val chips = mutableListOf(L.s("hum") to Wx.pct(w.cur.hum.roundToInt()), L.s("wind") to "${Wx.ws(w.cur.wind).roundToInt()} ${Wx.wUnit()}")
        w.aqi?.let { chips += "AQI" to it.v.roundToInt().toString() }
        val ch = if (story) 62f else 56f
        chips.forEach { (l, v) ->
            val t = "$l $v"; val cw = tp.measureText(t) + 44
            if (x + cw <= W - P) { c.drawRoundRect(RectF(x, y, x + cw, y + ch), ch / 2, ch / 2, chipP); c.drawText(t, x + 22, y + ch / 2 + (if (story) 11 else 10), tp); x += cw + 14 }
        }
        // aktivite kutusu
        val (icon, key) = Suggest.pick(k, w.cur.t, w.cur.isDay)
        val title = L.act(key)[0]
        val boxY = y + (if (story) 110 else 92)
        val ix = if (story) 120f else 96f
        val tx = P + 40 + ix + 30
        font(if (story) 44f else 38f, 600, true)
        val lines = wrap(tp, title, W - P - 40 - tx)
        val lh = if (story) 54f else 46f
        val boxH = maxOf(ix + 56, (if (story) 92f else 80f) + lines.size * lh)
        c.drawRoundRect(RectF(P, boxY, W - P, boxY + boxH), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF })
        c.drawRoundRect(RectF(P + 28, boxY + (boxH - ix) / 2, P + 28 + ix, boxY + (boxH - ix) / 2 + ix), 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEBFFFFFF.toInt() })
        ctx.getDrawable(icon)?.let { dr ->
            val l = (P + 28 + ix * .16f).toInt(); val t = (boxY + (boxH - ix) / 2 + ix * .16f).toInt(); val s = (ix * .68f).toInt()
            dr.setBounds(l, t, l + s, t + s); dr.draw(c)
        }
        font(if (story) 24f else 22f, 600, false); tp.alpha = 191; c.drawText(L.s("cardAct"), tx, boxY + (if (story) 62 else 54), tp); tp.alpha = 255
        font(if (story) 44f else 38f, 600, true)
        lines.forEachIndexed { i, l -> c.drawText(l, tx, boxY + (if (story) 118 else 100) + i * lh, tp) }
        font(if (story) 26f else 22f, 500, false); tp.alpha = 153; tp.textAlign = Paint.Align.CENTER
        c.drawText("HavaDuru · Open-Meteo", W / 2f, H - (if (story) 64f else 34f), tp)
        return bm
    }

    private fun wrap(p: Paint, text: String, maxW: Float): List<String> {
        val out = mutableListOf<String>(); var cur = ""
        text.split(' ').forEach { w -> val t = if (cur.isEmpty()) w else "$cur $w"; if (p.measureText(t) > maxW && cur.isNotEmpty()) { out += cur; cur = w } else cur = t }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    fun fileName(city: City, w: Weather, story: Boolean): String {
        val slug = java.text.Normalizer.normalize(Wx.cityName(city).lowercase(java.util.Locale("tr")).replace("ı", "i"), java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "sehir" }
        return "havaduru-$slug-${Wx.isoDay(w.now)}-${if (story) "story" else "square"}.png"
    }

    /** Uygulamanın önbelleğine yazar ve diğer uygulamalarla paylaşılabilir adres döndürür. */
    fun toShareUri(ctx: Context, bm: Bitmap, name: String): Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        File(dir, name).outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.parse("content://${ShareProvider.AUTH}/$name")
    }

    fun shareIntent(uri: Uri): Intent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "HavaDuru")

    /** Android 10 ve üstünde izin istemeden Galeri'ye (Resimler/HavaDuru) kaydeder. */
    fun saveToGallery(ctx: Context, bm: Bitmap, name: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HavaDuru")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
        return runCatching { ctx.contentResolver.openOutputStream(uri)?.use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }; true }.getOrDefault(false)
    }
}

/** Paylaşım için yalnızca önbellekteki kart görsellerini okunur olarak sunan küçük sağlayıcı. */
class ShareProvider : ContentProvider() {
    companion object { const val AUTH = "com.havaduru.app.share" }

    private fun file(uri: Uri): File? {
        val n = uri.lastPathSegment ?: return null
        if (!n.matches(Regex("havaduru-[a-z0-9-]+\\.png"))) return null
        return File(File(context!!.cacheDir, "share"), n).takeIf { it.exists() }
    }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        file(uri)?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = file(uri) ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(f.name, f.length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

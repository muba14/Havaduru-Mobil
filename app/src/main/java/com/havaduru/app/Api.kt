package com.havaduru.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone

/** Ücretsiz ve anahtarsız servisler: Open-Meteo, BigDataCloud, RainViewer, CARTO. */
object Api {
    private const val UA = "HavaDuru-Android/1.0 (+https://github.com/muba14/havaduru)"

    fun get(url: String, timeoutMs: Int = 12000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    fun bitmap(url: String): Bitmap? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode in 200..299) c.inputStream.use { BitmapFactory.decodeStream(it) } else null
        } finally { c.disconnect() }
    } catch (e: Exception) { null }

    /** "2026-11-23T22:45" ya da "2026-11-23" → UTC gibi milisaniye. */
    fun pIso(s: String): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.clear()
        c.set(s.substring(0, 4).toInt(), s.substring(5, 7).toInt() - 1, s.substring(8, 10).toInt(),
            if (s.length >= 16) s.substring(11, 13).toInt() else 0, if (s.length >= 16) s.substring(14, 16).toInt() else 0, 0)
        return c.timeInMillis
    }

    private fun JSONObject.d(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONArray.d(i: Int): Double? = if (isNull(i)) null else getDouble(i)

    fun forecast(c: City): Weather {
        val q = "latitude=${c.lat}&longitude=${c.lon}&timezone=auto"
        val w = JSONObject(get("https://api.open-meteo.com/v1/forecast?$q&past_days=1&forecast_days=11" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max"))
        val aq = runCatching {
            JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?$q&current=european_aqi,pm2_5,pm10," +
                    "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen")).getJSONObject("current")
        }.getOrNull()

        val cur = w.getJSONObject("current")
        val now = pIso(cur.getString("time"))
        val h = w.getJSONObject("hourly"); val ht = h.getJSONArray("time")
        val times = (0 until ht.length()).map { pIso(ht.getString(it)) }
        var i0 = times.indexOfFirst { it > now - 3600_000 }; if (i0 < 0) i0 = 0
        val hourFloor = now / 3600_000 * 3600_000
        val yi = times.indexOf(hourFloor - 86400_000)
        val htemp = h.getJSONArray("temperature_2m"); val hpop = h.optJSONArray("precipitation_probability")
        val hcode = h.getJSONArray("weather_code"); val hday = h.getJSONArray("is_day")
        val hourly = (i0 until minOf(i0 + 25, times.size)).map { k ->
            Hour(times[k], htemp.getDouble(k), hpop?.d(k), hcode.getInt(k), hday.getInt(k) == 1)
        }
        val dd = w.getJSONObject("daily"); val dt = dd.getJSONArray("time")
        var d0 = (0 until dt.length()).indexOfFirst { dt.getString(it) == cur.getString("time").substring(0, 10) }; if (d0 < 0) d0 = 0
        val daily = (d0 until minOf(d0 + 10, dt.length())).map { k ->
            Day(pIso(dt.getString(k)), dd.getJSONArray("weather_code").getInt(k),
                dd.getJSONArray("temperature_2m_max").getDouble(k), dd.getJSONArray("temperature_2m_min").getDouble(k),
                pIso(dd.getJSONArray("sunrise").getString(k)), pIso(dd.getJSONArray("sunset").getString(k)),
                dd.getJSONArray("uv_index_max").d(k), dd.optJSONArray("precipitation_probability_max")?.d(k))
        }
        val pollen = aq?.let { o ->
            val m = Pollen.types.associate { (k, _) -> k to o.d("${k}_pollen") }
            if (m.values.any { it != null }) m else null
        }
        return Weather(
            now = now, off = w.optLong("utc_offset_seconds") * 1000,
            yest = if (yi >= 0) htemp.d(yi) else null,
            cur = Current(cur.getDouble("temperature_2m"), cur.getDouble("apparent_temperature"), cur.getInt("weather_code"),
                cur.getInt("is_day") == 1, cur.getDouble("relative_humidity_2m"), cur.getDouble("wind_speed_10m"),
                cur.getDouble("wind_direction_10m"), cur.getDouble("surface_pressure")),
            aqi = aq?.d("european_aqi")?.let { Aqi(it, aq.d("pm2_5"), aq.d("pm10")) },
            pollen = pollen, hourly = hourly, daily = daily, live = true
        )
    }

    fun search(q: String): List<City> {
        val j = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=8&language=${L.lang}&format=json"))
        val r = j.optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map { i ->
            r.getJSONObject(i).let { City(it.getString("name"), it.optString("country"), it.getDouble("latitude"), it.getDouble("longitude"), it.optString("timezone").ifEmpty { null }, true, it.optString("admin1")) }
        }
    }

    /** Koordinattan şehir adı (BigDataCloud, istemci tarafı ücretsiz servis). */
    fun reverse(lat: Double, lon: Double): Pair<String, String>? = runCatching {
        val j = JSONObject(get("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=${L.lang}"))
        val name = j.optString("city").ifEmpty { j.optString("locality") }.ifEmpty { j.optString("principalSubdivision") }
        if (name.isEmpty()) null else name to j.optString("countryName")
    }.getOrNull()

    data class Mini(val t: Double, val code: Int, val day: Boolean)

    fun favorites(list: List<City>): List<Mini> {
        if (list.isEmpty()) return emptyList()
        val j = get("https://api.open-meteo.com/v1/forecast?latitude=${list.joinToString(",") { it.lat.toString() }}" +
                "&longitude=${list.joinToString(",") { it.lon.toString() }}&current=temperature_2m,weather_code,is_day&timezone=auto")
        val arr = if (j.trimStart().startsWith("[")) JSONArray(j) else JSONArray().put(JSONObject(j))
        return (0 until arr.length()).map { i ->
            arr.getJSONObject(i).getJSONObject("current").let { Mini(it.getDouble("temperature_2m"), it.getInt("weather_code"), it.getInt("is_day") == 1) }
        }
    }

    /** Dünya küresi: 15 derece aralıklı 264 nokta. */
    fun globe(kind: String): Map<String, DoubleArray> {
        Prefs.cacheGet("globe_$kind", 3600_000)?.let { return parseGlobe(it) }
        val lat = Globe.points.joinToString(",") { it.first.toInt().toString() }
        val lon = Globe.points.joinToString(",") { it.second.toInt().toString() }
        val raw = if (kind == "wx")
            get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,wind_speed_10m&daily=precipitation_sum&forecast_days=1&timezone=GMT", 25000)
        else get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=european_aqi&timezone=GMT", 25000)
        val arr = JSONArray(raw)
        if (arr.length() != Globe.points.size) throw IOException("len")
        val out = JSONObject()
        fun col(f: (JSONObject) -> Double?) = JSONArray().apply { Globe.fillNulls((0 until arr.length()).map { f(arr.getJSONObject(it)) }).forEach { put(it) } }
        if (kind == "wx") {
            out.put("temp", col { it.getJSONObject("current").d("temperature_2m") })
            out.put("wind", col { it.getJSONObject("current").d("wind_speed_10m") })
            out.put("rain", col { it.getJSONObject("daily").getJSONArray("precipitation_sum").d(0) })
        } else out.put("aqi", col { it.getJSONObject("current").d("european_aqi") })
        Prefs.cachePut("globe_$kind", out.toString())
        return parseGlobe(out.toString())
    }

    private fun parseGlobe(s: String): Map<String, DoubleArray> {
        val o = JSONObject(s)
        return o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> DoubleArray(a.length()) { a.getDouble(it) } } }
    }

    data class RadarFrames(val host: String, val frames: List<Pair<Long, String>>)

    fun radar(): RadarFrames {
        val j = JSONObject(get("https://api.rainviewer.com/public/weather-maps.json"))
        val past = j.getJSONObject("radar").getJSONArray("past")
        val all = (0 until past.length()).map { past.getJSONObject(it).let { f -> f.getLong("time") to f.getString("path") } }
        // 20 dakikada bir kare: ücretsiz servisin istek sınırını aşmamak için
        return RadarFrames(j.getString("host"), all.filterIndexed { i, _ -> (all.size - 1 - i) % 2 == 0 })
    }
}

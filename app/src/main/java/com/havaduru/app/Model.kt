package com.havaduru.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** Bir şehir. [raw] = arama ya da konumdan gelen, listede olmayan şehir. */
data class City(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val tz: String? = null,
    val raw: Boolean = false,
    val admin: String = ""
) {
    val key: String get() = String.format(java.util.Locale.US, "%.2f,%.2f", lat, lon)

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("country", country).put("lat", lat).put("lon", lon)
        .put("tz", tz ?: "").put("raw", raw).put("admin", admin)

    companion object {
        fun fromJson(o: JSONObject) = City(
            o.getString("name"), o.optString("country"), o.getDouble("lat"), o.getDouble("lon"),
            o.optString("tz").ifEmpty { null }, o.optBoolean("raw"), o.optString("admin")
        )
    }
}

data class Country(val name: String, val cities: List<City>)

data class Current(
    val t: Double, val feels: Double, val code: Int, val isDay: Boolean,
    val hum: Double, val wind: Double, val wdir: Double, val pres: Double
)

/** Saatler, web sürümündeki gibi "yerel duvar saati UTC gibi" milisaniye olarak tutulur. */
data class Hour(val t: Long, val temp: Double, val pop: Double?, val code: Int, val day: Boolean)

data class Day(
    val t: Long, val code: Int, val max: Double, val min: Double,
    val sunrise: Long, val sunset: Long, val uv: Double?, val pop: Double?
)

data class Aqi(val v: Double, val pm25: Double?, val pm10: Double?)

data class Weather(
    val now: Long,
    val off: Long,
    val yest: Double?,
    val cur: Current,
    val aqi: Aqi?,
    val pollen: Map<String, Double?>?,
    val hourly: List<Hour>,
    val daily: List<Day>,
    val live: Boolean,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("now", now).put("off", off).put("live", live).put("fetchedAt", fetchedAt)
        if (yest != null) o.put("yest", yest)
        o.put("cur", JSONObject().put("t", cur.t).put("feels", cur.feels).put("code", cur.code).put("isDay", cur.isDay)
            .put("hum", cur.hum).put("wind", cur.wind).put("wdir", cur.wdir).put("pres", cur.pres))
        if (aqi != null) o.put("aqi", JSONObject().put("v", aqi.v).putOpt("pm25", aqi.pm25).putOpt("pm10", aqi.pm10))
        if (pollen != null) {
            val p = JSONObject(); pollen.forEach { (k, v) -> p.put(k, v ?: JSONObject.NULL) }; o.put("pollen", p)
        }
        o.put("hourly", JSONArray().apply {
            hourly.forEach { put(JSONObject().put("t", it.t).put("temp", it.temp).putOpt("pop", it.pop).put("code", it.code).put("day", it.day)) }
        })
        o.put("daily", JSONArray().apply {
            daily.forEach {
                put(JSONObject().put("t", it.t).put("code", it.code).put("max", it.max).put("min", it.min)
                    .put("sunrise", it.sunrise).put("sunset", it.sunset).putOpt("uv", it.uv).putOpt("pop", it.pop))
            }
        })
        return o
    }

    companion object {
        private fun JSONObject.optD(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null

        fun fromJson(o: JSONObject): Weather {
            val c = o.getJSONObject("cur")
            val pol = o.optJSONObject("pollen")?.let { p -> p.keys().asSequence().associateWith { p.optD(it) } }
            val hs = o.getJSONArray("hourly"); val ds = o.getJSONArray("daily")
            return Weather(
                o.getLong("now"), o.getLong("off"), o.optD("yest"),
                Current(c.getDouble("t"), c.getDouble("feels"), c.getInt("code"), c.getBoolean("isDay"),
                    c.getDouble("hum"), c.getDouble("wind"), c.getDouble("wdir"), c.getDouble("pres")),
                o.optJSONObject("aqi")?.let { Aqi(it.getDouble("v"), it.optD("pm25"), it.optD("pm10")) },
                pol,
                (0 until hs.length()).map { i -> hs.getJSONObject(i).let { Hour(it.getLong("t"), it.getDouble("temp"), it.optD("pop"), it.getInt("code"), it.getBoolean("day")) } },
                (0 until ds.length()).map { i ->
                    ds.getJSONObject(i).let {
                        Day(it.getLong("t"), it.getInt("code"), it.getDouble("max"), it.getDouble("min"),
                            it.getLong("sunrise"), it.getLong("sunset"), it.optD("uv"), it.optD("pop"))
                    }
                },
                o.optBoolean("live"), o.optLong("fetchedAt", System.currentTimeMillis())
            )
        }
    }
}

/** Hava durumunun sahne/simge türü. */
enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

object Wx {
    fun kindOf(c: Int): Kind = when {
        c <= 1 -> Kind.CLEAR
        c == 2 -> Kind.PARTLY
        c == 3 -> Kind.CLOUDY
        c <= 48 -> Kind.FOG
        (c in 71..77) || c == 85 || c == 86 -> Kind.SNOW
        c >= 95 -> Kind.STORM
        else -> Kind.RAIN
    }

    fun iconRes(kind: Kind, day: Boolean): Int = when (kind) {
        Kind.CLEAR -> if (day) R.drawable.wx_clear_day else R.drawable.wx_clear_night
        Kind.PARTLY -> if (day) R.drawable.wx_partly_day else R.drawable.wx_partly_night
        Kind.CLOUDY -> R.drawable.wx_cloudy
        Kind.FOG -> R.drawable.wx_fog
        Kind.RAIN -> R.drawable.wx_rain
        Kind.SNOW -> R.drawable.wx_snow
        Kind.STORM -> R.drawable.wx_storm
    }

    // ---- birimler ----
    fun tc(c: Double) = if (Prefs.unitT == "f") c * 9 / 5 + 32 else c
    fun td(d: Double) = if (Prefs.unitT == "f") d * 9 / 5 else d
    fun ws(k: Double) = if (Prefs.unitW == "mph") k * 0.621371 else k
    fun wUnit() = if (Prefs.unitW == "mph") "mph" else L.s("kmh")
    fun tUnit() = if (Prefs.unitT == "f") "°F" else "°C"
    fun deg(c: Double) = "${tc(c).roundToInt()}°"
    fun pct(n: Int) = if (L.lang == "tr") "%$n" else "$n%"

    // ---- zaman (UTC gibi tutulan yerel saat) ----
    private fun cal(ms: Long) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    fun hm(ms: Long): String { val c = cal(ms); return String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) }
    fun dow(ms: Long) = cal(ms).get(Calendar.DAY_OF_WEEK) - 1
    fun dateLong(ms: Long): String { val c = cal(ms); return L.date(L.list("daysL")[dow(ms)], c.get(Calendar.DAY_OF_MONTH), L.list("mon")[c.get(Calendar.MONTH)]) }
    fun dayMonth(ms: Long): String { val c = cal(ms); return L.dm(c.get(Calendar.DAY_OF_MONTH), L.list("mon")[c.get(Calendar.MONTH)]) }
    fun isoDay(ms: Long): String { val c = cal(ms); return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) }

    fun cityName(c: City): String = if (c.raw) c.name else if (L.lang == "en") PlaceData.cityEn[c.name] ?: c.name else c.name
    fun countryName(c: City): String = if (c.raw) c.country else if (L.lang == "en") PlaceData.countryEn[c.country] ?: c.country else c.country
    fun countryLabel(name: String): String = if (L.lang == "en") PlaceData.countryEn[name] ?: name else name

    val allCities: List<City> by lazy { PlaceData.countries.flatMap { it.cities } }
}

/** Aktivite önerisi: (simge, metin anahtarı). Eşikler web sürümüyle aynı. */
object Suggest {
    fun pick(k: Kind, tc: Double, day: Boolean): Pair<Int, String> = when {
        k == Kind.STORM -> R.drawable.act_home to "storm"
        k == Kind.SNOW -> R.drawable.act_film to "snow"
        k == Kind.RAIN -> if (tc >= 18) R.drawable.act_umb to "rainW" else R.drawable.act_cup to "rainC"
        k == Kind.FOG -> R.drawable.act_cup to "fog"
        tc >= 35 -> R.drawable.act_wave to "t35"
        tc >= 30 -> R.drawable.act_ice to "t30"
        !day && k == Kind.CLEAR && tc >= 10 -> R.drawable.act_star to "stars"
        tc >= 25 -> R.drawable.act_ball to "t25"
        tc >= 18 -> R.drawable.act_park to "t18"
        tc >= 10 -> R.drawable.act_shoe to "t10"
        tc >= 0 -> R.drawable.act_cup to "t0"
        else -> R.drawable.act_home to "frz"
    }

    fun tint(icon: Int): Int = when (icon) {
        R.drawable.act_home -> 0xFFE4572E.toInt()
        R.drawable.act_cup -> 0xFFB5651D.toInt()
        R.drawable.act_film -> 0xFFE63946.toInt()
        R.drawable.act_umb -> 0xFF3B82F6.toInt()
        R.drawable.act_ball -> 0xFFF97316.toInt()
        R.drawable.act_wave -> 0xFF0EA5E9.toInt()
        R.drawable.act_ice -> 0xFFEC4899.toInt()
        R.drawable.act_star -> 0xFF6366F1.toInt()
        R.drawable.act_park -> 0xFF22C55E.toInt()
        else -> 0xFF3B82F6.toInt()
    }
}

/** "Biliyor muydun?" bilgileri. */
object Facts {
    fun pool(k: Kind, tc: Double, wind: Double, day: Boolean): Pair<List<List<String>>, Int> {
        val c = mutableListOf<String>()
        if (k == Kind.STORM) c += "storm"; if (k == Kind.SNOW) c += "snow"; if (k == Kind.RAIN) c += "rain"; if (k == Kind.FOG) c += "fog"
        if (tc >= 35) c += "hot35" else if (tc >= 28) c += "hot"
        if (tc <= 0) c += "cold"; if (wind >= 40) c += "wind"
        if (!day && (k == Kind.CLEAR || k == Kind.PARTLY)) c += "night"
        if (k == Kind.CLOUDY || k == Kind.PARTLY) c += "clouds"
        if (day && k == Kind.CLEAR) c += "sun"
        if (c.isEmpty()) c += "clouds"
        val f = I18nData.facts
        return c.flatMap { f[it].orEmpty() } to (f[c[0]]?.size ?: 1)
    }

    fun hash(s: String): Int { var h = 0; for (ch in s) h = h * 31 + ch.code; return kotlin.math.abs(h) }
}

/** Polen seviyeleri: ABD Ulusal Alerji Bürosu (NAB) eşikleri, tane/m³. */
object Pollen {
    val types = listOf("alder" to "tree", "birch" to "tree", "olive" to "tree", "grass" to "grass", "mugwort" to "weed", "ragweed" to "weed")
    private val th = mapOf("tree" to doubleArrayOf(15.0, 90.0, 1500.0), "grass" to doubleArrayOf(5.0, 20.0, 200.0), "weed" to doubleArrayOf(10.0, 50.0, 500.0))
    fun level(v: Double?, group: String): Int {
        if (v == null || v < 1) return 0
        val t = th.getValue(group)
        return if (v < t[0]) 1 else if (v < t[1]) 2 else if (v < t[2]) 3 else 4
    }
}

/** Ay evresi (cihazda hesaplanır). */
object Moon {
    const val SYN = 29.530588853
    fun age(nowUtc: Long): Double {
        val ref = 947182440000L // 2000-01-06 18:14 UTC
        val a = ((nowUtc - ref) / 86400000.0) % SYN
        return (a + SYN) % SYN
    }
}

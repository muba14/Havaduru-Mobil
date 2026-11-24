package com.havaduru.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Kalıcı ayarlar ve küçük önbellekler (SharedPreferences). */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("havaduru", Context.MODE_PRIVATE)
        L.lang = lang
    }

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var lang: String
        get() = str("lang", if (java.util.Locale.getDefault().language == "tr") "tr" else "en")
        set(v) { put("lang", v); L.lang = v }
    var unitT: String get() = str("unitT", "c"); set(v) = put("unitT", v)
    var unitW: String get() = str("unitW", "kmh"); set(v) = put("unitW", v)
    /** auto | light | dark */
    var theme: String get() = str("theme", "auto"); set(v) = put("theme", v)

    var notifDaily: Boolean get() = sp.getBoolean("notifDaily", true); set(v) = sp.edit().putBoolean("notifDaily", v).apply()
    var notifAlerts: Boolean get() = sp.getBoolean("notifAlerts", true); set(v) = sp.edit().putBoolean("notifAlerts", v).apply()
    var askedNotif: Boolean get() = sp.getBoolean("askedNotif", false); set(v) = sp.edit().putBoolean("askedNotif", v).apply()

    var city: City
        get() = runCatching { City.fromJson(JSONObject(str("city", ""))) }.getOrNull() ?: Wx.allCities.first()
        set(v) = put("city", v.toJson().toString())

    var favs: List<City>
        get() {
            val raw = sp.getString("favs", null) ?: return listOf("İstanbul", "Ankara", "İzmir").mapNotNull { n -> Wx.allCities.find { it.name == n } }
            return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { City.fromJson(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
        }
        set(v) = put("favs", JSONArray().apply { v.forEach { put(it.toJson()) } }.toString())

    /** Son alınan hava durumu (widget ve bildirimler için). */
    fun saveWeather(c: City, w: Weather) =
        put("wx", JSONObject().put("city", c.toJson()).put("w", w.toJson()).toString())

    fun loadWeather(): Pair<City, Weather>? = runCatching {
        val o = JSONObject(str("wx", ""))
        City.fromJson(o.getJSONObject("city")) to Weather.fromJson(o.getJSONObject("w"))
    }.getOrNull()

    /** Aynı bildirimi aynı gün iki kez göndermemek için. */
    fun sentToday(tag: String, day: String): Boolean = sp.getString("sent_$tag", "") == day
    fun markSent(tag: String, day: String) = put("sent_$tag", day)

    fun cacheGet(k: String, maxAgeMs: Long): String? {
        val ts = sp.getLong("ts_$k", 0L)
        if (System.currentTimeMillis() - ts > maxAgeMs) return null
        return sp.getString("c_$k", null)
    }

    fun cachePut(k: String, v: String) = sp.edit().putString("c_$k", v).putLong("ts_$k", System.currentTimeMillis()).apply()
}

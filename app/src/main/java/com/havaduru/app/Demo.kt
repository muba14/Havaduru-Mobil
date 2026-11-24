package com.havaduru.app

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.tan

/** İnternet yokken gösterilen örnek veri (web sürümündeki demo() ile aynı mantık). */
object Demo {
    class Rng(seed: String) {
        private var h = 1779033703
        init { for (ch in seed) { h = (h xor ch.code) * -862048943; h = (h shl 13) or (h ushr 19) } }
        fun next(): Double {
            h = (h xor (h ushr 16)) * -2048144789
            h = (h xor (h ushr 13)) * -1028477387
            h = h xor (h ushr 16)
            return (h.toLong() and 0xffffffffL) / 4294967296.0
        }
    }

    private fun clamp(v: Double, a: Double, b: Double) = maxOf(a, minOf(b, v))

    fun sunTimes(dayMs: Long, lat: Double, lon: Double, offMin: Int): Pair<Long, Long> {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dayMs }
        val doy = c.get(Calendar.DAY_OF_YEAR)
        val dec = -23.44 * cos(2 * PI * (doy + 10) / 365) * PI / 180
        val la = lat * PI / 180
        val h = acos(clamp(-tan(la) * tan(dec), -1.0, 1.0)) / PI * 12
        val noon = 12 + offMin / 60.0 - lon / 15
        return (dayMs + (noon - h) * 3600_000).toLong() to (dayMs + (noon + h) * 3600_000).toLong()
    }

    fun weather(c: City, force: Kind? = null): Weather {
        val offMin = c.tz?.let { TimeZone.getTimeZone(it).getOffset(System.currentTimeMillis()) / 60000 } ?: (Math.round(c.lon / 15) * 60).toInt()
        val now = System.currentTimeMillis() + offMin * 60000L
        val day0 = now / 86400_000 * 86400_000
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day0 }
        val r = Rng(c.name + Wx.isoDay(day0))
        val doy = cal.get(Calendar.DAY_OF_YEAR)
        val season = cos(2 * PI * (doy - 196) / 365) * (if (c.lat >= 0) 1 else -1)
        val mean = 28 - .35 * abs(c.lat) + season * min(1.3, abs(c.lat) / 40) * 10
        val pool = intArrayOf(0, 1, 2, 2, 3, 61, 63, 80, 95, 45)
        fun pick(): Int { var k = pool[floor(r.next() * pool.size).toInt()]; if (mean < 1 && k in 61..94) k = 73; return k }
        val forced = when (force) { Kind.CLEAR -> 0; Kind.PARTLY -> 2; Kind.CLOUDY -> 3; Kind.FOG -> 45; Kind.RAIN -> 63; Kind.SNOW -> 73; Kind.STORM -> 95; null -> null }
        val dOff = DoubleArray(11); for (i in 1 until 11) dOff[i] = dOff[i - 1] + (r.next() - .5) * 3.2
        fun tAt(ms: Long): Double {
            val h = (ms - day0) / 3600_000.0
            val di = clamp(floor(h / 24), 0.0, 9.0).toInt()
            val fr = clamp(h / 24 - di, 0.0, 1.0)
            return mean + dOff[di] * (1 - fr) + dOff[di + 1] * fr + 5.2 * cos(((h % 24) - 15) / 24 * 2 * PI)
        }
        val daily = (0 until 10).map { i ->
            val tt = day0 + i * 86400_000L
            val (sr, ss) = sunTimes(tt, c.lat, c.lon, offMin)
            val code = if (i == 0 && forced != null) forced else pick()
            val k = Wx.kindOf(code)
            Day(tt, code, mean + dOff[i] + 5.2, mean + dOff[i] - 5.2, sr, ss,
                clamp(7 * cos((c.lat - season * 20) * PI / 180) + r.next() * 1.5, 0.0, 11.0),
                if (k == Kind.RAIN || k == Kind.STORM || k == Kind.SNOW) 55 + r.next() * 40 else r.next() * 25)
        }
        val code = daily[0].code
        val h0 = now / 3600_000 * 3600_000
        val hourly = (0 until 25).map { i ->
            val tt = h0 + i * 3600_000L
            val di = clamp(floor((tt - day0) / 86400_000.0), 0.0, 9.0).toInt()
            Hour(tt, tAt(tt), clamp((daily[di].pop ?: 0.0) + (r.next() - .5) * 20, 0.0, 100.0), if (i < 8) code else daily[di].code, tt > daily[di].sunrise && tt < daily[di].sunset)
        }
        val tn = tAt(now)
        val kind = Wx.kindOf(code)
        val wind = 6 + r.next() * 22 + (if (kind == Kind.STORM) 18 else 0)
        val hum = clamp(45 + r.next() * 25 + (if (kind in listOf(Kind.RAIN, Kind.STORM, Kind.FOG, Kind.SNOW)) 28 else 0), 20.0, 99.0)
        val inEu = c.lat > 34 && c.lat < 72 && c.lon > -25 && c.lon < 45
        val mo = cal.get(Calendar.MONTH)
        val sea = doubleArrayOf(0.0, .1, .6, 1.0, 1.0, .9, .7, .6, .4, .1, 0.0, 0.0)[mo]
        val pollen = if (!inEu) null else mapOf(
            "alder" to r.next() * 40 * (if (mo < 4) 1 else 0) * sea,
            "birch" to r.next() * 300 * (if (mo in 2..4) 1 else 0),
            "olive" to r.next() * 120 * (if (mo in 3..5 && c.lat < 45) 1 else 0),
            "grass" to r.next() * 60 * sea,
            "mugwort" to r.next() * 30 * (if (mo in 6..8) 1 else 0),
            "ragweed" to r.next() * 40 * (if (mo in 7..9) 1 else 0)
        )
        return Weather(
            now, offMin * 60000L, tn + (r.next() - .5) * 8,
            Current(tn, tn - wind * .07 + (hum - 55) * .03, code, now > daily[0].sunrise && now < daily[0].sunset, hum, wind, r.next() * 360, 1004 + r.next() * 20),
            Aqi(14 + r.next() * 58, 5 + r.next() * 20, 10 + r.next() * 30), pollen, hourly, daily, live = false
        )
    }

    fun globe(layer: String): DoubleArray {
        val r = Rng("globe$layer" + Wx.isoDay(System.currentTimeMillis()))
        return DoubleArray(Globe.points.size) { i ->
            val (la, lo) = Globe.points[i]
            val n = kotlin.math.sin(lo * PI / 50 + la / 20) * cos(la * PI / 40 - lo / 70)
            when (layer) {
                "temp" -> 30 - .62 * abs(la) - (if (la < -60) 14 else 0) + 6 * n + (r.next() - .5) * 4
                "wind" -> maxOf(0.0, 10 + 20 * abs(kotlin.math.sin(la * PI / 60)) + 10 * n + r.next() * 8)
                "rain" -> maxOf(0.0, n * 16 + (if (abs(la) < 12) 9 else 0) + r.next() * 8 - 7)
                else -> 12 + (if (la > 10 && la < 45 && lo > 60 && lo < 135) 45 else 0) + (if (la > 12 && la < 35 && lo > -15 && lo < 55) 30 else 0) + r.next() * 14
            }
        }
    }
}

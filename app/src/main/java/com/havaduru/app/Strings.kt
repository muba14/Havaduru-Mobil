package com.havaduru.app

/** Uygulama metinleri (TR/EN). Ortak metinler [I18nData] içinde, uygulamaya özel olanlar burada. */
object L {
    @Volatile var lang: String = "tr"

    private val appTr = mapOf(
        "appSub" to "Hava durumu",
        "search" to "Şehir ara…",
        "settings" to "Ayarlar",
        "notifT" to "Bildirimler",
        "notifDaily" to "Sabah özeti",
        "notifDailySub" to "Her sabah 07:00'den sonra günün havası",
        "notifAlerts" to "Hava uyarıları",
        "notifAlertsSub" to "Fırtına, şiddetli yağış, aşırı sıcak ve don",
        "notifOff" to "Bildirim izni kapalı. Ayarlardan açabilirsin.",
        "chDaily" to "Günlük özet",
        "chAlerts" to "Hava uyarıları",
        "alStorm" to "Fırtına uyarısı",
        "alStormT" to { h: String -> "$h civarında gök gürültülü fırtına bekleniyor. Dışarıdaysan güvenli bir yere geç." },
        "alRain" to "Şiddetli yağış",
        "alRainT" to { p: String -> "Bugün yağış ihtimali $p. Şemsiyeni unutma." },
        "alHot" to "Aşırı sıcak",
        "alHotT" to { t: String -> "Bugün sıcaklık $t'ye çıkacak. Bol su iç, öğle saatlerinde gölgede kal." },
        "alFrost" to "Don uyarısı",
        "alFrostT" to { t: String -> "Gece sıcaklık $t'ye düşecek. Yollarda buzlanma olabilir." },
        "alWind" to "Kuvvetli rüzgar",
        "alWindT" to { w: String -> "Rüzgar şu an $w hızında esiyor. Dışarıda dikkatli ol." },
        "dailyT" to { c: String, t: String, cond: String -> "$c $t · $cond" },
        "theme" to "Tema", "thAuto" to "Cihaz", "thLight" to "Açık", "thDark" to "Koyu",
        "units" to "Birimler", "langT" to "Dil",
        "share" to "Paylaş", "save" to "Kaydet", "savedGallery" to "Görsel Galeri'ye kaydedildi",
        "locWait" to "Konum bulunuyor…",
        "refresh" to "Yenile",
        "offline" to "Canlı veriye ulaşılamadı; örnek veri gösteriliyor. Bağlantını kontrol edip yenile.",
        "updated" to { t: String -> "Güncellendi: $t" },
        "widgetName" to "HavaDuru",
        "radarErr" to "Radar verisine ulaşılamadı.",
        "globeHint2" to "Sürükleyerek döndür, dokununca değeri gör",
        "tapToOpen" to "Açmak için dokun"
    )

    private val appEn = mapOf(
        "appSub" to "Weather",
        "search" to "Search city…",
        "settings" to "Settings",
        "notifT" to "Notifications",
        "notifDaily" to "Morning summary",
        "notifDailySub" to "Today's weather every morning after 07:00",
        "notifAlerts" to "Weather alerts",
        "notifAlertsSub" to "Storms, heavy rain, extreme heat and frost",
        "notifOff" to "Notifications are blocked. You can allow them in Settings.",
        "chDaily" to "Daily summary",
        "chAlerts" to "Weather alerts",
        "alStorm" to "Storm warning",
        "alStormT" to { h: String -> "A thunderstorm is expected around $h. If you're outside, find shelter." },
        "alRain" to "Heavy rain",
        "alRainT" to { p: String -> "Chance of rain today is $p. Don't forget your umbrella." },
        "alHot" to "Extreme heat",
        "alHotT" to { t: String -> "It will reach $t today. Drink plenty of water and stay in the shade at midday." },
        "alFrost" to "Frost warning",
        "alFrostT" to { t: String -> "It will drop to $t tonight. Roads may be icy." },
        "alWind" to "Strong wind",
        "alWindT" to { w: String -> "The wind is blowing at $w right now. Take care outside." },
        "dailyT" to { c: String, t: String, cond: String -> "$c $t · $cond" },
        "theme" to "Theme", "thAuto" to "Device", "thLight" to "Light", "thDark" to "Dark",
        "units" to "Units", "langT" to "Language",
        "share" to "Share", "save" to "Save", "savedGallery" to "Image saved to Gallery",
        "locWait" to "Finding your location…",
        "refresh" to "Refresh",
        "offline" to "Couldn't reach live data, so sample data is shown. Check your connection and refresh.",
        "updated" to { t: String -> "Updated: $t" },
        "widgetName" to "HavaDuru",
        "radarErr" to "Couldn't reach radar data.",
        "globeHint2" to "Drag to rotate, tap to read a value",
        "tapToOpen" to "Tap to open"
    )

    private fun app() = if (lang == "en") appEn else appTr
    private fun data() = if (lang == "en") I18nData.en else I18nData.tr

    private fun raw(k: String): Any? = app()[k] ?: data()[k] ?: appTr[k] ?: I18nData.tr[k]

    fun s(k: String): String = raw(k) as? String ?: k

    @Suppress("UNCHECKED_CAST")
    fun list(k: String): List<String> = raw(k) as? List<String> ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    fun map(k: String): Map<String, String> = raw(k) as? Map<String, String> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun act(k: String): List<String> = ((raw("act") as? Map<String, List<String>>)?.get(k)) ?: listOf("", "")

    @Suppress("UNCHECKED_CAST")
    fun f1(k: String, a: String): String = (raw(k) as? (String) -> String)?.invoke(a) ?: k

    @Suppress("UNCHECKED_CAST")
    fun f3(k: String, a: String, b: String, c: String): String = (raw(k) as? (String, String, String) -> String)?.invoke(a, b, c) ?: k

    fun code(c: Int): String = map("codes")[c.toString()] ?: ""

    // Web sürümündeki fonksiyonlu metinler
    fun warmer(n: Int) = if (lang == "en") "$n° warmer than yesterday" else "Dünden $n° daha sıcak"
    fun colder(n: Int) = if (lang == "en") "$n° colder than yesterday" else "Dünden $n° daha soğuk"
    fun sunsetIn(d: String) = if (lang == "en") "Sunset in $d" else "Batışa $d"
    fun sunriseIn(d: String) = if (lang == "en") "Sunrise in $d" else "Doğuşa $d"
    fun dur(h: Int, m: Int) = if (lang == "en") "${h}h ${m}m" else "$h sa $m dk"
    fun lit(n: Int) = if (lang == "en") "$n% illuminated" else "%$n aydınlık"
    fun age(n: String) = if (lang == "en") "$n days old" else "$n günlük"
    fun pollenTop(n: String, l: String) = if (lang == "en") "Highest: $n (${l.lowercase()})" else "En yüksek: $n (${l.lowercase(java.util.Locale("tr"))})"
    fun date(dl: String, d: Int, m: String) = if (lang == "en") "$dl, $m $d" else "$dl, $d $m"
    fun dm(d: Int, m: String) = if (lang == "en") "$m $d" else "$d $m"
}

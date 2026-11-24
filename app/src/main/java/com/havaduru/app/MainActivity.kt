package com.havaduru.app

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** HavaDuru ana ekranı: web sitesindeki tüm bölümler, aynı sırayla. */
class MainActivity : Activity() {
    private lateinit var p: Palette
    private lateinit var u: Ui
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(3)

    private var city: City = Wx.allCities.first()
    private var data: Weather? = null
    private var mode = "h"
    private var factOff = 0
    private var favs = listOf<City>()
    private val favData = HashMap<String, Api.Mini>()
    private var prevTemp: Int? = null
    private val prevMetrics = HashMap<Int, Float>()
    private var lastKey: String? = null
    private var globeLayer = "temp"
    private val globeVals = HashMap<String, DoubleArray>()
    private val globeLive = HashMap<String, Boolean>()
    private var radarIdx = 0
    private var radarPlaying = false
    private var loadSeq = 0

    // görünümler
    private lateinit var scroll: ScrollView
    private lateinit var countrySp: Spinner
    private lateinit var citySp: Spinner
    private lateinit var search: EditText
    private var popup: ListPopupWindow? = null
    private lateinit var note: TextView
    private lateinit var favRow: LinearLayout
    private lateinit var favMsg: TextView
    private lateinit var actIcon: ImageView
    private lateinit var actBox: FrameLayout
    private lateinit var actTitle: TextView
    private lateinit var actSub: TextView
    private lateinit var factText: TextView
    private lateinit var sky: SkyView
    private lateinit var heroContent: LinearLayout
    private lateinit var place: TextView
    private lateinit var starBtn: ImageView
    private lateinit var whenTv: TextView
    private lateinit var tempTv: TextView
    private lateinit var unitTv: TextView
    private lateinit var condTv: TextView
    private lateinit var feelsTv: TextView
    private lateinit var diffTv: TextView
    private lateinit var hiloTv: TextView
    private lateinit var metrics: LinearLayout
    private lateinit var pollenBox: LinearLayout
    private lateinit var chart: ChartView
    private lateinit var segChart: Seg
    private lateinit var tenDay: TenDayView
    private lateinit var sunView: SunView
    private lateinit var sunState: TextView
    private lateinit var sunRows: LinearLayout
    private lateinit var moonBig: MoonView
    private lateinit var moonRow: MoonView
    private lateinit var moonInfo: LinearLayout
    private var globe: GlobeView? = null
    private lateinit var globeTip: TextView
    private lateinit var globeState: TextView
    private lateinit var globeTitle: TextView
    private lateinit var globeBar: View
    private lateinit var globeTicks: LinearLayout
    private lateinit var radar: RadarView
    private lateinit var radarMsg: TextView
    private lateinit var playBtn: ImageView
    private lateinit var seek: SeekBar
    private lateinit var rtime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Notify.channels(this)
        city = Prefs.city
        favs = Prefs.favs
        build()
        Prefs.loadWeather()?.let { (c, w) -> if (c.key == city.key) { data = w; render() } }
        load(city)
        WeatherJob.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.askedNotif && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Prefs.askedNotif = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    override fun onResume() { super.onResume(); globe?.onResume() }
    override fun onPause() { super.onPause(); globe?.onPause(); setPlay(false) }
    override fun onDestroy() { super.onDestroy(); io.shutdownNow() }

    // ------------------------------------------------------------------ kurulum
    @Suppress("DEPRECATION")
    private fun build() {
        p = Palette.current(this); u = Ui(this, p)
        window.statusBarColor = p.bg; window.navigationBarColor = p.bg
        window.decorView.systemUiVisibility = if (p.dark) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0))
        globe?.onPause(); globe = null

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(u.dp(16), u.dp(14), u.dp(16), u.dp(32)) }
        fun add(v: View, top: Int = 16) = root.addView(v, u.lp(topMargin = u.dp(top)))

        root.addView(header(), u.lp())
        add(picker(), 12)
        note = u.text(L.s("offline"), 13.5f).apply { background = rounded(p.accentSoft, dp(14f)); setPadding(u.dp(14), u.dp(10), u.dp(14), u.dp(10)); visibility = View.GONE; setLineSpacing(0f, 1.2f) }
        add(note, 12)
        add(favorites(), 14)
        add(activityCard())
        add(factCard(), 12)
        add(hero())
        metrics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(u.card(metrics, 6, radius = 22))
        pollenBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(u.card(u.col(10, title(R.drawable.ic_flower, L.s("pollenT")), pollenBox)))
        add(chartCard())
        tenDay = TenDayView(this, p)
        add(u.card(u.col(8, title(R.drawable.ic_calendar, L.s("d10"), L.s("d10s")), tenDay)))
        add(sunCard()); add(moonCard())
        add(globeCard()); add(radarCard())
        add(u.text("${L.s("data")}: Open-Meteo · ${L.s("radarBy")}: RainViewer · BigDataCloud · © OpenStreetMap, CARTO\n${L.s("moonNote")}", 12f, p.muted).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f) }, 22)

        scroll = ScrollView(this).apply { setBackgroundColor(p.bg); isFillViewport = true; addView(root) }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> globeVisibility() }
        setContentView(scroll)
        syncPickers(city)
    }

    private fun dp(v: Float): Float = resources.displayMetrics.density * v

    private fun iconView(res: Int, sizeDp: Int, tint: Int? = null) = ImageView(this).apply {
        setImageResource(res); if (tint != null) imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(u.dp(sizeDp), u.dp(sizeDp))
    }

    private fun title(icon: Int, t: String, sub: String? = null, color: Int = p.fg, iconTint: Int = p.accent): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(iconView(icon, 18, iconTint))
        row.addView(u.text(t, 18f, color, 600, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = u.dp(8) })
        if (sub != null) row.addView(u.text(sub, 13f, p.muted).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(8) })
        return row
    }

    private fun circleBtn(icon: Int, desc: String, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(icon); imageTintList = ColorStateList.valueOf(p.fg); contentDescription = desc
        background = rounded(p.surface, dp(999f), p.line, u.dp(1)); setPadding(u.dp(9), u.dp(9), u.dp(9), u.dp(9))
        layoutParams = LinearLayout.LayoutParams(u.dp(40), u.dp(40)); setOnClickListener { onClick() }
    }

    private fun header(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(iconView(R.drawable.ic_logo_sun, 24))
        row.addView(u.text("HavaDuru", 22f, p.fg, 700, true).apply { letterSpacing = -0.02f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = u.dp(8) })
        row.addView(circleBtn(R.drawable.ic_refresh, L.s("refresh")) { load(city) })
        row.addView(circleBtn(R.drawable.ic_settings, L.s("settings")) { settings() }, LinearLayout.LayoutParams(u.dp(40), u.dp(40)).apply { marginStart = u.dp(8) })
        return row
    }

    // ------------------------------------------------------------------ ülke/şehir seçimi, arama, konum
    private var syncing = false
    private var countryItems = listOf<String>()

    private inner class SpAdapter(items: List<String>) : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = style(super.getView(position, convertView, parent) as TextView, false)
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = style(super.getDropDownView(position, convertView, parent) as TextView, true)
        private fun style(t: TextView, drop: Boolean) = t.apply {
            setTextColor(p.fg); typeface = Fonts.body(context, 500); textSize = 15f
            setPadding(u.dp(if (drop) 16 else 4), u.dp(if (drop) 12 else 0), u.dp(16), u.dp(if (drop) 12 else 0)); setBackgroundColor(if (drop) p.surface else 0)
        }
    }

    private fun spinner(icon: Int): Pair<FrameLayout, Spinner> {
        val sp = Spinner(this, Spinner.MODE_DROPDOWN).apply {
            background = null; setPopupBackgroundDrawable(rounded(p.surface, dp(14f), p.line, u.dp(1)))
            setPadding(u.dp(30), 0, u.dp(26), 0)
        }
        val box = FrameLayout(this).apply { background = rounded(p.surface, dp(999f), p.line, u.dp(1)) }
        box.addView(sp, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(44)))
        box.addView(iconView(icon, 16, p.muted), FrameLayout.LayoutParams(u.dp(16), u.dp(16), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = u.dp(12) })
        box.addView(iconView(R.drawable.ic_chevron, 14, p.muted), FrameLayout.LayoutParams(u.dp(14), u.dp(14), Gravity.CENTER_VERTICAL or Gravity.END).apply { marginEnd = u.dp(12) })
        return box to sp
    }

    private fun picker(): View {
        val (cBox, cSp) = spinner(R.drawable.ic_globe); countrySp = cSp
        val (ciBox, ciSp) = spinner(R.drawable.ic_pin); citySp = ciSp
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(cBox, LinearLayout.LayoutParams(0, u.dp(44), 1f))
        top.addView(ciBox, LinearLayout.LayoutParams(0, u.dp(44), 1f).apply { marginStart = u.dp(8) })

        search = EditText(this).apply {
            hint = L.s("search"); setHintTextColor(p.muted); setTextColor(p.fg); textSize = 15f; typeface = Fonts.body(context, 400)
            background = null; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH; setPadding(u.dp(36), 0, u.dp(14), 0)
        }
        val sBox = FrameLayout(this).apply { background = rounded(p.surface, dp(999f), p.line, u.dp(1)) }
        sBox.addView(search, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(44)))
        sBox.addView(iconView(R.drawable.ic_search, 16, p.muted), FrameLayout.LayoutParams(u.dp(16), u.dp(16), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = u.dp(12) })
        val loc = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = rounded(p.surface, dp(999f), p.line, u.dp(1))
            setPadding(u.dp(12), 0, u.dp(14), 0)
            addView(iconView(R.drawable.ic_locate, 16, p.accent))
            addView(u.text(L.s("myLoc"), 14.5f, p.fg, 500), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(6) })
            setOnClickListener { locate() }
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(sBox, LinearLayout.LayoutParams(0, u.dp(44), 1f))
        bottom.addView(loc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.dp(44)).apply { marginStart = u.dp(8) })

        countrySp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (syncing) return
                val name = countryItems.getOrNull(pos) ?: return
                // Spinner ilk çizimde de seçim bildirir: zaten seçili ülkeyse şehri değiştirme
                if (!city.raw && name == city.country) return
                PlaceData.countries.find { it.name == name }?.let { go(it.cities.first()) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        citySp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (syncing) return
                val co = PlaceData.countries.find { it.name == countryItems.getOrNull(countrySp.selectedItemPosition) } ?: return
                sortedCities(co).getOrNull(pos)?.let { if (it.key != city.key) go(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        var pending: Runnable? = null
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pending?.let { main.removeCallbacks(it) }
                val q = s?.toString()?.trim().orEmpty()
                if (q.length < 2) { popup?.dismiss(); return }
                pending = Runnable { runSearch(q) }.also { main.postDelayed(it, 300) }
            }
        })
        search.setOnEditorActionListener { _, _, _ -> popup?.takeIf { it.isShowing }?.performItemClick(0); true }
        return u.col(8, top, bottom)
    }

    private fun sortedCities(co: Country): List<City> {
        val loc = if (L.lang == "en") java.util.Locale.ENGLISH else java.util.Locale("tr")
        val col = java.text.Collator.getInstance(loc)
        return co.cities.sortedWith { a, b -> col.compare(Wx.cityName(a), Wx.cityName(b)) }
    }

    private fun syncPickers(c: City) {
        syncing = true
        val co = if (c.raw) null else PlaceData.countries.find { co -> co.name == c.country && co.cities.any { it.key == c.key } }
        countryItems = (if (co == null) listOf(c.country.ifEmpty { L.s("searchRes") }) else emptyList()) + PlaceData.countries.map { it.name }
        countrySp.adapter = SpAdapter(countryItems.mapIndexed { i, n -> if (co == null && i == 0) n else Wx.countryLabel(n) })
        if (co != null) {
            countrySp.setSelection(countryItems.indexOf(co.name), false)
            val list = sortedCities(co)
            citySp.adapter = SpAdapter(list.map { Wx.cityName(it) })
            citySp.setSelection(list.indexOfFirst { it.key == c.key }.coerceAtLeast(0), false)
        } else {
            countrySp.setSelection(0, false)
            citySp.adapter = SpAdapter(listOf(Wx.cityName(c)))
        }
        main.post { syncing = false }
    }

    private fun runSearch(q: String) {
        val lv = q.lowercase(java.util.Locale("tr"))
        io.execute {
            val res = if (data?.live == false) null else runCatching { Api.search(q) }.getOrNull()
            val list = res ?: Wx.allCities.filter { it.name.lowercase(java.util.Locale("tr")).contains(lv) || Wx.cityName(it).lowercase().contains(lv) }.take(6)
            main.post { showResults(list) }
        }
    }

    private fun showResults(list: List<City>) {
        if (isFinishing) return
        val items = if (list.isEmpty()) listOf(L.s("noResult")) else list.map { c -> listOf(Wx.cityName(c), c.admin, Wx.countryName(c)).filter { it.isNotEmpty() }.distinct().joinToString(", ") }
        val pw = popup ?: ListPopupWindow(this).also { popup = it }
        pw.anchorView = search; pw.setBackgroundDrawable(rounded(p.surface, dp(14f), p.line, u.dp(1)))
        pw.setAdapter(SpAdapter(items)); pw.isModal = false
        pw.setOnItemClickListener { _, _, pos, _ ->
            pw.dismiss()
            list.getOrNull(pos)?.let { c ->
                search.setText(""); (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
                go(c)
            }
        }
        pw.show()
    }

    private fun locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOC); return
        }
        val lm = getSystemService(LocationManager::class.java)
        if (lm == null) { toast(L.s("locNo")); return }
        toast(L.s("locWait"))
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) { toast(L.s("locFail")); return }
        try {
            val lastLoc = lm.getLastKnownLocation(provider)
            if (lastLoc != null && System.currentTimeMillis() - lastLoc.time < 10 * 60_000) { gotLocation(lastLoc); return }
            if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(provider, null, mainExecutor) { l -> if (l != null) gotLocation(l) else toast(L.s("locFail")) }
            else @Suppress("DEPRECATION") lm.requestSingleUpdate(provider, { l -> gotLocation(l) }, Looper.getMainLooper())
        } catch (e: SecurityException) { toast(L.s("locDenied")) }
    }

    private fun gotLocation(l: Location) {
        val lat = (l.latitude * 1000).roundToInt() / 1000.0; val lon = (l.longitude * 1000).roundToInt() / 1000.0
        io.execute {
            val r = Api.reverse(lat, lon)
            main.post { go(City(r?.first ?: L.s("myLoc"), r?.second ?: "", lat, lon, null, true)) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (requestCode == REQ_LOC) { if (ok) locate() else toast(L.s("locDenied")) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ favoriler
    private fun favorites(): View {
        favMsg = u.text("", 12.5f, p.bad, 500)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(iconView(R.drawable.ic_star_fill, 13, p.sun))
        head.addView(u.label(L.s("favs")), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(8) })
        head.addView(favMsg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(10) })
        favRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, u.dp(2), 0, u.dp(6)) }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; addView(favRow) }
        return u.col(8, head, hs)
    }

    private fun renderFavs() {
        favRow.removeAllViews()
        if (favs.isEmpty()) { favRow.addView(u.text(L.s("favEmpty"), 13.5f, p.muted)); return }
        favs.forEachIndexed { i, c ->
            val fd = favData[c.key]; val active = c.key == city.key
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = rounded(if (active) p.accentSoft else p.surface, dp(18f), if (active) p.accent else p.line, u.dp(1))
                elevation = if (p.dark) 0f else dp(1.5f)
                setPadding(u.dp(10), u.dp(10), u.dp(14), u.dp(10)); minimumWidth = u.dp(168)
                setOnClickListener { go(c) }
            }
            card.addView(iconView(if (fd != null) Wx.iconRes(Wx.kindOf(fd.code), fd.day) else R.drawable.wx_cloudy, 30))
            val names = u.col(2, u.text(Wx.cityName(c), 14.5f, p.fg, 600).apply { maxWidth = u.dp(110); setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END },
                u.text(if (fd != null) L.code(fd.code) else Wx.countryName(c), 12.5f, p.muted).apply { maxWidth = u.dp(110); setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END })
            card.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = u.dp(10) })
            card.addView(u.text(if (fd != null) Wx.deg(fd.t) else "–", 22f, p.fg, 600, true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(8) })
            favRow.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) marginStart = u.dp(10); marginEnd = u.dp(2); bottomMargin = u.dp(3) })
        }
    }

    private fun loadFavs() {
        val list = favs.toList(); if (list.isEmpty()) { renderFavs(); return }
        val live = data?.live == true
        io.execute {
            val res = if (live) runCatching { Api.favorites(list) }.getOrNull() else null
            val out = res ?: list.map { c -> Demo.weather(c).let { Api.Mini(it.cur.t, it.cur.code, it.cur.isDay) } }
            main.post { list.forEachIndexed { i, c -> out.getOrNull(i)?.let { favData[c.key] = it } }; renderFavs() }
        }
    }

    private fun toggleFav() {
        if (favs.any { it.key == city.key }) favs = favs.filter { it.key != city.key }
        else { if (favs.size >= 5) { favMsg.text = L.s("favFull"); main.postDelayed({ favMsg.text = "" }, 3500); return }; favs = favs + city }
        Prefs.favs = favs; favMsg.text = ""; starState(); loadFavs()
    }

    private fun starState() {
        val on = favs.any { it.key == city.key }
        starBtn.setImageResource(if (on) R.drawable.ic_star_fill else R.drawable.ic_star)
        starBtn.imageTintList = ColorStateList.valueOf(if (on) p.sun else p.muted)
        starBtn.background = rounded(withAlpha(p.surface, .85f), dp(999f), if (on) p.sun else p.line, u.dp(1))
        starBtn.contentDescription = if (on) L.s("favDel") else L.s("favAdd")
    }

    // ------------------------------------------------------------------ aktivite ve bilgi kartları
    private fun tile(): FrameLayout = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(u.dp(56), u.dp(56)) }

    private fun activityCard(): View {
        actBox = tile(); actIcon = ImageView(this); actBox.addView(actIcon, FrameLayout.LayoutParams(u.dp(34), u.dp(34), Gravity.CENTER))
        actTitle = u.text("—", 20f, p.fg, 600, true).apply { setLineSpacing(0f, 1.1f) }
        actSub = u.text("", 14f, p.muted).apply { setLineSpacing(0f, 1.25f) }
        val body = u.col(3, u.label(L.s("actL")), actTitle, actSub)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(actBox); addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = u.dp(14) }) }
        return u.card(row, 16)
    }

    private fun factCard(): View {
        val box = tile().apply { background = rounded(mix(0xFFFACC15.toInt(), p.surface, .84f), dp(18f)) }
        box.addView(ImageView(this).apply { setImageResource(R.drawable.ic_bulb) }, FrameLayout.LayoutParams(u.dp(34), u.dp(34), Gravity.CENTER))
        factText = u.text("—", 15.5f, p.fg, 500).apply { setLineSpacing(0f, 1.3f) }
        val more = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(p.surface2, dp(999f), p.line, u.dp(1)); setPadding(u.dp(10), u.dp(6), u.dp(12), u.dp(6))
            addView(iconView(R.drawable.ic_refresh, 14, p.accent))
            addView(u.text(L.s("factMore"), 13f, p.fg, 500), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(6) })
            setOnClickListener { factOff++; renderFact() }
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(u.label(L.s("factL"))); body.addView(factText, u.lp(topMargin = u.dp(4)))
        body.addView(more, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.dp(10) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(box); addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = u.dp(14) }) }
        return u.card(row, 16)
    }

    private fun renderAct() {
        val w = data ?: return
        val (icon, key) = Suggest.pick(Wx.kindOf(w.cur.code), w.cur.t, w.cur.isDay)
        actIcon.setImageResource(icon)
        actBox.background = rounded(mix(Suggest.tint(icon), p.surface, .84f), dp(18f))
        val t = L.act(key); actTitle.text = t[0]; actSub.text = t.getOrElse(1) { "" }
    }

    private fun renderFact() {
        val w = data ?: return
        val (pool, first) = Facts.pool(Wx.kindOf(w.cur.code), w.cur.t, w.cur.wind, w.cur.isDay)
        if (pool.isEmpty()) return
        val base = Facts.hash(city.name + Wx.isoDay(w.now)) % first
        val f = pool[(base + factOff) % pool.size]
        factText.text = if (L.lang == "en") f.getOrElse(1) { f[0] } else f[0]
    }

    // ------------------------------------------------------------------ kahraman alan
    private fun hero(): View {
        val frame = FrameLayout(this).apply { background = rounded(p.surface, dp(28f)); clipToOutline = true; elevation = if (p.dark) 0f else dp(2f) }
        sky = SkyView(this)
        frame.addView(sky, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        place = u.text("—", 21f, p.fg, 600, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        starBtn = ImageView(this).apply { setPadding(u.dp(8), u.dp(8), u.dp(8), u.dp(8)); setOnClickListener { toggleFav() } }
        val shareBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_share); imageTintList = ColorStateList.valueOf(p.fg); contentDescription = L.s("shareT")
            background = rounded(withAlpha(p.surface, .85f), dp(999f), p.line, u.dp(1)); setPadding(u.dp(8), u.dp(8), u.dp(8), u.dp(8))
            setOnClickListener { shareDialog() }
        }
        val placeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        placeRow.addView(iconView(R.drawable.ic_pin_fill, 18, p.accent))
        placeRow.addView(place, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = u.dp(6) })
        placeRow.addView(starBtn, LinearLayout.LayoutParams(u.dp(34), u.dp(34)).apply { marginStart = u.dp(6) })
        placeRow.addView(shareBtn, LinearLayout.LayoutParams(u.dp(34), u.dp(34)).apply { marginStart = u.dp(6) })
        whenTv = u.text("…", 13.5f, p.muted)
        tempTv = u.text("–", 104f, p.fg, 700, true).apply { letterSpacing = -0.055f; includeFontPadding = false }
        unitTv = u.text("°C", 34f, p.muted, 600, true)
        val tempRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; addView(tempTv); addView(unitTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.dp(10); marginStart = u.dp(2) }) }
        condTv = u.text("—", 22f, p.fg, 600, true)
        feelsTv = u.text("", 14f, p.muted)
        diffTv = u.text("", 13f, p.fg, 600).apply {
            background = rounded(p.surface2, dp(999f), p.line, u.dp(1)); setPadding(u.dp(8), u.dp(4), u.dp(10), u.dp(4)); compoundDrawablePadding = u.dp(4); visibility = View.GONE
        }
        hiloTv = u.text("", 14f, p.muted)
        heroContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(u.dp(20), u.dp(18), u.dp(16), u.dp(18)) }
        heroContent.addView(placeRow); heroContent.addView(whenTv, u.lp(topMargin = u.dp(2)))
        heroContent.addView(tempRow, u.lp(topMargin = u.dp(10)))
        heroContent.addView(condTv, u.lp(topMargin = u.dp(2))); heroContent.addView(feelsTv, u.lp(topMargin = u.dp(4)))
        heroContent.addView(diffTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.dp(8) })
        heroContent.addView(hiloTv, u.lp(topMargin = u.dp(14)))
        frame.addView(heroContent, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return frame
    }

    private fun tween(tv: TextView, from: Float?, to: Float, dec: Int, suffix: String = "") {
        fun fmt(v: Float) = if (dec == 0) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.${dec}f", v)
        if (from == null || from == to || !animationsOn()) { tv.text = fmt(to) + suffix; return }
        ValueAnimator.ofFloat(from, to).apply {
            duration = 700; interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { tv.text = fmt(it.animatedValue as Float) + suffix }; start()
        }
    }

    private fun animationsOn() = runCatching { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)

    private fun renderHero() {
        val w = data ?: return
        val c = w.cur; val td = w.daily.first(); val k = Wx.kindOf(c.code)
        place.text = Wx.cityName(city) + (if (city.country.isNotEmpty()) ", " + Wx.countryName(city) else "")
        whenTv.text = "${Wx.dateLong(w.now)} · ${Wx.hm(w.now)}"
        val nt = Wx.tc(c.t).roundToInt()
        tween(tempTv, prevTemp?.toFloat(), nt.toFloat(), 0); prevTemp = nt
        unitTv.text = Wx.tUnit()
        condTv.text = L.code(c.code)
        feelsTv.text = "${L.s("feels")} ${Wx.deg(c.feels)}"
        val y = w.yest
        if (y == null) diffTv.visibility = View.GONE else {
            val n = Wx.td(c.t - y).roundToInt(); diffTv.visibility = View.VISIBLE
            val (txt, icon, col) = when { n == 0 -> Triple(L.s("same"), R.drawable.ic_minus, p.muted); n > 0 -> Triple(L.warmer(n), R.drawable.ic_up, p.sun); else -> Triple(L.colder(-n), R.drawable.ic_down, p.accent) }
            diffTv.text = txt
            diffTv.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(icon)?.mutate()?.apply { setTint(col); setBounds(0, 0, u.dp(14), u.dp(14)) }, null, null, null)
        }
        hiloTv.text = "↑ ${L.s("hi")} ${Wx.deg(td.max)}     ↓ ${L.s("lo")} ${Wx.deg(td.min)}"
        sky.set(k, c.isDay)
        starState()
    }

    // ------------------------------------------------------------------ ölçümler
    private fun metricCell(icon: Int, label: String, value: String, unit: String, sub: View, idx: Int, num: Float?): View {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(iconView(icon, 14, p.accent)); head.addView(u.label(label), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(6) })
        val v = u.text(value, 26f, p.fg, 600, true)
        if (num != null) { val dec = if (value.contains('.')) 1 else 0; tween(v, prevMetrics[idx], num, dec); prevMetrics[idx] = num }
        val vr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; addView(v); addView(u.text(unit, 13f, p.muted, 500), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(5); bottomMargin = u.dp(3) }) }
        return u.col(6, head, vr, sub).apply { setPadding(u.dp(12), u.dp(12), u.dp(12), u.dp(12)) }
    }

    private fun meter(v: Float) = MeterView(this, p).apply { value = v }

    private fun renderMetrics() {
        val w = data ?: return; val c = w.cur; val td = w.daily.first()
        metrics.removeAllViews()
        val dir = L.list("dirs").getOrElse(((c.wdir / 45).roundToInt()) % 8) { "" }
        val bi = when { c.wind < 6 -> 0; c.wind < 20 -> 1; c.wind < 39 -> 2; c.wind < 62 -> 3; else -> 4 }
        val aqSub: View; val aqVal: String; val aqNum: Float?
        if (w.aqi != null) {
            val v = w.aqi.v; val ai = if (v <= 20) 0 else if (v <= 40) 1 else if (v <= 60) 2 else if (v <= 80) 3 else 4
            val col = listOf(p.good, p.good, p.warn, p.bad, p.bad)[ai]
            aqSub = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(View(context).apply { background = rounded(col, dp(99f)) }, LinearLayout.LayoutParams(u.dp(8), u.dp(8)))
                addView(u.text("${L.list("aqN")[ai]} · PM2.5 ${w.aqi.pm25?.roundToInt() ?: "—"}", 12.5f, p.muted), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(6) })
            }
            aqVal = v.roundToInt().toString(); aqNum = v.toFloat().roundToInt().toFloat()
        } else { aqSub = u.text(L.s("noData"), 12.5f, p.muted); aqVal = "—"; aqNum = null }
        val uv = td.uv; val uvn = if (uv == null) "" else L.list("uvN")[if (uv < 3) 0 else if (uv < 6) 1 else if (uv < 8) 2 else if (uv < 11) 3 else 4]
        val pi = if (c.pres < 1005) 0 else if (c.pres > 1020) 2 else 1
        val windSub = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(iconView(R.drawable.ic_up, 13, p.muted).apply { rotation = (c.wdir + 180).toFloat() })
            addView(u.text("$dir · ${L.list("beau")[bi]}", 12.5f, p.muted), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(4) })
        }
        val pop = td.pop
        val cells = listOf(
            metricCell(R.drawable.ic_drop, L.s("hum"), c.hum.roundToInt().toString(), "%", meter((c.hum / 100).toFloat()), 0, c.hum.roundToInt().toFloat()),
            metricCell(R.drawable.ic_air, L.s("aq"), aqVal, "AQI", aqSub, 1, aqNum),
            metricCell(R.drawable.ic_wind, L.s("wind"), Wx.ws(c.wind).roundToInt().toString(), Wx.wUnit(), windSub, 2, Wx.ws(c.wind).roundToInt().toFloat()),
            metricCell(R.drawable.ic_gauge, L.s("pres"), c.pres.roundToInt().toString(), "hPa", u.text(L.list("presN")[pi], 12.5f, p.muted), 3, c.pres.roundToInt().toFloat()),
            metricCell(R.drawable.ic_uv, L.s("uv"), if (uv == null) "—" else String.format(java.util.Locale.US, "%.1f", uv), "", u.text(uvn, 12.5f, p.muted), 4, uv?.let { (it * 10).roundToInt() / 10f }),
            metricCell(R.drawable.ic_rain, L.s("pop"), pop?.roundToInt()?.toString() ?: "—", "%", meter(((pop ?: 0.0) / 100).toFloat()), 5, pop?.roundToInt()?.toFloat())
        )
        for (r in 0 until 3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until 2) row.addView(cells[r * 2 + k], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (r > 0) metrics.addView(View(this).apply { setBackgroundColor(p.line) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(1)))
            metrics.addView(row)
        }
    }

    // ------------------------------------------------------------------ polen
    private fun renderPollen() {
        val w = data ?: return
        pollenBox.removeAllViews()
        val pol = w.pollen
        if (pol == null) { pollenBox.addView(u.text(L.s("pollenNA"), 14f, p.muted).apply { setLineSpacing(0f, 1.25f) }); return }
        val colors = listOf(p.muted, p.good, p.warn, p.bad, 0xFF8E2A8E.toInt())
        val items = Pollen.types.map { (k, g) -> Triple(k, pol[k], Pollen.level(pol[k], g)) }
        val top = items.maxByOrNull { it.third }!!
        val names = L.map("pollenN"); val lv = L.list("pollenLv")
        val risk = u.text("${L.s("pollenRisk")}: ${lv[top.third]}", 14f, p.fg, 600).apply {
            background = rounded(mix(colors[top.third], p.surface, .82f), dp(999f)); setPadding(u.dp(12), u.dp(6), u.dp(12), u.dp(6))
        }
        pollenBox.addView(risk, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pollenBox.addView(u.text(if (top.third > 0) L.pollenTop(names[top.first] ?: "", lv[top.third]) else L.s("pollenNone"), 13.5f, p.muted), u.lp(topMargin = u.dp(8)))
        for (r in 0 until 3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until 2) {
                val (key, v, level) = items[r * 2 + k]
                val bars = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (i in 1..4) addView(View(context).apply { background = rounded(if (level >= i) colors[level] else p.line, dp(9f)) }, LinearLayout.LayoutParams(0, u.dp(5), 1f).apply { if (i > 1) marginStart = u.dp(3) })
                }
                val cell = u.col(6, u.text(names[key] ?: key, 14f, p.fg, 600), bars, u.text("${lv[level]} · ${v?.roundToInt() ?: "—"} ${L.s("pollenUnit")}", 12f, p.muted)).apply {
                    background = rounded(p.surface2, dp(14f)); setPadding(u.dp(12), u.dp(12), u.dp(12), u.dp(12))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (k > 0) marginStart = u.dp(10) })
            }
            pollenBox.addView(row, u.lp(topMargin = u.dp(10)))
        }
        pollenBox.addView(u.text(L.s("pollenFoot"), 12f, p.muted).apply { setLineSpacing(0f, 1.25f) }, u.lp(topMargin = u.dp(10)))
    }

    // ------------------------------------------------------------------ grafik, güneş, ay
    private fun chartCard(): View {
        chart = ChartView(this, p)
        segChart = Seg(this, p, listOf("h" to L.s("h24"), "d" to L.s("d7")), mode, panel = true) { mode = it; chart.daily = it == "d" }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(title(R.drawable.ic_chart, L.s("chartT")), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(segChart)
        return u.card(u.col(10, head, chart))
    }

    private fun sunCard(): View {
        sunView = SunView(this, p); sunState = u.text("", 13f, p.muted)
        sunRows = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(title(R.drawable.ic_sunrise, L.s("sunT")), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); head.addView(sunState)
        return u.card(u.col(8, head, sunView, sunRows))
    }

    private fun moonCard(): View {
        moonBig = MoonView(this, p, false); moonRow = MoonView(this, p, true)
        moonInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(moonBig); addView(moonInfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = u.dp(18) }) }
        val sep = View(this).apply { setBackgroundColor(p.line) }
        return u.card(u.col(12, title(R.drawable.ic_moon, L.s("moonT")), top, sep.also { it.layoutParams = u.lp(h = u.dp(1)) }, moonRow).also { col ->
            (sep.layoutParams as LinearLayout.LayoutParams).height = u.dp(1); col.updateViewLayout(sep, sep.layoutParams)
        })
    }

    private fun stat(label: String, value: String, icon: Int?): View {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (icon != null) head.addView(iconView(icon, 13, p.sun))
        head.addView(u.text(label, 12f, p.muted), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (icon != null) marginStart = u.dp(5) })
        return u.col(2, head, u.text(value, 19f, p.fg, 600, true))
    }

    private fun renderSunMoon() {
        val w = data ?: return; val td = w.daily.first()
        sunView.w = w
        fun dur(ms: Long) = L.dur((ms / 3600_000).toInt(), ((ms % 3600_000) / 60_000.0).roundToInt())
        val up = w.now > td.sunrise && w.now < td.sunset
        sunState.text = if (up) L.sunsetIn(dur(td.sunset - w.now)) else if (w.now < td.sunrise) L.sunriseIn(dur(td.sunrise - w.now)) else L.s("sunGone")
        sunRows.removeAllViews()
        listOf(stat(L.s("sunrise"), Wx.hm(td.sunrise), R.drawable.ic_sunrise), stat(L.s("sunset"), Wx.hm(td.sunset), R.drawable.ic_sunset), stat(L.s("dayLen"), dur(td.sunset - td.sunrise), R.drawable.ic_clock))
            .forEach { sunRows.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        val age = Moon.age(System.currentTimeMillis()); val ph = age / Moon.SYN; val ill = (1 - cos(2 * Math.PI * ph)) / 2
        moonBig.phase = ph; moonRow.phase = ph
        val idx = (ph * 8).roundToInt() % 8
        fun next(days: Double) = Wx.dayMonth((System.currentTimeMillis() + w.off + days * 86400_000).toLong())
        moonInfo.removeAllViews()
        moonInfo.addView(u.text(L.list("moonN")[idx], 22f, p.fg, 600, true))
        moonInfo.addView(u.text("${L.lit((ill * 100).roundToInt())} · ${L.age(String.format(java.util.Locale.US, "%.1f", age))}", 13.5f, p.muted), u.lp(topMargin = u.dp(3)))
        val nx = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nx.addView(stat(L.s("nextFull"), next(((.5 - ph + 1) % 1) * Moon.SYN), null), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nx.addView(stat(L.s("nextNew"), next((1 - ph) * Moon.SYN), null), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        moonInfo.addView(nx, u.lp(topMargin = u.dp(10)))
    }

    // ------------------------------------------------------------------ dünya küresi
    private fun globeCard(): View {
        val spaceFg = 0xFFE8EEF7.toInt(); val spaceMuted = 0xFF9AA9C0.toInt()
        val sp = Palette(true, 0, 0x14FFFFFF, 0x14FFFFFF, 0x1FFFFFFF, spaceFg, spaceMuted, 0xFFE8EEF7.toInt(), 0, 0, 0, 0, 0)
        val seg = Seg(this, sp, listOf("temp" to L.s("gTemp"), "wind" to L.s("gWind"), "rain" to L.s("gRain"), "aqi" to L.s("gAqi")), globeLayer, panel = false, selText = 0xFF0A1328.toInt()) { setGlobeLayer(it) }
        val segScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(seg) }
        val box = FrameLayout(this)
        val gv = GlobeView(this) { lat, lon, x, y -> globeTap(lat, lon, x, y) }
        globe = gv
        box.addView(gv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(340)))
        globeTip = u.text("", 12.5f, 0xFF0A1328.toInt(), 500).apply { background = rounded(0xF2E8EEF7.toInt(), dp(10f)); setPadding(u.dp(10), u.dp(6), u.dp(10), u.dp(6)); visibility = View.GONE }
        box.addView(globeTip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        globeTitle = u.text("", 12.5f, spaceMuted)
        globeBar = View(this)
        globeTicks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        globeState = u.text("", 12.5f, spaceMuted)
        val foot = u.col(5, globeTitle, globeBar.also { it.layoutParams = u.lp(h = u.dp(8)) }, globeTicks,
            u.text(L.s("globeHint2"), 12.5f, spaceMuted).also { it.layoutParams = u.lp(topMargin = u.dp(6)) }, globeState)
        (globeBar.layoutParams as LinearLayout.LayoutParams).height = u.dp(8)
        val col = u.col(10, title(R.drawable.ic_globe, L.s("globeT"), color = spaceFg, iconTint = 0xFF7FB2FF.toInt()), segScroll, box, foot)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(u.dp(18), u.dp(18), u.dp(18), u.dp(18))
            background = rounded(0xFF071026.toInt(), dp(22f)); clipToOutline = true; addView(col)
        }
        globeLegend()
        return card
    }

    private fun globeTap(lat: Double, lon: Double, x: Float, y: Float) {
        val v = globeVals[globeLayer] ?: return
        val value = Globe.field(v)(lat, lon)
        val tr = L.lang == "tr"
        val ns = if (lat >= 0) (if (tr) "K" else "N") else (if (tr) "G" else "S"); val ew = if (lon >= 0) (if (tr) "D" else "E") else (if (tr) "B" else "W")
        globeTip.text = "${Globe.valueText(globeLayer, if (globeLayer == "temp") value else maxOf(0.0, value))} · ${abs(lat).roundToInt()}°$ns ${abs(lon).roundToInt()}°$ew"
        globeTip.visibility = View.VISIBLE
        globeTip.post {
            globeTip.x = (x - globeTip.width / 2f).coerceIn(0f, ((globeTip.parent as View).width - globeTip.width).toFloat())
            globeTip.y = (y - globeTip.height - u.dp(12)).coerceAtLeast(0f)
        }
        main.removeCallbacksAndMessages(TIP_TOKEN)
        main.postAtTime({ globeTip.visibility = View.GONE }, TIP_TOKEN, android.os.SystemClock.uptimeMillis() + 3000)
    }

    private fun globeLegend() {
        val sc = Globe.scales.getValue(globeLayer); val lo = sc.ticks.first(); val hi = sc.ticks.last()
        val unit = when (globeLayer) { "temp" -> " (${Wx.tUnit()})"; "wind" -> " (${Wx.wUnit()})"; else -> "" }
        globeTitle.text = (L.map("gTitles")[globeLayer] ?: "") + unit
        val stops = sc.stops.filter { it.first in lo..hi }
        globeBar.background = object : android.graphics.drawable.Drawable() {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(c: Canvas) {
                val b = bounds
                paint.shader = LinearGradient(b.left.toFloat(), 0f, b.right.toFloat(), 0f, stops.map { it.second }.toIntArray(), stops.map { ((it.first - lo) / (hi - lo)).toFloat() }.toFloatArray(), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(b), b.height() / 2f, b.height() / 2f, paint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        globeTicks.removeAllViews()
        sc.ticks.forEachIndexed { i, v ->
            globeTicks.addView(u.text(Globe.tickText(globeLayer, v), 12f, 0xFF9AA9C0.toInt()).apply { gravity = if (i == 0) Gravity.START else if (i == sc.ticks.size - 1) Gravity.END else Gravity.CENTER }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun globeStateText(loading: Boolean = false) {
        val live = globeLive[globeLayer] == true
        globeState.text = if (loading) "● " + L.s("gLoading") else if (live) "● " + L.s("gLive") else "● " + L.s("gSample")
        globeState.setTextColor(if (loading) 0xFF9AA9C0.toInt() else if (live) 0xFF4CC38A.toInt() else 0xFFE3B341.toInt())
    }

    private fun setGlobeLayer(l: String) {
        globeLayer = l; globeLegend(); globeTip.visibility = View.GONE
        val cached = globeVals[l]
        if (cached != null) { paintGlobe(); return }
        globeStateText(true)
        val live = data?.live == true
        io.execute {
            var got: Map<String, DoubleArray>? = null
            if (live) got = runCatching { Api.globe(if (l == "aqi") "aqi" else "wx") }.getOrNull()
            main.post {
                if (got != null) got.forEach { (k, v) -> globeVals[k] = v; globeLive[k] = true } else globeVals[l] = Demo.globe(l)
                if (globeLayer == l) paintGlobe()
            }
        }
    }

    private fun paintGlobe() {
        val l = globeLayer; val v = globeVals[l] ?: return
        globeStateText()
        val g = globe ?: return
        io.execute { val bm = Globe.paint(this, l, v); main.post { if (globeLayer == l) g.setTexture(bm) } }
    }

    private fun globeVisibility() {
        val g = globe ?: return
        val r = android.graphics.Rect()
        val vis = g.getGlobalVisibleRect(r)
        g.renderMode = if (vis) android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY else android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    // ------------------------------------------------------------------ yağış radarı
    private fun radarCard(): View {
        radar = RadarView(this, p)
        radarMsg = u.text("", 14f, p.muted).apply { gravity = Gravity.CENTER; setBackgroundColor(withAlpha(p.surface2, .9f)); setPadding(u.dp(20), u.dp(20), u.dp(20), u.dp(20)); visibility = View.GONE }
        val box = FrameLayout(this).apply { background = rounded(p.surface2, dp(16f), p.line, u.dp(1)); clipToOutline = true }
        box.addView(radar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val zoom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(p.surface, dp(12f), p.line, u.dp(1)); clipToOutline = true
            addView(u.text("+", 20f, p.fg, 500).apply { gravity = Gravity.CENTER; setOnClickListener { radar.zoomBy(1) } }, LinearLayout.LayoutParams(u.dp(38), u.dp(38)))
            addView(View(context).apply { setBackgroundColor(p.line) }, LinearLayout.LayoutParams(u.dp(38), u.dp(1)))
            addView(u.text("−", 20f, p.fg, 500).apply { gravity = Gravity.CENTER; setOnClickListener { radar.zoomBy(-1) } }, LinearLayout.LayoutParams(u.dp(38), u.dp(38)))
        }
        box.addView(zoom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(u.dp(10), u.dp(10), 0, 0) })
        box.addView(radarMsg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        playBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_play); imageTintList = ColorStateList.valueOf(p.surface); background = rounded(p.accent, dp(999f))
            setPadding(u.dp(11), u.dp(11), u.dp(11), u.dp(11)); isEnabled = false; alpha = .4f
            setOnClickListener { setPlay(!radarPlaying) }
        }
        seek = SeekBar(this).apply {
            isEnabled = false; progressTintList = ColorStateList.valueOf(p.accent); thumbTintList = ColorStateList.valueOf(p.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) { setPlay(false); showFrame(v) } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        rtime = u.text("--:--", 14f, p.fg, 600)
        val ctl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ctl.addView(playBtn, LinearLayout.LayoutParams(u.dp(40), u.dp(40)))
        ctl.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ctl.addView(rtime)
        val scale = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(u.text(L.s("light"), 12.5f, p.muted))
            addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(0xFF9BE1F2.toInt(), 0xFF2FA4E0.toInt(), 0xFF0C5FB8.toInt(), 0xFFF5E600.toInt(), 0xFFF59A00.toInt(), 0xFFF04400.toInt(), 0xFFB80000.toInt(), 0xFFF2A8F2.toInt())).apply { cornerRadius = dp(9f) }
            }, LinearLayout.LayoutParams(u.dp(120), u.dp(8)).apply { marginStart = u.dp(8); marginEnd = u.dp(8) })
            addView(u.text(L.s("heavy"), 12.5f, p.muted))
        }
        return u.card(u.col(10, title(R.drawable.ic_radar, L.s("radarT"), L.s("radarSub")), box, ctl, scale))
    }

    private val radarTick = object : Runnable {
        override fun run() { if (!radarPlaying || radar.frames.isEmpty()) return; showFrame((radarIdx + 1) % radar.frames.size); main.postDelayed(this, 900) }
    }

    private fun setPlay(on: Boolean) {
        if (!::playBtn.isInitialized) return
        radarPlaying = on && radar.frames.isNotEmpty(); main.removeCallbacks(radarTick)
        playBtn.setImageResource(if (radarPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        playBtn.contentDescription = L.s(if (radarPlaying) "pause" else "play")
        if (radarPlaying) main.postDelayed(radarTick, 900)
    }

    private fun showFrame(i: Int) {
        val f = radar.frames.getOrNull(i) ?: return
        radarIdx = i; radar.frame = i; seek.progress = i
        rtime.text = Wx.hm(f.first * 1000 + (data?.off ?: 0L))
    }

    private fun radarFocus() {
        radar.center(city.lat, city.lon)
        val live = data?.live == true
        radar.enabledNet = live
        if (!live) { radarMsg.text = L.s("mapErrB") + "\n" + L.s("mapErrT"); radarMsg.visibility = View.VISIBLE; return }
        radarMsg.visibility = View.GONE
        if (radar.frames.isNotEmpty()) return
        io.execute {
            val r = runCatching { Api.radar() }.getOrNull()
            main.post {
                if (r == null || r.frames.isEmpty()) { radarMsg.text = L.s("radarErr"); radarMsg.visibility = View.VISIBLE; return@post }
                radar.host = r.host; radar.frames = r.frames
                seek.max = r.frames.size - 1; seek.isEnabled = true; playBtn.isEnabled = true; playBtn.alpha = 1f
                showFrame(r.frames.size - 1)
            }
        }
    }

    // ------------------------------------------------------------------ veri yükleme
    private fun go(c: City) { factOff = 0; syncPickers(c); load(c) }

    private fun load(c: City) {
        city = c; Prefs.city = c
        val seq = ++loadSeq
        io.execute {
            val w = runCatching { Api.forecast(c) }.getOrNull() ?: Demo.weather(c)
            if (w.live) { Prefs.saveWeather(c, w) }
            main.post {
                if (seq != loadSeq || isFinishing) return@post
                val changed = lastKey != null && lastKey != c.key
                data = w; lastKey = c.key
                render()
                if (changed) riseIn()
                loadFavs()
                if (w.live) { HavaWidget.updateAll(this); Notify.evaluate(this, c, w) }
            }
        }
    }

    private fun render() {
        val w = data ?: return
        note.visibility = if (w.live) View.GONE else View.VISIBLE
        renderHero(); renderAct(); renderFact(); renderMetrics(); renderPollen()
        chart.w = w; tenDay.w = w; renderSunMoon(); renderFavs()
        globe?.focus(city.lat, city.lon)
        if (globeVals.isEmpty() || globeLive[globeLayer] != true && w.live) { globeVals.clear(); setGlobeLayer(globeLayer) }
        radarFocus()
    }

    private fun riseIn() {
        if (!animationsOn()) return
        listOf(heroContent, metrics, actTitle.parent as View, factText.parent as View).forEach { v ->
            v.alpha = .35f; v.translationY = dp(8f); v.animate().alpha(1f).translationY(0f).setDuration(450).start()
        }
    }

    // ------------------------------------------------------------------ ayarlar
    private fun settings() {
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val col = u.col(14)
        col.setPadding(u.dp(20), u.dp(20), u.dp(20), u.dp(20)); col.background = rounded(p.surface, dp(22f))
        col.addView(u.text(L.s("settings"), 20f, p.fg, 600, true))
        fun section(t: String, v: View) = u.col(8, u.label(t), v)
        var dirty = false
        col.addView(section(L.s("units"), u.row(8,
            Seg(this, p, listOf("c" to "°C", "f" to "°F"), Prefs.unitT) { Prefs.unitT = it; dirty = true },
            Seg(this, p, listOf("kmh" to L.s("kmh"), "mph" to "mph"), Prefs.unitW) { Prefs.unitW = it; dirty = true })), u.lp(topMargin = u.dp(14)))
        col.addView(section(L.s("theme"), Seg(this, p, listOf("auto" to L.s("thAuto"), "light" to L.s("thLight"), "dark" to L.s("thDark")), Prefs.theme) { Prefs.theme = it; dirty = true }), u.lp(topMargin = u.dp(14)))
        col.addView(section(L.s("langT"), Seg(this, p, listOf("tr" to "Türkçe", "en" to "English"), Prefs.lang) { Prefs.lang = it; dirty = true }), u.lp(topMargin = u.dp(14)))
        fun sw(t: String, sub: String, on: Boolean, set: (Boolean) -> Unit): View {
            val s = Switch(this)
            s.isChecked = on; s.thumbTintList = ColorStateList.valueOf(if (on) p.accent else p.muted)
            s.setOnCheckedChangeListener { _, c -> set(c); s.thumbTintList = ColorStateList.valueOf(if (c) p.accent else p.muted); if (c) askNotif() }
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(u.col(2, u.text(t, 15f, p.fg, 600), u.text(sub, 12.5f, p.muted)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(s); return r
        }
        val notifCol = u.col(10, sw(L.s("notifDaily"), L.s("notifDailySub"), Prefs.notifDaily) { Prefs.notifDaily = it }, sw(L.s("notifAlerts"), L.s("notifAlertsSub"), Prefs.notifAlerts) { Prefs.notifAlerts = it })
        if (!Notify.allowed(this)) notifCol.addView(u.text(L.s("notifOff"), 12.5f, p.bad).apply { setOnClickListener { askNotif() } })
        col.addView(section(L.s("notifT"), notifCol), u.lp(topMargin = u.dp(14)))
        col.addView(u.text("HavaDuru 1.0 · Open-Meteo · RainViewer · BigDataCloud", 12f, p.muted), u.lp(topMargin = u.dp(18)))
        d.setContentView(ScrollView(this).apply { addView(col) })
        d.window?.setBackgroundDrawable(ColorDrawable(0))
        d.window?.setLayout((resources.displayMetrics.widthPixels * .92f).toInt().coerceAtMost(u.dp(480)), WindowManager.LayoutParams.WRAP_CONTENT)
        d.setOnDismissListener {
            if (dirty) {
                Notify.channels(this); HavaWidget.updateAll(this)
                val s = scroll.scrollY; build(); data?.let { render() }; scroll.post { scroll.scrollTo(0, s) }; globe?.onResume()
            }
        }
        d.show()
    }

    private fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    // ------------------------------------------------------------------ paylaşım kartı
    private fun shareDialog() {
        val w = data ?: return
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var story = true; var bm: Bitmap? = null
        val preview = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val prevBox = FrameLayout(this).apply { background = rounded(p.surface2, dp(14f)); setPadding(u.dp(10), u.dp(10), u.dp(10), u.dp(10)); addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(380))) }
        fun build() { bm = ShareCard.render(this, city, w, story); preview.setImageBitmap(bm) }
        val seg = Seg(this, p, listOf("s" to L.s("story"), "q" to L.s("square")), "s", panel = true) { story = it == "s"; build() }
        fun btn(t: String, icon: Int, primary: Boolean, click: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = rounded(if (primary) p.accent else p.surface, dp(999f), if (primary) p.accent else p.line, u.dp(1))
            addView(iconView(icon, 16, if (primary) p.surface else p.fg))
            addView(u.text(t, 14.5f, if (primary) p.surface else p.fg, 600), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = u.dp(8) })
            setOnClickListener { click() }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(btn(L.s("share"), R.drawable.ic_share, true) {
            bm?.let { runCatching { startActivity(ShareCard.shareIntent(ShareCard.toShareUri(this, it, ShareCard.fileName(city, w, story)))) } }
        }, LinearLayout.LayoutParams(0, u.dp(46), 1f))
        if (Build.VERSION.SDK_INT >= 29) actions.addView(btn(L.s("save"), R.drawable.ic_download, false) {
            bm?.let { toast(if (ShareCard.saveToGallery(this, it, ShareCard.fileName(city, w, story))) L.s("savedGallery") else L.s("saveFail")) }
        }, LinearLayout.LayoutParams(0, u.dp(46), 1f).apply { marginStart = u.dp(8) })
        val col = u.col(12, u.text(L.s("shareT"), 18f, p.fg, 600, true), seg, prevBox, actions)
        col.setPadding(u.dp(18), u.dp(18), u.dp(18), u.dp(18)); col.background = rounded(p.surface, dp(22f))
        d.setContentView(ScrollView(this).apply { addView(col) })
        d.window?.setBackgroundDrawable(ColorDrawable(0))
        d.window?.setLayout((resources.displayMetrics.widthPixels * .92f).toInt().coerceAtMost(u.dp(480)), WindowManager.LayoutParams.WRAP_CONTENT)
        build(); d.show()
    }

    companion object {
        private const val REQ_LOC = 11
        private const val REQ_NOTIF = 12
        private val TIP_TOKEN = Any()
    }
}

/** Web'deki segmentli düğme grubu (.seg). */
@android.annotation.SuppressLint("ViewConstructor")
class Seg(ctx: Context, private val p: Palette, private val items: List<Pair<String, String>>, sel: String, private val panel: Boolean = false, private val selText: Int? = null, private val onPick: (String) -> Unit) : LinearLayout(ctx) {
    private var current = sel
    private val views = ArrayList<TextView>()

    init {
        orientation = HORIZONTAL; val d = ctx.resources.displayMetrics.density
        background = rounded(if (panel) p.surface2 else p.surface, 999 * d, p.line, if (panel) 0 else d.toInt().coerceAtLeast(1))
        setPadding((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
        items.forEach { (k, label) ->
            val t = TextView(ctx).apply {
                text = label; textSize = 13.5f; typeface = Fonts.body(ctx, 500); gravity = Gravity.CENTER
                setPadding((13 * d).toInt(), (7 * d).toInt(), (13 * d).toInt(), (7 * d).toInt())
                setOnClickListener { if (current != k) { current = k; paint(); onPick(k) } }
            }
            views += t; addView(t)
        }
        paint()
    }

    private fun paint() {
        val d = resources.displayMetrics.density
        views.forEachIndexed { i, t ->
            val on = items[i].first == current
            t.isSelected = on
            t.background = if (on) rounded(if (panel) p.surface else p.accent, 999 * d) else null
            t.setTextColor(if (on) (selText ?: if (panel) p.fg else 0xFFFFFFFF.toInt()) else p.muted)
            t.elevation = if (on && panel) d else 0f
        }
    }
}

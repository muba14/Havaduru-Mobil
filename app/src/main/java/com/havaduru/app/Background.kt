package com.havaduru.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** Bildirim kanalları ve kuralları. */
object Notify {
    private const val CH_DAILY = "daily"
    private const val CH_ALERTS = "alerts"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, L.s("chDaily"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, L.s("chAlerts"), NotificationManager.IMPORTANCE_HIGH))
    }

    fun allowed(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (ctx.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false)

    private fun post(ctx: Context, id: Int, ch: String, title: String, text: String, iconRes: Int) {
        if (!allowed(ctx)) return
        channels(ctx)
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(drawableBitmap(ctx, iconRes, 128))
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setColor(0xFF2466E0.toInt())
            .setContentIntent(pi).setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    fun drawableBitmap(ctx: Context, res: Int, size: Int): Bitmap {
        val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        ctx.getDrawable(res)?.let { it.setBounds(0, 0, size, size); it.draw(Canvas(bm)) }
        return bm
    }

    /** Sabah özeti (07:00–11:00 arası, günde bir) ve uyarılar (her tür günde bir). Yalnızca canlı veriyle. */
    fun evaluate(ctx: Context, city: City, w: Weather) {
        if (!w.live) return
        val day = Wx.isoDay(w.now)
        val hour = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = w.now }.get(Calendar.HOUR_OF_DAY)
        val name = Wx.cityName(city)
        val td = w.daily.firstOrNull() ?: return
        val kind = Wx.kindOf(w.cur.code)
        if (Prefs.notifDaily && hour in 7..10 && !Prefs.sentToday("daily", day)) {
            val (_, key) = Suggest.pick(kind, w.cur.t, w.cur.isDay)
            val pop = td.pop?.let { " · ${L.s("rain")} ${Wx.pct(it.roundToInt())}" } ?: ""
            post(ctx, 1, CH_DAILY, L.f3("dailyT", name, Wx.deg(w.cur.t), L.code(w.cur.code)),
                "${L.s("today")}: ${Wx.deg(td.min)} / ${Wx.deg(td.max)}$pop\n${L.act(key)[0]}", Wx.iconRes(kind, w.cur.isDay))
            Prefs.markSent("daily", day)
        }
        if (!Prefs.notifAlerts) return
        w.hourly.take(13).firstOrNull { Wx.kindOf(it.code) == Kind.STORM }?.let { h ->
            if (!Prefs.sentToday("storm", day)) { post(ctx, 10, CH_ALERTS, "${L.s("alStorm")} · $name", L.f1("alStormT", Wx.hm(h.t)), R.drawable.wx_storm); Prefs.markSent("storm", day) }
        }
        val tk = Wx.kindOf(td.code)
        if ((td.pop ?: 0.0) >= 80 && (tk == Kind.RAIN || tk == Kind.STORM) && !Prefs.sentToday("rain", day)) {
            post(ctx, 11, CH_ALERTS, "${L.s("alRain")} · $name", L.f1("alRainT", Wx.pct(td.pop!!.roundToInt())), R.drawable.wx_rain); Prefs.markSent("rain", day)
        }
        if (td.max >= 35 && !Prefs.sentToday("hot", day)) {
            post(ctx, 12, CH_ALERTS, "${L.s("alHot")} · $name", L.f1("alHotT", Wx.deg(td.max)), R.drawable.act_wave); Prefs.markSent("hot", day)
        }
        if (td.min <= 0 && !Prefs.sentToday("frost", day)) {
            post(ctx, 13, CH_ALERTS, "${L.s("alFrost")} · $name", L.f1("alFrostT", Wx.deg(td.min)), R.drawable.wx_snow); Prefs.markSent("frost", day)
        }
        if (w.cur.wind >= 50 && !Prefs.sentToday("wind", day)) {
            post(ctx, 14, CH_ALERTS, "${L.s("alWind")} · $name", L.f1("alWindT", "${Wx.ws(w.cur.wind).roundToInt()} ${Wx.wUnit()}"), R.drawable.wx_cloudy); Prefs.markSent("wind", day)
        }
    }
}

/** Saatlik arka plan işi: hava durumunu yeniler, widget'ı günceller, bildirimleri değerlendirir. */
class WeatherJob : JobService() {
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val ctx = applicationContext
        worker = Thread {
            Prefs.init(ctx)
            val city = Prefs.city
            val w = runCatching { Api.forecast(city) }.getOrNull()
            if (w != null) Prefs.saveWeather(city, w)
            HavaWidget.updateAll(ctx)
            Prefs.loadWeather()?.let { (c, cached) -> if (w != null) Notify.evaluate(ctx, c, cached) }
            jobFinished(params, w == null)
        }.also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { worker?.interrupt(); return true }

    companion object {
        private const val PERIODIC = 1001
        private const val NOW = 1002

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(PERIODIC) != null) return
            js.schedule(JobInfo.Builder(PERIODIC, ComponentName(ctx, WeatherJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60 * 60 * 1000L, 15 * 60 * 1000L)
                .setPersisted(true)
                .build())
        }

        fun runNow(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            js.schedule(JobInfo.Builder(NOW, ComponentName(ctx, WeatherJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(10_000L)
                .build())
        }
    }
}

/** Ana ekran widget'ı: gökyüzü sahnesi arka planı, şehir, derece, durum, en yüksek/en düşük. */
class HavaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Prefs.init(ctx)
        ids.forEach { render(ctx, mgr, it) }
        WeatherJob.schedule(ctx); WeatherJob.runNow(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        Prefs.init(ctx); render(ctx, mgr, id)
    }

    companion object {
        fun updateAll(ctx: Context) {
            Prefs.init(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, HavaWidget::class.java)).forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_weather)
            val opts = mgr.getAppWidgetOptions(id)
            val den = ctx.resources.displayMetrics.density
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0).takeIf { it > 0 } ?: 300
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: 130
            val pw = (wDp * den).toInt().coerceIn(200, 1000); val ph = (hDp * den).toInt().coerceIn(120, 600)
            val data = Prefs.loadWeather()
            val kind = data?.let { Wx.kindOf(it.second.cur.code) } ?: Kind.PARTLY
            val day = data?.second?.cur?.isDay ?: true
            val bg = SkyScene.still(kind, day, pw, ph, den, 22 * den)
            // okunabilirlik için soldan koyulaşan katman
            Canvas(bg).drawRect(0f, 0f, pw.toFloat(), ph.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, pw.toFloat(), 0f, 0x8C050A18.toInt(), 0x00050A18, Shader.TileMode.CLAMP)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
            })
            rv.setImageViewBitmap(R.id.w_bg, bg)
            if (data != null) {
                val (c, w) = data
                rv.setTextViewText(R.id.w_city, Wx.cityName(c))
                rv.setTextViewText(R.id.w_temp, "${Wx.tc(w.cur.t).roundToInt()}°")
                rv.setTextViewText(R.id.w_cond, L.code(w.cur.code))
                val td = w.daily.firstOrNull()
                rv.setTextViewText(R.id.w_hilo, if (td != null) "↑ ${Wx.deg(td.max)}   ↓ ${Wx.deg(td.min)}" else "")
                rv.setImageViewResource(R.id.w_icon, Wx.iconRes(kind, day))
                rv.setTextViewText(R.id.w_time, Wx.hm(w.now))
            } else {
                rv.setTextViewText(R.id.w_city, "HavaDuru"); rv.setTextViewText(R.id.w_temp, "–")
                rv.setTextViewText(R.id.w_cond, L.s("tapToOpen")); rv.setTextViewText(R.id.w_hilo, ""); rv.setTextViewText(R.id.w_time, "")
                rv.setImageViewResource(R.id.w_icon, R.drawable.wx_partly_day)
            }
            rv.setViewVisibility(R.id.w_hilo, if (hDp >= 100) View.VISIBLE else View.GONE)
            val pi = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.w_root, pi)
            mgr.updateAppWidget(id, rv)
        }
    }
}

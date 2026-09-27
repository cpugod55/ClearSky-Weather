package com.clearsky.weather

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class WeatherStatusWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val p = applicationContext.getSharedPreferences("prefs", 0)
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return Result.success()
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return Result.success()
        val name = p.getString("name", "Current location") ?: "Current location"
        val w = runCatching { WeatherRepository.load(name, lat, lon) }.getOrNull() ?: return Result.retry()
        WeatherUiStore.save(applicationContext, w)
        show(applicationContext, w)
        WeatherWidget.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "weather_status"
        fun schedule(c: Context) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("weather_status", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<WeatherStatusWorker>(1, TimeUnit.HOURS).build())
        }
        fun show(c: Context, w: Weather) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = c.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Current weather", NotificationManager.IMPORTANCE_LOW))
            val openWeather = Intent(c, MainActivity::class.java).apply {
                action = "com.clearsky.weather.OPEN_WEATHER"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                c,
                1101,
                openWeather,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("${w.currentTemp.toInt()}° • ${w.name}")
                .setContentText("${WeatherRepository.label(w.code)} • Feels ${w.apparent.toInt()}°")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build()
            nm.notify(1101, n)
        }
    }
}

object WeatherUiStore {
    fun save(c: Context, w: Weather) {
        val e = c.getSharedPreferences("prefs", 0).edit()
            .putString("lat", w.lat.toString()).putString("lon", w.lon.toString()).putString("name", w.name)
            .putString("temp", "${w.currentTemp.toInt()}°").putString("condition", WeatherRepository.label(w.code)).putInt("code", w.code)
        w.days.take(5).forEachIndexed { i, d -> e.putString("day$i", "${shortDay(d.date)}\n${d.hi.toInt()}°  ${d.lo.toInt()}°").putInt("day${i}_code", d.code) }
        e.apply()
    }
    private fun shortDay(s: String) = runCatching { java.time.LocalDate.parse(s).format(java.time.format.DateTimeFormatter.ofPattern("EEE")) }.getOrDefault(s)
}

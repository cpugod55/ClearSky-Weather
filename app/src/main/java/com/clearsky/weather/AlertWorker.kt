package com.clearsky.weather

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class AlertWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("prefs", 0)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return Result.success()
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return Result.success()
        val alerts = runCatching { WeatherRepository.nwsAlerts(lat, lon) }.getOrDefault(emptyList())
        if (alerts.isEmpty()) return Result.success()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = "weather_alerts"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channel, "Weather alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val alert = alerts.first()
        val openAlert = Intent(applicationContext, MainActivity::class.java).apply {
            action = "com.clearsky.weather.OPEN_ALERT.${alert.id}"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("alert_id", alert.id)
            putExtra("alert_event", alert.event)
            putExtra("alert_headline", alert.headline)
            putExtra("alert_severity", alert.severity)
            putExtra("alert_description", alert.description)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            alert.id.hashCode(),
            openAlert,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = alert.headline.ifBlank { alert.description.take(180) }
        val notification = Notification.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(alert.event)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(alert.id.hashCode(), notification)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "alerts",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AlertWorker>(3, TimeUnit.HOURS).build()
            )
        }
    }
}

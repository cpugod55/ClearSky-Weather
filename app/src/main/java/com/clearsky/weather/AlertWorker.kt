package com.clearsky.weather
import android.app.*
import android.content.Context
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit
class AlertWorker(ctx:Context,p:WorkerParameters):Worker(ctx,p){ override fun doWork():Result{ val sp=applicationContext.getSharedPreferences("prefs",0); val lat=sp.getString("lat",null)?.toDoubleOrNull()?:return Result.success(); val lon=sp.getString("lon",null)?.toDoubleOrNull()?:return Result.success(); val alerts=runCatching{WeatherRepository.nwsAlerts(lat,lon)}.getOrDefault(emptyList()); if(alerts.isNotEmpty()){ val nm=applicationContext.getSystemService(NotificationManager::class.java); val ch="weather_alerts"; if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel(ch,"Weather alerts",NotificationManager.IMPORTANCE_HIGH)); val a=alerts.first(); val n=Notification.Builder(applicationContext,ch).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(a.event).setContentText(a.headline).setStyle(Notification.BigTextStyle().bigText(a.headline)).setAutoCancel(true).build(); nm.notify(a.id.hashCode(),n)}; return Result.success() }
 companion object{ fun schedule(c:Context){WorkManager.getInstance(c).enqueueUniquePeriodicWork("alerts",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<AlertWorker>(3,TimeUnit.HOURS).build())} }
}

package com.clearsky.weather
import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
private fun weatherRes(code:Int)=when(code){0->R.drawable.ic_weather_sun;in 51..67,in 80..82,in 95..99->R.drawable.ic_weather_rain;in 71..77,in 85..86->R.drawable.ic_weather_snow;else->R.drawable.ic_weather_cloud}
private fun fill(c:Context,m:AppWidgetManager,id:Int){val p=c.getSharedPreferences("prefs",0);val v=RemoteViews(c.packageName,R.layout.weather_widget_strip);v.setTextViewText(R.id.widget_place,p.getString("name","ClearSky"));v.setTextViewText(R.id.widget_temp,p.getString("temp","—"));v.setTextViewText(R.id.widget_condition,p.getString("condition","Open ClearSky to refresh"));v.setImageViewResource(R.id.widget_icon,weatherRes(p.getInt("code",3)));val ids=intArrayOf(R.id.widget_day0,R.id.widget_day1,R.id.widget_day2);val icons=intArrayOf(R.id.widget_icon0,R.id.widget_icon1,R.id.widget_icon2);ids.forEachIndexed{i,x->v.setTextViewText(x,p.getString("day${i+1}",""));v.setImageViewResource(icons[i],weatherRes(p.getInt("day${i+1}_code",3)))};v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v)}
class WeatherWidgetStrip:AppWidgetProvider(){override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray)=ids.forEach{fill(c,m,it)}}
object WeatherWidget{fun refreshAll(c:Context){val m=AppWidgetManager.getInstance(c);m.getAppWidgetIds(ComponentName(c,WeatherWidgetStrip::class.java)).forEach{fill(c,m,it)}}}

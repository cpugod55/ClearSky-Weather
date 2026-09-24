package com.clearsky.weather

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object WeatherRepository {
    private val http = OkHttpClient()
    private fun text(url: String): String {
        val r = Request.Builder().url(url).header("User-Agent", "ClearSky/1.1 (private weather app)").build()
        http.newCall(r).execute().use {
            if (!it.isSuccessful) error("Weather service ${it.code}")
            return it.body?.string() ?: error("Empty response")
        }
    }
    private fun j(url: String) = JSONObject(text(url))

    fun search(q: String): List<Place> {
        val x = j("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=8&language=en&format=json")
        val a = x.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Place(listOf(it.optString("name"), it.optString("admin1"), it.optString("country_code")).filter { v -> v.isNotBlank() }.joinToString(", "), it.getDouble("latitude"), it.getDouble("longitude"))
        }
    }

    fun reverseName(lat: Double, lon: Double): String {
        if (lat !in 18.0..72.0 || lon !in -180.0..-60.0) return "Current location"
        return runCatching {
            val props = j("https://api.weather.gov/points/$lat,$lon").getJSONObject("properties")
                .getJSONObject("relativeLocation").getJSONObject("properties")
            val city = props.optString("city")
            val state = props.optString("state")
            listOf(city, state).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Current location" }
        }.getOrDefault("Current location")
    }

    fun load(name: String, lat: Double, lon: Double): Weather {
        val vars = "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,precipitation_probability,precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"
        val daily = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,sunrise,sunset,uv_index_max"
        val f = j("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=$vars&daily=$daily&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch&timezone=auto&forecast_days=10")
        val c = f.getJSONObject("current")
        val currentTime = c.optString("time")
        val currentHour = if (currentTime.length >= 13) currentTime.substring(0, 13) else currentTime
        val h = f.getJSONObject("hourly")
        val ht = h.getJSONArray("time")
        val allHours = (0 until ht.length()).map { i ->
            Hour(ht.getString(i), h.getJSONArray("temperature_2m").getDouble(i), h.getJSONArray("apparent_temperature").getDouble(i), h.getJSONArray("precipitation").getDouble(i), h.getJSONArray("precipitation_probability").optInt(i), h.getJSONArray("weather_code").getInt(i), h.getJSONArray("wind_speed_10m").getDouble(i), h.getJSONArray("wind_direction_10m").optInt(i), h.getJSONArray("wind_gusts_10m").getDouble(i), h.getJSONArray("relative_humidity_2m").getInt(i), h.getJSONArray("dew_point_2m").getDouble(i), h.getJSONArray("surface_pressure").getDouble(i), h.getJSONArray("uv_index").optDouble(i))
        }
        val start = allHours.indexOfFirst { it.time.startsWith(currentHour) }.let { if (it < 0) 0 else it }
        val hours = allHours.drop(start).take(48)
        val d = f.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days = (0 until dt.length()).map { i -> Day(dt.getString(i), d.getJSONArray("temperature_2m_max").getDouble(i), d.getJSONArray("temperature_2m_min").getDouble(i), d.getJSONArray("precipitation_probability_max").optInt(i), d.getJSONArray("precipitation_sum").getDouble(i), d.getJSONArray("sunrise").getString(i), d.getJSONArray("sunset").getString(i), d.getJSONArray("uv_index_max").optDouble(i), d.getJSONArray("weather_code").getInt(i)) }
        val air = runCatching {
            val a = j("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi,pm2_5,ozone&timezone=auto").getJSONObject("current")
            Air(a.optInt("us_aqi"), a.optDouble("pm2_5"), a.optDouble("ozone"))
        }.getOrNull()
        val alerts = if (lat in 18.0..72.0 && lon in -180.0..-60.0) runCatching { nwsAlerts(lat, lon) }.getOrDefault(emptyList()) else emptyList()
        return Weather(name, lat, lon, f.optString("timezone"), c.getDouble("temperature_2m"), c.getDouble("apparent_temperature"), c.getInt("relative_humidity_2m"), c.getDouble("wind_speed_10m"), c.optInt("wind_direction_10m"), c.getDouble("wind_gusts_10m"), c.getDouble("surface_pressure"), c.getInt("weather_code"), hours, days, air, alerts)
    }

    fun nwsAlerts(lat: Double, lon: Double): List<AlertItem> {
        val a = j("https://api.weather.gov/alerts/active?point=$lat,$lon").getJSONArray("features")
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            val p = it.getJSONObject("properties")
            AlertItem(it.optString("id"), p.optString("event"), p.optString("headline"), p.optString("severity"), p.optString("description"))
        }
    }

    fun compass(deg: Int): String {
        val d=((deg%360)+360)%360
        return arrayOf("N","NE","E","SE","S","SW","W","NW")[((d+22)/45)%8]
    }

    fun label(code: Int) = when (code) { 0 -> "Clear"; 1,2 -> "Partly cloudy"; 3 -> "Overcast"; 45,48 -> "Fog"; 51,53,55,56,57 -> "Drizzle"; 61,63,65,66,67,80,81,82 -> "Rain"; 71,73,75,77,85,86 -> "Snow"; 95,96,99 -> "Thunderstorms"; else -> "Weather" }
}

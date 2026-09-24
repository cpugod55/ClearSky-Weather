# ClearSky Weather

Free, privacy-focused Android weather app with forecasts, radar, alerts, widgets, and no ads or tracking.

## Current checkpoint
**v1.3.5** — verified working on the primary test phone. The app waits 30 days before showing a single optional donation request; once shown, it will not automatically ask again.

## Features
- Current conditions and feels-like temperature
- Hourly and 10-day forecasts
- UV, AQI, humidity, dew point, pressure, wind and gusts
- Sunrise/sunset
- U.S. NWS weather alerts
- Six radar modes including current, historical and forecast views
- Saved locations and current GPS location
- Home-screen weather widget
- Dark theme
- No account, ads, analytics, or tracking SDK

## Weather data
Forecast, geocoding and air quality data are provided by Open-Meteo. U.S. alerts and radar use NOAA/NWS resources. Internet providers necessarily receive the IP address and requested coordinates. ClearSky has no backend that stores user locations.

## Android compatibility
Android 11 (API 30) or newer.

## Build
Requires Android SDK 36 and JDK 17+.

```bash
gradle :app:assembleDebug
```

The APK will be generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Support ClearSky
ClearSky is free and has no ads or analytics. Voluntary support is available through GitHub Sponsors for **cpugod55**.

## Developer
Created by **!!ZuEs!! / cpugod55**.

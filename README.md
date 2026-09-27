# ClearSky 1.3.16

ClearSky is a straightforward Android weather app focused on useful weather information without ads, accounts, analytics, or a ClearSky backend.

## Features
- Tappable 10-day forecast with daily temperature/rain graphs and AM/PM hourly details
- Current conditions, feels-like temperature, humidity, pressure, wind, and gusts
- 24-hour forecast and 10-day forecast
- Precipitation, dew point, UV, sunrise, and sunset
- US AQI, PM2.5, and ozone data
- Official NWS active weather alerts in the United States
- Six radar and forecast views
- Current location and manually saved locations
- Home-screen weather widget
- Background weather status and alert checks
- Pull-to-refresh while keeping the last forecast on screen
- Automatic refresh while the app is open and whenever it returns to the foreground

## Privacy
ClearSky has no account system, advertising SDK, analytics SDK, or tracking SDK. Saved locations and support-reminder state remain on the device. Weather and radar requests go directly to their data providers, which receive the requesting IP address and forecast coordinates as part of normal network requests.

## Weather data
Forecast, geocoding, and air-quality data are provided by Open-Meteo. United States alerts and radar data are provided by NOAA/NWS.

## Build
ClearSky requires Android SDK 36 and JDK 17 or newer. Open the project in Android Studio and build the `app` module.

Release signing values are read from Gradle properties:
- `CLEARSKY_STORE_FILE`
- `CLEARSKY_STORE_PASSWORD`
- `CLEARSKY_KEY_ALIAS`
- `CLEARSKY_KEY_PASSWORD`

## Support
ClearSky is free to use with no locked features. After 30 days, Google Play builds may show one optional support reminder. If dismissed, the next automatic reminder is delayed for one year. Any completed support purchase permanently disables automatic reminders. One-time support options are configured in Google Play as $1, $3, and $5 products.

## 1.3.16
- Added optional one-time Google Play support purchases.
- Added $1, $3, and $5 support tiers.
- Kept the first support reminder at 30 days.
- Changed dismissed reminders to return no more than once per year.
- Permanently disables automatic support reminders after a completed support purchase.
- Removed the external GitHub Sponsors purchase path from the in-app support UI.
- Updated to Google Play Billing Library 9.1.0.

## 1.3.15
- Added pull-to-refresh to weather pages.
- Kept the last successful forecast visible while new data loads.
- Added automatic refresh every 15 minutes while the app is active.
- Added a refresh when returning to the app.
- Kept the previous forecast on screen if a refresh fails.
- Cleaned up project wording and version metadata.

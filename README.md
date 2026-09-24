ClearSky v1.3.5 one-time donation prompt

# ClearSky 1.0
Original privacy-first Android weather app inspired by the feature category of Quiet Sky, without copying its code, branding, artwork, or proprietary UI.

## Included
- GPS location and manual city/place search
- Current conditions, feels-like, humidity, pressure, wind and gusts
- 48-hour data / 24-hour card view
- 10-day forecast
- precipitation probability/amount, dew point, UV
- sunrise/sunset
- US AQI, PM2.5 and ozone
- official NWS active alerts in the United States
- interactive animated NWS radar in-app
- home-screen widget
- periodic NWS alert checks/notifications
- on-device last-location cache
- no account, ads, analytics or tracking SDK

## Data
Forecast/geocoding/air quality: Open-Meteo. US alerts/radar: NOAA/NWS. Internet providers necessarily receive the IP address and requested coordinates. No ClearSky backend exists.

## Build
Requires Android SDK 36 and JDK 17+. Open in Android Studio and build the `app` module, or run `./gradlew assembleDebug` after generating/using the Gradle wrapper.

## Support ClearSky
ClearSky is free and has no ads or analytics. The app waits 30 days before showing one automatic donation request, and never automatically asks again after that prompt is dismissed or used. Voluntary support remains available anytime through GitHub Sponsors for `cpugod55`.

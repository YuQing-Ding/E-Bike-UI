# E-Bike UI

## Overview
E-Bike UI is a custom Android launcher designed specifically for electric bikes with Android-powered displays. It provides a clean, transparent interface that shows essential information like current time, weather conditions, and speed, along with quick access to frequently used applications.

> **Note:** This application was specifically optimized for Sony T2 Ultra displays.

## Features
- **Transparent UI**: Overlays your existing wallpaper for a seamless look
- **Real-time Information Display**:
  - 12-hour clock format
  - Current speed in km/h with location tracking
  - Local weather conditions and temperature for Shantou City
- **App Shortcuts**: Quick access to commonly used applications
  - Amap Auto (高德地图)
  - Sony Walkman
  - System Settings
- **Easy Exit**: Triple-tap the speed display to return to the default launcher

## Technical Details
- Uses Google's FusedLocationProviderClient for accurate speed tracking
- Weather data fetched from OpenWeatherMap API
- Automatically updates weather information every 15 minutes
- Caches weather data locally to handle network interruptions
- Adapts layout based on screen dimensions

## Required Permissions
- `ACCESS_FINE_LOCATION`: For speed tracking functionality
- Internet access: For fetching weather data

## Installation
1. Install the APK on your e-bike's Android system
2. Set as default launcher when prompted, or:
   - Go to Settings > Apps > Default Apps > Home app
   - Select "E-Bike UI" from the list

## Usage Notes
- The reminder message "Remember to take me with you to avoid loss!" is displayed in Chinese
- Weather information is specifically configured for Shantou City
- The app uses Android system icons for weather conditions when network icons cannot be loaded
- Weather temperature is displayed in Celsius only

## Known Issues
- UI is only available in Chinese
- City is hardcoded to Shantou and requires code modification to change
- App shortcuts (currently 4) must be modified through hardcoded values
- UI layout is specifically optimized for Sony T2 Ultra displays and may not render correctly on other devices

## Customization
To modify the application for other locations or add different app shortcuts:
1. Change the `WEATHER_URL` constant in `WeatherService.java` to your city's ID
2. Modify the `TARGET_PACKAGES` array in `MainActivity.java` to include your preferred applications
3. To change UI language, all text strings would need to be modified in the source code

## Development
- Built using standard Android XML layouts and Java
- Uses Glide for image loading
- GSON for JSON parsing
- Compatible with most Android versions that support location services

## License
This application is open source. When redistributing or modifying this code, you must retain the original author's name (YuQing Ding) in all derivative works.

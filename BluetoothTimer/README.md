# Bluetooth Timer

A separate native Kotlin Android app inside the Prosperity repository.

## Features
- 5, 10, 15, 30 and 60 minute presets
- Custom timer from 1 to 1440 minutes
- Countdown display and cancel control
- Exact alarm while the app is in the background
- Notification at expiry with a shortcut to Android Bluetooth settings

## Android limitation
On modern Android versions, ordinary third-party apps cannot silently switch Bluetooth off. This app opens the system Bluetooth settings from the expiry notification so the user can switch it off with confirmation.

## Build
See the repository Actions tab. The workflow named **Build Bluetooth Timer APK** runs on pushes affecting this folder and can also be started manually. Download the generated **BluetoothTimer-debug-apk** artifact from the successful workflow run.

To build locally, open the BluetoothTimer folder in Android Studio, allow Gradle sync, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

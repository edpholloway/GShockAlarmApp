# G-Shock Alarm + Timer (DW-H5600)

A small Android companion for setting the alarms and countdown timer on the Casio DW-H5600 (module 3516). It uses Bluetooth Low Energy and the open-source GShockAPI library.

## Features

- Finds and connects to a nearby Casio watch.
- Reads the watch's alarm times and enabled states.
- Lets you tap an alarm time to edit it and toggle each alarm on or off.
- Saves all alarm changes to the watch and reads them back for confirmation.
- Reads and sets the countdown timer, with 5, 10, 15, and 25 minute shortcuts.
- Accepts a custom countdown from one second up to 24 hours.
- Keeps settings local to the phone; no account or cloud service is used.

## Connect to the watch

1. Install and open the app. Allow the Bluetooth permission when Android asks.
2. If the watch is not found, open Bluetooth Link on the DW-H5600.
3. Tap **Find watch**, then select the watch when it appears.
4. After the settings load, edit an alarm or timer and use its save button.

The watch must be paired/connected through its Bluetooth Link function. Keep the watch close to the phone while sending settings.

## Build an APK

Open this folder in Android Studio with JDK 21 and Android SDK Platform 36 installed. Accept the Android SDK license in Android Studio when prompted, then run **Build > Build APK(s)**. The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

You can also build from a terminal with `gradlew.bat :app:assembleDebug` after setting `JAVA_HOME` and `ANDROID_HOME`.

Minimum Android version: Android 8.0 (API 26).

## Bluetooth library

The app includes source from [GShockAPI](https://github.com/izivkov/GShockAPI), licensed under MIT. See [LICENSE](LICENSE). GShockAPI provides the Casio BLE protocol implementation. This app is unofficial and is not made by or affiliated with Casio.

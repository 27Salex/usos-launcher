# Build and Run

## Requirements
- JDK 17 (pinned in `gradle.properties`).
- Android SDK, path in `local.properties` (`sdk.dir=...`; not versioned). compileSdk/targetSdk 36, minSdk 33.
- Gradle wrapper 8.14.3 (`./gradlew`), AGP 8.13.2, Kotlin 2.4.20.

## Commands
```
./gradlew test                 # unit tests
./gradlew assembleDebug        # debug APK
./gradlew test assembleRelease # release (see signing)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
After installing, press Home and choose Usos as the home app.

## Wireless ADB (Android 11+)
Developer options > Wireless debugging > Pair device with pairing code, then:
```
adb pair <ip>:<pairing-port>
adb connect <ip>:<port>
adb devices
```

## Signing with your own key
Create `~/.android/usos/keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it the release build still succeeds and produces `app/build/outputs/apk/release/app-release-unsigned.apk`, which you must sign yourself. Never commit keys. Updates only work between APKs signed with the same key.

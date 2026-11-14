# LUMIQ AI — Build Guide

## Prerequisites
- **JDK:** 17+
- **Android SDK:** Compile SDK 36, Min SDK 24, Target SDK 36
- **Gradle:** Uses system `gradle` or Android Studio Gradle Sync

## Building Locally
1. Verify `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` are present.
2. Run unit & Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
   *(If running Firestore Emulator `*Rule*` tests, ensure Firestore & Auth emulators are active on `127.0.0.1:8085` and `127.0.0.1:9099`.)*
3. Assemble the Debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
   Output APK path: `app/build/outputs/apk/debug/app-debug.apk`.
4. Build a signed Release APK / AAB using environment variables:
   - `KEYSTORE_PATH`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`
   ```bash
   gradle :app:assembleRelease
   ```

## Installing on Android Device
1. Transfer `app/build/outputs/apk/debug/app-debug.apk` to your Android device.
2. Open the `.apk` file, allow **Install unknown apps** if requested, and tap **Install**.
3. Sign in with your Google Account (via Jetpack Credential Manager).

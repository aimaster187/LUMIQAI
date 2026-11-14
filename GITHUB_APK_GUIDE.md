# GitHub Actions APK Build Guide

## Workflow File
`.github/workflows/android-apk.yml`

## How It Works
1. Triggers automatically on pushes to `main`/`master` or manually via **Actions &rarr; Build LUMIQ AI Android APK &rarr; Run workflow**.
2. Installs **JDK 17** and sets up Gradle caching.
3. Runs unit tests (`./gradlew :app:testDebugUnitTest --exclude-task "*Rule*"`).
4. Builds `app/build/outputs/apk/debug/app-debug.apk` via `./gradlew :app:assembleDebug`.
5. Verifies the APK file exists and uploads the downloadable artifact named **`LUMIQ-AI-APK`**.

## For Apps Created Inside LUMIQ Android App Studio
Export your generated project ZIP from **Android App Studio**, extract it into a GitHub repository containing `.github/workflows/android-apk.yml`, push to GitHub, and download the compiled APK artifact from the GitHub Actions run summary.

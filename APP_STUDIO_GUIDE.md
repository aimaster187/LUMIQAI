# LUMIQ Android App Studio & Code Studio Guide

## Code Studio + Termux Integration
- Edit Kotlin, Java, Python, TypeScript, JavaScript, HTML, CSS, SQL, JSON, and XML files.
- Use **AI Generate / Debug** to improve or fix code using Firebase AI Logic or your verified custom provider.
- **Termux Runner:** Detects `com.termux` via `PackageManager`. When Termux is installed with `allow-external-apps=true` in `~/.termux/termux.properties`, commands are dispatched to `com.termux.app.RunCommandService` (`com.termux.RUN_COMMAND`). If Termux is not installed, LUMIQ clearly prompts you and provides one-tap clipboard copy.

## Android App Studio
1. Enter your App Name, Package Name (e.g. `com.lumiqlabs.novashop`), and app requirements.
2. Tap **Generate Android Project** to generate `app/build.gradle.kts`, `AndroidManifest.xml`, and `MainActivity.kt` using Gemini.
3. Tap **Export ZIP** to share or save the project bundle.
4. Build an installable APK using the GitHub Actions workflow in `.github/workflows/android-apk.yml` (on-device Android sandboxes cannot run full Gradle/AAPT2 builds natively).

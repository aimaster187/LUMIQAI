# Next Steps for Production Release

1. **Publish Public Website & Admin Dashboard**:
   Run `firebase login && firebase deploy --only hosting` locally (or configure `FIREBASE_SERVICE_ACCOUNT_GEN_LANG_CLIENT_0099902352` in GitHub Secrets) so `https://gen-lang-client-0099902352.web.app` and `/admin/` go publicly live.
2. **Release Keystore Signing**:
   Create an upload keystore (`my-upload-key.jks`) and supply `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` when running `gradle :app:bundleRelease` for Google Play.
3. **Firebase Console Authorized Domains**:
   Confirm your live web hosting domain is listed under Firebase Console -> Authentication -> Settings -> Authorized domains for Admin Google Sign-In.
4. **Physical Device Testing**:
   Install `app-debug.apk` on a physical Android phone to verify live SpeechRecognizer Hindi/Hinglish latency, CameraX preview, Termux `RUN_COMMAND`, and Accessibility actions (not testable in headless build container without ADB).

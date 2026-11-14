# LUMIQ LABS Admin Dashboard Deployment & Usage

## Location
- Dashboard HTML/JS: `web/public/admin/index.html` and `web/public/admin/admin.js`
- Connected Firestore Database: `ai-studio-android-lumiqai-02bb64ec-3121-4fae-995f-3f44cf6d4a9a`

## Security Model
- **Zero Hardcoded Passwords:** Access requires Google Sign-In (`signInWithPopup`).
- **Server-Verified Authorization:** Firestore security rules verify either:
  1. `request.auth.token.email_verified == true && request.auth.token.email == 'pranavsmmsupport@gmail.com'`, or
  2. Existence of `admins/{uid}` in Firestore.
- **Privacy Guarantee:** Admins can inspect user metadata (`publicId`, `username`, `vipStatus`, `deviceId`, `isBanned`), manage licenses, moderate public/studio projects, publish announcements, toggle maintenance mode, and view append-only `auditLogs`. Private chats, AI memories, Keystore vault secrets, and user API keys remain strictly inaccessible to admins.

## Deploying to Firebase Hosting
See `WEBSITE_DEPLOYMENT_GUIDE.md` for live Firebase Hosting deployment steps. Note: ensure Google Auth is enabled for your Firebase Hosting domain in Firebase Console &rarr; Authentication &rarr; Settings &rarr; Authorized domains (`gen-lang-client-0099902352.web.app`).

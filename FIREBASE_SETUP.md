# Firebase Setup & Architecture — LUMIQ AI

## Provisioned Resources
- **Project ID:** `gen-lang-client-0099902352` (`750655526285`)
- **Android Package Name:** `com.lumiqlabs.lumiqai`
- **SHA-1 Registered:** `e25c044a8a2557f8f80692d5dc5cf60d20bb8fbd`
- **Firestore Named Database ID:** `ai-studio-android-lumiqai-02bb64ec-3121-4fae-995f-3f44cf6d4a9a`
- **App Check:** Debug provider in `BuildConfig.DEBUG`, reCAPTCHA provider (`recaptcha_site_key`) in release builds (`AppCheckInit.kt`).

## Authentication Scope
- The Android client uses **Google Sign-In via Jetpack Credential Manager** exclusively (`LumiqAuthRepository.kt`).
- Anonymous authentication (`signInAnonymously`) is disabled and strictly prohibited.
- Email/Password sign-in is **not enabled** in the provisioned configuration; if you want Email/Password auth in the future, enable the Email/Password sign-in provider manually in Firebase Console &rarr; Authentication &rarr; Sign-in method.

## Firestore Rules & Deployment
- Rules live in `/firestore.rules` and schema blueprint in `/firebase-blueprint.json`.
- Bootstrapped admin account in `firestore.rules`: `pranavsmmsupport@gmail.com` (`email_verified == true`), plus `admins/{uid}` documents.
- Rules are deployed to database `ai-studio-android-lumiqai-02bb64ec-3121-4fae-995f-3f44cf6d4a9a`.
- Automated verification:
  - `node --test firestore.test.js`
  - `gradle :app:testDebugUnitTest --tests '*Rule*'`

# Security Architecture — LUMIQ AI

## 1. Zero-Trust Cloud Rules (`firestore.rules`)
- Default deny-all fallback (`match /{document=**} { allow read, write: if false; }`).
- Owner-only access for `chats`, `messages`, `memories`, `agentTasks`, and `private/account`.
- Immutable `userId`, `publicId`, and `createdAt` enforced on updates.
- Privilege escalation prevention: users cannot self-grant `vipStatus = 'vip'` or `'lifetime'`, mutate `isBanned`, or forge `isAdmin()`. License redemption requires atomic batch validation via `get(...)` and `getAfter(...)`.
- Admin actions (`users` ban/VIP/unbind, `licenses`, `announcements`, `config`, `projects` moderation) are gated by server-verified `isAdmin()` and logged to append-only `auditLogs`.

## 2. On-Device Cryptography (`KeystoreCryptoManager.kt`)
- **Password & Secret Vault:** Hardware-backed **Android Keystore AES-256-GCM** (`lumiq_ai_master_aes_key`) encrypts Vault secrets and custom AI provider API keys per signed-in user UID.
- **App Lock PIN:** Salted **PBKDF2WithHmacSHA256** (65,536 iterations, 16-byte `SecureRandom` salt, constant-time `MessageDigest.isEqual`) + 5-attempt 30s lockout + `BiometricPrompt`.
- **Clipboard Hygiene:** Copying a decrypted Vault secret schedules a 30-second automatic clipboard clear.

## 3. Authentication & Zero-Key AI
- Google Sign-In via Jetpack `CredentialManager` exclusively.
- Gemini inference uses **Firebase AI Logic** attested by **Firebase App Check** (Debug provider in debug builds, reCAPTCHA in release builds) — no client-side Gemini API key is ever bundled in source or APK.

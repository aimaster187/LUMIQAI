# Privacy Policy — LUMIQ AI (LUMIQ LABS)

**Effective Date:** November 2026  
**Developer:** LUMIQ LABS (Founder: Pranav Patel)  
**Support Email:** pranavsmmsupport@gmail.com  

## 1. Data We Process
- **Account Authentication:** Google Sign-In provides your Firebase UID, display name, and email (stored strictly in private `users/{uid}/private/account`, accessible only to you and verified administrators for support).
- **Public Profile:** Your deterministic `LUM-XXXXXXXX` Public ID, chosen username, bio, and public/private visibility toggle.
- **Chats & AI Memory:** Stored under `users/{uid}/chats` and `users/{uid}/memories` solely for your account. Administrators cannot read your private chats or AI memory.
- **Voice, Camera & Documents:** Processed on-device (ML Kit OCR/QR, Android SpeechRecognizer, PdfRenderer) or sent to cloud AI only when you explicitly tap Send, Capture/Analyze, or speak a query.
- **Vault & API Keys:** Encrypted locally on your device using Android Keystore AES-256-GCM and isolated by user UID.

## 2. Data Control & Deletion
- You may delete individual chat sessions, AI memories, Vault entries, and custom provider configurations inside the app at any time.
- WhatsApp and Accessibility controls strictly require explicit user triggers and confirmation—no silent background messaging or credential harvesting occurs.

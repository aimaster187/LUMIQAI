# LUMIQ AI — Permissions & Privacy Compliance Guide

## Google Play Compliant Least-Privilege Matrix
| Permission / Capability | Purpose | Prompt Timing |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Firebase AI Logic, Firestore sync, Web Browser | Install-time |
| `RECORD_AUDIO` | Voice Studio & Mic Orb speech recognition | Runtime on mic tap |
| `CAMERA` | CameraX Vision capture & QR/OCR scan | Runtime on CameraX tap |
| `POST_NOTIFICATIONS` | Foreground Voice Service status & alerts | Runtime (Android 13+) |
| `FOREGROUND_SERVICE_MICROPHONE` | Active persistent Voice Service | When user enables service |
| `com.android.alarm.permission.SET_ALARM` | Quick alarm/timer intents | User-triggered command |
| `com.termux.permission.RUN_COMMAND` | Optional Termux script execution | When user runs Termux cmd |
| **Android Photo Picker** | Gallery image selection | Zero storage permissions |
| **Storage Access Framework (`OpenDocument`)** | PDF/TXT/MD loading | Zero storage permissions |

- Broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`) are **never declared or requested**.
- `LumiqAccessibilityService` only performs explicit user-triggered global navigation actions (`Home`, `Back`, `Recents`, `Quick Settings`) and never harvests passwords or simulates hidden clicks.

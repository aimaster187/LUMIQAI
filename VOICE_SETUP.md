# LUMIQ Voice Studio Setup Guide

## Implemented Components
- **Speech-to-Text (`LumiqVoiceEngine.kt`)**: Android `SpeechRecognizer` supporting `en-IN` (English India / Hinglish), `hi-IN` (Hindi), `en-US`, and `en-GB`.
- **Text-to-Speech & Profiles**: 5 curated voice profiles (`LUMIQ Nova`, `LUMIQ Atlas`, `LUMIQ Aanya`, `LUMIQ Arjun`, `LUMIQ Pulse`) with adjustable pitch, speed, and volume.
- **Echo & Feedback Prevention**: Microphone recognition is automatically gated when `TextToSpeech` starts speaking, and rapid duplicate recognition results within 1500ms are deduplicated.
- **Background Foreground Service (`LumiqVoiceForegroundService.kt`)**: Persistent notification indicator (`FOREGROUND_SERVICE_TYPE_MICROPHONE`) with quick Stop action.

## Recommended Device Settings
1. Grant **Microphone (`RECORD_AUDIO`)** and **Notifications (`POST_NOTIFICATIONS`)** inside Permission Center.
2. Set your preferred language (`en-IN` or `hi-IN`) and Wake Word (`Hey LUMIQ`) inside **Voice Studio**.
3. For uninterrupted background voice listening, exempt LUMIQ AI from battery optimization in **Permission Center**.
4. Make sure Google Speech Services / Android TTS voice packs are installed on the phone for offline Hindi/English recognition.

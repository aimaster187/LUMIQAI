package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.LumiqVoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

enum class VoiceEngineState { IDLE, LISTENING, PROCESSING, SPEAKING }

data class RecognitionRecord(
    val text: String,
    val locale: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class LumiqVoiceEngine(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var continuousSessionActive = false
    private var currentLocaleTag = "en-IN"
    private var activeWakeWord = "Hey LUMIQ"
    private var lastSubmittedText = ""
    private var lastSubmittedAtMs = 0L

    private var onFinalTranscriptCallback: ((String) -> Unit)? = null
    private var onWakeWordDetectedCallback: ((String) -> Unit)? = null

    private val _engineState = MutableStateFlow(VoiceEngineState.IDLE)
    val engineState: StateFlow<VoiceEngineState> = _engineState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _recognitionHistory = MutableStateFlow<List<RecognitionRecord>>(emptyList())
    val recognitionHistory: StateFlow<List<RecognitionRecord>> = _recognitionHistory.asStateFlow()

    private val _ttsError = MutableStateFlow<String?>(null)
    val ttsError: StateFlow<String?> = _ttsError.asStateFlow()

    private var activeProfile = CURATED_PROFILES.first()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyProfile(activeProfile)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Gate microphone while LUMIQ is speaking so AI doesn't hear itself
                        _engineState.value = VoiceEngineState.SPEAKING
                        stopRecognizerInternal()
                    }

                    override fun onDone(utteranceId: String?) {
                        abandonFocus()
                        _engineState.value = VoiceEngineState.IDLE
                        if (continuousSessionActive) {
                            startRecognizerInternal()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        abandonFocus()
                        _engineState.value = VoiceEngineState.IDLE
                    }
                })
            } else {
                _ttsError.value = "Device TTS engine unavailable."
            }
        }
    }

    fun updateConfiguration(
        localeTag: String,
        wakeWord: String,
        profileId: String,
        speed: Float,
        pitch: Float,
        volume: Float
    ) {
        currentLocaleTag = localeTag
        activeWakeWord = wakeWord
        val base = CURATED_PROFILES.firstOrNull { it.id == profileId } ?: CURATED_PROFILES.first()
        activeProfile = base.copy(speed = speed, pitch = pitch, volume = volume)
        if (ttsReady) {
            applyProfile(activeProfile)
        }
    }

    private fun applyProfile(profile: LumiqVoiceProfile) {
        val locale = Locale.forLanguageTag(profile.localeTag)
        tts?.language = locale
        tts?.setPitch(profile.pitch)
        tts?.setSpeechRate(profile.speed)
    }

    fun startListeningSession(
        continuous: Boolean,
        onTranscript: (String) -> Unit,
        onWakeWord: ((String) -> Unit)? = null
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _ttsError.value = "Speech recognition service is not available on this device."
            return
        }
        onFinalTranscriptCallback = onTranscript
        onWakeWordDetectedCallback = onWakeWord
        continuousSessionActive = continuous

        // Stop TTS if speaking so user can interrupt cleanly
        if (_engineState.value == VoiceEngineState.SPEAKING) {
            tts?.stop()
        }
        startRecognizerInternal()
    }

    fun stopListeningSession() {
        continuousSessionActive = false
        stopRecognizerInternal()
        if (_engineState.value == VoiceEngineState.LISTENING) {
            _engineState.value = VoiceEngineState.IDLE
        }
        _rmsLevel.value = 0f
    }

    fun speak(text: String) {
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted ")
            .replace(Regex("[*#_`]"), "")
            .trim()
            .take(1600)
        if (cleanText.isBlank() || !ttsReady) return

        // Gate microphone before speaking to prevent echo loops
        stopRecognizerInternal()
        requestAudioFocus()
        _engineState.value = VoiceEngineState.SPEAKING

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, activeProfile.volume.coerceIn(0f, 1f))
        }
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        abandonFocus()
        _engineState.value = VoiceEngineState.IDLE
    }

    fun previewProfile(profile: LumiqVoiceProfile) {
        applyProfile(profile)
        val sample = if (profile.localeTag.startsWith("hi")) {
            "Namaste! Main LUMIQ AI hoon, aapka 10D smart assistant."
        } else {
            "Hello! I am LUMIQ AI by LUMIQ Labs, ready to assist you."
        }
        speak(sample)
    }

    private fun startRecognizerInternal() {
        if (_engineState.value == VoiceEngineState.SPEAKING) return
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _engineState.value = VoiceEngineState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        _engineState.value = VoiceEngineState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _rmsLevel.value = 0f
                        _engineState.value = VoiceEngineState.PROCESSING
                    }

                    override fun onError(error: Int) {
                        _rmsLevel.value = 0f
                        _engineState.value = VoiceEngineState.IDLE
                        if (continuousSessionActive &&
                            (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        ) {
                            startRecognizerInternal()
                        } else {
                            Log.w(TAG, "SpeechRecognizer error code: $error")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        _rmsLevel.value = 0f
                        _engineState.value = VoiceEngineState.IDLE

                        if (text.isNotBlank()) {
                            handleFinalTranscript(text)
                        } else if (continuousSessionActive) {
                            startRecognizerInternal()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        _partialTranscript.value = partial
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocaleTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLocaleTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _engineState.value = VoiceEngineState.IDLE
        }
    }

    private fun handleFinalTranscript(rawText: String) {
        val now = System.currentTimeMillis()
        // Deduplicate rapid identical recognition events within 1500ms
        if (rawText.equals(lastSubmittedText, ignoreCase = true) && now - lastSubmittedAtMs < 1500L) {
            return
        }
        lastSubmittedText = rawText
        lastSubmittedAtMs = now

        _recognitionHistory.update { list ->
            (listOf(RecognitionRecord(rawText, currentLocaleTag, now)) + list).take(30)
        }

        val wakeLower = activeWakeWord.lowercase()
        val textLower = rawText.lowercase()
        if (textLower.startsWith(wakeLower)) {
            val stripped = rawText.substring(activeWakeWord.length).trim().trimStart(',', ':', ' ')
            onWakeWordDetectedCallback?.invoke(stripped)
            if (stripped.isNotBlank()) {
                onFinalTranscriptCallback?.invoke(stripped)
            }
        } else {
            onFinalTranscriptCallback?.invoke(rawText)
        }
    }

    private fun stopRecognizerInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
    }

    fun release() {
        stopListeningSession()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val TAG = "LumiqVoiceEngine"

        val SUPPORTED_LOCALES = listOf(
            "en-IN" to "English (India / Hinglish)",
            "hi-IN" to "Hindi (हिन्दी)",
            "en-US" to "English (US)",
            "en-GB" to "English (UK)"
        )

        val WAKE_WORDS = listOf("Hey LUMIQ", "Hello LUMIQ", "LUMIQ")

        val CURATED_PROFILES = listOf(
            LumiqVoiceProfile("lumiq_nova_female", "LUMIQ Nova (Female)", "en-IN", 1.05f, 1.04f, 1.0f, "Crystal Clear"),
            LumiqVoiceProfile("lumiq_atlas_male", "LUMIQ Atlas (Male)", "en-IN", 0.86f, 0.98f, 1.0f, "Deep Studio"),
            LumiqVoiceProfile("lumiq_aanya_hindi", "LUMIQ Aanya (Hindi)", "hi-IN", 1.04f, 1.02f, 1.0f, "Natural Hindi"),
            LumiqVoiceProfile("lumiq_arjun_hindi", "LUMIQ Arjun (Hindi Male)", "hi-IN", 0.88f, 0.98f, 1.0f, "Warm Baritone"),
            LumiqVoiceProfile("lumiq_echo_fast", "LUMIQ Pulse (Fast 1.2x)", "en-US", 1.02f, 1.20f, 1.0f, "Ultra Rapid")
        )
    }
}

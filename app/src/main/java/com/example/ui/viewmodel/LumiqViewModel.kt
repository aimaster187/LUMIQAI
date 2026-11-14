package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AgentOrchestrator
import com.example.ai.LumiqAiEngine
import com.example.ai.WorkspaceAgent
import com.example.data.local.LocalPreferencesStore
import com.example.data.model.AgentTaskRun
import com.example.data.model.AiProviderType
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.ChatMessageItem
import com.example.data.model.ChatSession
import com.example.data.model.CodeFileItem
import com.example.data.model.CustomProviderConfig
import com.example.data.model.FollowRelation
import com.example.data.model.PublicProfile
import com.example.data.model.StudioProject
import com.example.data.model.UserMemoryItem
import com.example.data.model.UserNotification
import com.example.data.model.UserProfile
import com.example.data.model.VaultEntry
import com.example.data.repository.LumiqAuthRepository
import com.example.data.repository.LumiqFirestoreRepository
import com.example.device.PhoneControlManager
import com.example.security.KeystoreCryptoManager
import com.example.voice.LumiqVoiceEngine
import com.example.voice.SpeakerVoiceprint
import com.example.voice.SpeakerVoiceprintEngine
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data object Thinking : ChatStreamState
    data class Streaming(val text: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

enum class WhatsAppStage(val label: String) {
    DRAFTED("Drafted"),
    OPENED("Opened"),
    SENT_CONFIRMED("Sent Confirmed")
}

data class WhatsAppDraftItem(
    val id: String,
    val phone: String,
    val message: String,
    val stage: WhatsAppStage,
    val timestampMs: Long = System.currentTimeMillis()
)

class LumiqViewModel(
    private val repository: LumiqFirestoreRepository,
    private val prefs: LocalPreferencesStore,
    private val aiEngine: LumiqAiEngine,
    val voiceEngine: LumiqVoiceEngine,
    private val currentUser: FirebaseUser
) : ViewModel() {

    val currentUserId: String = currentUser.uid

    val agentOrchestrator = AgentOrchestrator(aiEngine, viewModelScope)

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _chatStreamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val chatStreamState: StateFlow<ChatStreamState> = _chatStreamState.asStateFlow()

    private var activeChatJob: Job? = null
    private var lastUserPromptForRetry: String = ""

    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lockCheckedOnStart = false

    val isDarkTheme: StateFlow<Boolean> = prefs.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeProviderId: StateFlow<String> = prefs.activeProviderId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderType.GEMINI_FAST_LITE.name)

    val wakeWord: StateFlow<String> = prefs.wakeWord
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Hey LUMIQ")

    val sttLocale: StateFlow<String> = prefs.sttLocale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en-IN")

    val activeVoiceId: StateFlow<String> = prefs.activeVoiceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "lumiq_nova_female")

    val voiceSpeed: StateFlow<Float> = prefs.voiceSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.02f)

    val voicePitch: StateFlow<Float> = prefs.voicePitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val voiceVolume: StateFlow<Float> = prefs.voiceVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val searchEngine: StateFlow<String> = prefs.searchEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Google")

    val appLockEnabled: StateFlow<Boolean> = prefs.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockBiometric: StateFlow<Boolean> = prefs.appLockBiometric
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val failedPinAttempts: StateFlow<Int> = prefs.failedPinAttempts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lockoutUntilMs: StateFlow<Long> = prefs.lockoutUntilMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalAiRequests: StateFlow<Long> = prefs.totalAiRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lastAiLatencyMs: StateFlow<Long> = prefs.lastAiLatencyMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val enrolledVoiceprint: StateFlow<SpeakerVoiceprint?> = prefs.speakerVoiceprintFlow(currentUserId)
        .map { encrypted ->
            if (encrypted.isBlank()) null
            else SpeakerVoiceprintEngine.decodeProfile(KeystoreCryptoManager.decrypt(encrypted))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _speakerVerifyStatus = MutableStateFlow<String?>(null)
    val speakerVerifyStatus: StateFlow<String?> = _speakerVerifyStatus.asStateFlow()

    private val _isRecordingVoiceprint = MutableStateFlow(false)
    val isRecordingVoiceprint: StateFlow<Boolean> = _isRecordingVoiceprint.asStateFlow()

    private val _builtInModelTestResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val builtInModelTestResults: StateFlow<Map<String, String>> = _builtInModelTestResults.asStateFlow()

    val customProviders: StateFlow<List<CustomProviderConfig>> = prefs.customProvidersFlow(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultEntries: StateFlow<List<VaultEntry>> = prefs.vaultEntriesFlow(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browserBookmarks: StateFlow<List<Pair<String, String>>> = prefs.browserBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { err -> handleFlowError("observeUserProfile", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chats: StateFlow<List<ChatSession>> = repository.observeChats(currentUserId)
        .catch { err -> handleFlowError("observeChats", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessageItem>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(currentUserId, chatId)
                .catch { err -> handleFlowError("observeMessages", err) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<UserMemoryItem>> = repository.observeMemories(currentUserId)
        .catch { err -> handleFlowError("observeMemories", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<StudioProject>> = repository.observeUserProjects(currentUserId)
        .catch { err -> handleFlowError("observeUserProjects", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentTasks: StateFlow<List<AgentTaskRun>> = repository.observeAgentTasks(currentUserId)
        .catch { err -> handleFlowError("observeAgentTasks", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<UserNotification>> = repository.observeNotifications(currentUserId)
        .catch { err -> handleFlowError("observeNotifications", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val announcements: StateFlow<List<Announcement>> = repository.observeAnnouncements()
        .catch { err -> handleFlowError("observeAnnouncements", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appConfig: StateFlow<AppConfig?> = repository.observeAppConfig()
        .catch { err -> handleFlowError("observeAppConfig", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val following: StateFlow<List<FollowRelation>> = repository.observeFollowing(currentUserId)
        .catch { err -> handleFlowError("observeFollowing", err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Studio workspaces
    private val _codeFiles = MutableStateFlow(defaultCodeFiles())
    val codeFiles: StateFlow<List<CodeFileItem>> = _codeFiles.asStateFlow()

    private val _websiteFiles = MutableStateFlow(defaultWebsiteFiles())
    val websiteFiles: StateFlow<Map<String, String>> = _websiteFiles.asStateFlow()

    private val _websiteBuildLogs = MutableStateFlow<List<String>>(listOf("LUMIQ Website Studio ready."))
    val websiteBuildLogs: StateFlow<List<String>> = _websiteBuildLogs.asStateFlow()

    private val _appStudioFiles = MutableStateFlow(defaultAndroidAppFiles("Nova Shop", "com.lumiqlabs.novashop"))
    val appStudioFiles: StateFlow<Map<String, String>> = _appStudioFiles.asStateFlow()

    private val _appStudioLogs = MutableStateFlow<List<String>>(listOf("LUMIQ Android App Studio ready."))
    val appStudioLogs: StateFlow<List<String>> = _appStudioLogs.asStateFlow()

    private val _whatsappDrafts = MutableStateFlow<List<WhatsAppDraftItem>>(emptyList())
    val whatsappDrafts: StateFlow<List<WhatsAppDraftItem>> = _whatsappDrafts.asStateFlow()

    private val _discoveredProfiles = MutableStateFlow<List<PublicProfile>>(emptyList())
    val discoveredProfiles: StateFlow<List<PublicProfile>> = _discoveredProfiles.asStateFlow()

    private val _lastHomeReply = MutableStateFlow("Namaste! Tap the 10D Orb or type below to command LUMIQ.")
    val lastHomeReply: StateFlow<String> = _lastHomeReply.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfileProvisioned(currentUser)
                .onFailure { _statusBanner.value = "Profile sync notice: ${it.localizedMessage}" }
        }
        viewModelScope.launch {
            prefs.appLockEnabled.collect { enabled ->
                if (!lockCheckedOnStart) {
                    lockCheckedOnStart = true
                    if (enabled) _isUnlocked.value = false
                }
            }
        }
        viewModelScope.launch {
            chats.collect { list ->
                if (_selectedChatId.value == null && list.isNotEmpty()) {
                    _selectedChatId.value = list.first().id
                }
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(enabled) }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch { prefs.setActiveProviderId(providerId) }
    }

    fun syncVoiceConfiguration(
        localeTag: String = sttLocale.value,
        wake: String = wakeWord.value,
        profileId: String = activeVoiceId.value,
        speed: Float = voiceSpeed.value,
        pitch: Float = voicePitch.value,
        volume: Float = voiceVolume.value
    ) {
        voiceEngine.updateConfiguration(localeTag, wake, profileId, speed, pitch, volume)
        viewModelScope.launch {
            prefs.setSttLocale(localeTag)
            prefs.setWakeWord(wake)
            prefs.setVoiceSettings(profileId, speed, pitch, volume)
        }
    }

    // --- Individual Speaker Recognition (Voice Identity) ---
    fun enrollSpeakerVoice() {
        if (_isRecordingVoiceprint.value) return
        viewModelScope.launch {
            _isRecordingVoiceprint.value = true
            _speakerVerifyStatus.value = "Recording 2.6s owner voice sample... Speak naturally now."
            try {
                val pcm = SpeakerVoiceprintEngine.recordPcmSample(2600).getOrThrow()
                val print = withContext(Dispatchers.Default) {
                    SpeakerVoiceprintEngine.extractVoiceprint(pcm).getOrThrow()
                }
                val encoded = SpeakerVoiceprintEngine.encodeProfile(print)
                val encrypted = KeystoreCryptoManager.encrypt(encoded)
                prefs.saveSpeakerVoiceprint(currentUserId, encrypted)
                _speakerVerifyStatus.value =
                    "Owner voiceprint enrolled (~${print.f0Hz.toInt()} Hz pitch, ${print.voicedFrames} voiced frames)."
                _statusBanner.value = "Speaker voiceprint enrolled and encrypted."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _speakerVerifyStatus.value = "Enrollment failed: ${e.localizedMessage}"
            } finally {
                _isRecordingVoiceprint.value = false
            }
        }
    }

    fun verifySpeakerVoice() {
        if (_isRecordingVoiceprint.value) return
        val enrolled = enrolledVoiceprint.value
        if (enrolled == null) {
            _speakerVerifyStatus.value = "No voiceprint enrolled yet. Tap Enroll Owner Voice first."
            return
        }
        viewModelScope.launch {
            _isRecordingVoiceprint.value = true
            _speakerVerifyStatus.value = "Listening 2.4s to verify speaker identity..."
            try {
                val pcm = SpeakerVoiceprintEngine.recordPcmSample(2400).getOrThrow()
                val probe = withContext(Dispatchers.Default) {
                    SpeakerVoiceprintEngine.extractVoiceprint(pcm).getOrThrow()
                }
                val res = SpeakerVoiceprintEngine.verify(enrolled, probe)
                val pct = (res.similarity * 100f).toInt().coerceIn(0, 100)
                _speakerVerifyStatus.value = if (res.isMatch) {
                    "Verified Owner Speaker ($pct% match • ${res.probeF0Hz.toInt()} Hz vs ${res.enrolledF0Hz.toInt()} Hz)."
                } else {
                    "Unrecognized Speaker ($pct% match < ${(SpeakerVoiceprintEngine.MATCH_THRESHOLD * 100).toInt()}% • ${res.probeF0Hz.toInt()} Hz)."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _speakerVerifyStatus.value = "Verification failed: ${e.localizedMessage}"
            } finally {
                _isRecordingVoiceprint.value = false
            }
        }
    }

    fun clearSpeakerVoiceprint() {
        viewModelScope.launch {
            prefs.saveSpeakerVoiceprint(currentUserId, "")
            _speakerVerifyStatus.value = "Enrolled voiceprint cleared."
        }
    }

    // --- Voice & Quick Command Handling ---
    fun handleVoiceOrHomePrompt(
        context: Context,
        prompt: String,
        speakReply: Boolean,
        onNavigateToChat: () -> Unit = {}
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return

        // Fast <10ms local device command routing first
        val localAction = PhoneControlManager.tryFastLocalCommand(context, trimmed)
        if (localAction != null) {
            _lastHomeReply.value = localAction.message
            if (speakReply) voiceEngine.speak(localAction.message)
            return
        }

        // Fast streaming AI response
        sendChatMessage(
            prompt = trimmed,
            speakReply = speakReply,
            imageBitmap = null
        )
    }

    // --- Multi-Model Chat ---
    fun selectChat(chatId: String) {
        _selectedChatId.value = chatId
    }

    fun createNewChat(title: String = "New LUMIQ Session") {
        viewModelScope.launch {
            val prov = activeProviderId.value
            val res = repository.createChatSession(currentUserId, title, prov, prov)
            res.onSuccess { newId -> _selectedChatId.value = newId }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameChatSession(currentUserId, chatId, newTitle)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChatSession(currentUserId, chatId)
                .onSuccess {
                    if (_selectedChatId.value == chatId) {
                        _selectedChatId.value = null
                    }
                }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun sendChatMessage(
        prompt: String,
        speakReply: Boolean = false,
        imageBitmap: Bitmap? = null,
        attachedDocumentText: String = ""
    ) {
        val fullPrompt = if (attachedDocumentText.isNotBlank()) {
            "$prompt\n\n[Attached Document Content]\n${attachedDocumentText.take(12000)}"
        } else prompt

        lastUserPromptForRetry = fullPrompt
        activeChatJob?.cancel()

        activeChatJob = viewModelScope.launch {
            _chatStreamState.value = ChatStreamState.Thinking
            val startMs = System.currentTimeMillis()
            try {
                val chatId = _selectedChatId.value ?: run {
                    val title = prompt.take(36).ifBlank { "Vision Query" }
                    val created = repository.createChatSession(
                        currentUserId,
                        title,
                        activeProviderId.value,
                        activeProviderId.value
                    ).getOrThrow()
                    _selectedChatId.value = created
                    created
                }

                repository.appendChatMessage(currentUserId, chatId, "user", fullPrompt, activeProviderId.value)

                val memContext = memories.value
                    .sortedByDescending { it.isPinned }
                    .take(8)
                    .joinToString("\n") { "- ${it.title}: ${it.content}" }

                val historyPairs = activeMessages.value.takeLast(6).map { it.role to it.content }

                var latestReply = ""
                aiEngine.streamReply(
                    prompt = fullPrompt,
                    history = historyPairs,
                    memoryContext = memContext,
                    activeProviderId = activeProviderId.value,
                    customProviders = customProviders.value,
                    imageBitmap = imageBitmap
                ).collect { streamed ->
                    latestReply = streamed
                    _chatStreamState.value = ChatStreamState.Streaming(streamed)
                    _lastHomeReply.value = streamed
                }

                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                prefs.recordAiUsage(latency)
                repository.appendChatMessage(
                    currentUserId,
                    chatId,
                    "model",
                    latestReply,
                    activeProviderId.value,
                    latency
                )
                _chatStreamState.value = ChatStreamState.Idle
                if (speakReply) {
                    voiceEngine.speak(latestReply)
                }
            } catch (e: CancellationException) {
                _chatStreamState.value = ChatStreamState.Idle
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "AI reply failed."
                _chatStreamState.value = ChatStreamState.Error(msg)
                _lastHomeReply.value = msg
            }
        }
    }

    fun stopGeneration() {
        activeChatJob?.cancel()
        _chatStreamState.value = ChatStreamState.Idle
    }

    fun retryLastMessage() {
        if (lastUserPromptForRetry.isNotBlank()) {
            sendChatMessage(lastUserPromptForRetry)
        }
    }

    // --- AI Memory ---
    fun addMemory(category: String, title: String, content: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.saveMemory(currentUserId, category, title, content, isPinned)
                .onSuccess { _statusBanner.value = "Saved to AI Memory." }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            repository.deleteMemory(currentUserId, id)
        }
    }

    // --- Five-Agent Workspace ---
    fun dispatchAgentTask(agentId: String, prompt: String) {
        agentOrchestrator.dispatchTask(
            agentId = agentId,
            taskPrompt = prompt,
            customProviders = customProviders.value
        ) { agent, status, summary ->
            repository.logAgentTask(
                userId = currentUserId,
                agentType = agent.roleType,
                agentName = agent.name,
                prompt = prompt,
                status = status,
                resultSummary = summary
            )
        }
    }

    // --- Code Studio ---
    fun updateCodeFile(path: String, content: String) {
        _codeFiles.update { list ->
            val exists = list.any { it.path == path }
            if (exists) list.map { if (it.path == path) it.copy(content = content) else it }
            else list + CodeFileItem(path, detectLang(path), content)
        }
    }

    fun createCodeFile(path: String) {
        val clean = path.trim().ifBlank { return }
        if (_codeFiles.value.any { it.path == clean }) return
        _codeFiles.update { it + CodeFileItem(clean, detectLang(clean), "// Created in LUMIQ Code Studio\n") }
    }

    fun deleteCodeFile(path: String) {
        _codeFiles.update { list -> list.filterNot { it.path == path } }
    }

    fun renameCodeFile(oldPath: String, newPath: String) {
        val cleanNew = newPath.trim().ifBlank { return }
        _codeFiles.update { list ->
            list.map { if (it.path == oldPath) it.copy(path = cleanNew, language = detectLang(cleanNew)) else it }
        }
    }

    suspend fun aiAssistCode(instruction: String, currentCode: String, language: String): Result<String> {
        return try {
            val res = aiEngine.generateOnce(
                prompt = "Language: $language\nInstruction: $instruction\n\nExisting Code:\n```\n$currentCode\n```\nProvide clean, ready-to-use code and concise explanation.",
                activeProviderId = activeProviderId.value,
                customProviders = customProviders.value
            )
            Result.success(res.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Website Studio & Real Hosting Deployment ---
    fun generateWebsiteWithAi(siteName: String, requirements: String) {
        viewModelScope.launch {
            _websiteBuildLogs.update { it + "Generating responsive architecture for '$siteName'..." }
            try {
                val prompt = """
                    Build a complete, modern, responsive single-file HTML5 website (with embedded CSS and vanilla JS) for:
                    Site Name: $siteName
                    Requirements: $requirements
                    Include navigation, hero section, feature cards/products, interactive cart or form state, mobile responsiveness, and footer by LUMIQ Website Studio.
                    Return ONLY raw HTML starting with <!DOCTYPE html>.
                """.trimIndent()
                val res = aiEngine.generateOnce(
                    prompt = prompt,
                    activeProviderId = AiProviderType.FIREBASE_GEMINI.name
                )
                val html = res.text
                    .removePrefix("```html")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                val finalHtml = if (html.contains("<html", ignoreCase = true)) html else defaultWebsiteFiles()["index.html"]!!
                _websiteFiles.value = mapOf(
                    "index.html" to finalHtml,
                    "robots.txt" to "User-agent: *\nAllow: /\n",
                    "lumiq-site.json" to JSONObject().apply {
                        put("name", siteName)
                        put("generator", "LUMIQ AI Website Studio")
                        put("version", "1.0.0")
                    }.toString(2)
                )
                _websiteBuildLogs.update { it + "Generated ${finalHtml.length} bytes HTML5 bundle in ${res.latencyMs}ms." }

                val pubId = userProfile.value?.publicId ?: LumiqAuthRepository.derivePermanentPublicId(currentUserId)
                repository.createProject(
                    userId = currentUserId,
                    ownerPublicId = pubId,
                    name = siteName,
                    projectType = "website",
                    framework = "HTML5 Responsive",
                    summary = requirements.take(240)
                )
                _websiteBuildLogs.update { it + "Project registered in LUMIQ Cloud Projects." }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _websiteBuildLogs.update { it + "Generation error: ${e.localizedMessage}" }
            }
        }
    }

    fun deployWebsiteToNetlify(siteName: String, netlifyToken: String, projectId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _websiteBuildLogs.update { it + "Validating website bundle before Netlify deployment..." }
            val cleanToken = netlifyToken.trim()
            if (cleanToken.isBlank()) {
                _websiteBuildLogs.update { it + "Deployment cancelled: Netlify Personal Access Token required." }
                return@launch
            }
            try {
                val zipBytes = ByteArrayOutputStream().use { baos ->
                    ZipOutputStream(baos).use { zos ->
                        _websiteFiles.value.forEach { (name, content) ->
                            zos.putNextEntry(ZipEntry(name))
                            zos.write(content.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                    }
                    baos.toByteArray()
                }
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url("https://api.netlify.com/api/v1/sites")
                    .addHeader("Authorization", "Bearer $cleanToken")
                    .addHeader("Content-Type", "application/zip")
                    .post(zipBytes.toRequestBody("application/zip".toMediaType()))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        _websiteBuildLogs.update { it + "Netlify HTTP ${resp.code}: ${raw.take(240)}" }
                        return@launch
                    }
                    val json = JSONObject(raw)
                    val liveUrl = json.optString("ssl_url").ifBlank { json.optString("url") }
                    _websiteBuildLogs.update { it + "LIVE DEPLOYMENT SUCCEEDED: $liveUrl" }
                    if (!projectId.isNullOrBlank()) {
                        repository.updateProjectDeployment(projectId, "ready", "deployed", liveUrl)
                    }
                }
            } catch (e: Exception) {
                _websiteBuildLogs.update { it + "Deployment failed: ${e.localizedMessage}" }
            }
        }
    }

    // --- Android App Studio ---
    fun generateAndroidProject(appName: String, packageName: String, requirements: String) {
        viewModelScope.launch {
            _appStudioLogs.update { it + "Architecting Kotlin + Jetpack Compose project for '$appName' ($packageName)..." }
            try {
                val prompt = """
                    Design a real Jetpack Compose MainActivity.kt for an Android app named "$appName" (package $packageName).
                    Requirements: $requirements
                    Include Material 3 theme, navigation/tabs, interactive state, and clean Kotlin code.
                    Return Kotlin code only.
                """.trimIndent()
                val res = aiEngine.generateOnce(
                    prompt = prompt,
                    activeProviderId = AiProviderType.FIREBASE_GEMINI.name
                )
                val ktCode = res.text.removePrefix("```kotlin").removePrefix("```").removeSuffix("```").trim()
                val baseFiles = defaultAndroidAppFiles(appName, packageName).toMutableMap()
                if (ktCode.contains("package ")) {
                    baseFiles["app/src/main/java/${packageName.replace('.', '/')}/MainActivity.kt"] = ktCode
                }
                _appStudioFiles.value = baseFiles
                _appStudioLogs.update { it + "Generated ${baseFiles.size} Android project files in ${res.latencyMs}ms." }

                val pubId = userProfile.value?.publicId ?: LumiqAuthRepository.derivePermanentPublicId(currentUserId)
                repository.createProject(
                    userId = currentUserId,
                    ownerPublicId = pubId,
                    name = appName,
                    projectType = "android_app",
                    framework = "Kotlin + Jetpack Compose",
                    packageName = packageName,
                    summary = requirements.take(240)
                )
                _appStudioLogs.update { it + "Registered Android Studio project. Export ZIP or run GitHub Actions to build APK." }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _appStudioLogs.update { it + "App generation error: ${e.localizedMessage}" }
            }
        }
    }

    // --- WhatsApp Assistant ---
    fun draftWhatsAppMessage(phone: String, promptOrText: String, useAiPolish: Boolean) {
        viewModelScope.launch {
            val finalMsg = if (useAiPolish) {
                runCatching {
                    aiEngine.generateOnce(
                        prompt = "Draft a polite, clear WhatsApp message for this intent (return message text only): $promptOrText",
                        activeProviderId = AiProviderType.GEMINI_FAST_LITE.name
                    ).text.trim()
                }.getOrDefault(promptOrText)
            } else promptOrText

            val draft = WhatsAppDraftItem(
                id = UUID.randomUUID().toString().take(8),
                phone = phone.trim(),
                message = finalMsg,
                stage = WhatsAppStage.DRAFTED
            )
            _whatsappDrafts.update { listOf(draft) + it }
        }
    }

    fun markWhatsAppStage(draftId: String, stage: WhatsAppStage) {
        _whatsappDrafts.update { list ->
            list.map { if (it.id == draftId) it.copy(stage = stage) else it }
        }
    }

    // --- Vault & App Lock ---
    fun setAppLockPin(pin: String, biometricEnabled: Boolean) {
        viewModelScope.launch {
            val salt = KeystoreCryptoManager.generateSaltBase64()
            val hash = KeystoreCryptoManager.hashPin(pin, salt)
            prefs.saveAppLockPin(hash, salt, biometricEnabled)
            _statusBanner.value = "LUMIQ App Lock enabled with salted PBKDF2 hash."
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            prefs.disableAppLock()
            _isUnlocked.value = true
            _statusBanner.value = "LUMIQ App Lock disabled."
        }
    }

    fun verifyAppLockPin(pin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lockedUntil = lockoutUntilMs.value
            if (now < lockedUntil) {
                val secs = ((lockedUntil - now) / 1000L).coerceAtLeast(1L)
                onResult(false, "Too many attempts. Try again in ${secs}s.")
                return@launch
            }
            val storedHash = prefs.appLockPinHash.first()
            val storedSalt = prefs.appLockPinSalt.first()
            val ok = KeystoreCryptoManager.verifyPin(pin, storedSalt, storedHash)
            if (ok) {
                prefs.resetPinFailures()
                _isUnlocked.value = true
                onResult(true, null)
            } else {
                val nextAttempts = failedPinAttempts.value + 1
                val nextLockout = if (nextAttempts >= 5) now + 30_000L else 0L
                prefs.recordPinFailure(nextAttempts, nextLockout)
                onResult(
                    false,
                    if (nextAttempts >= 5) "5 failed attempts. Locked for 30s."
                    else "Incorrect PIN ($nextAttempts/5)."
                )
            }
        }
    }

    fun unlockWithBiometric() {
        viewModelScope.launch {
            prefs.resetPinFailures()
            _isUnlocked.value = true
        }
    }

    fun lockAppNow() {
        if (appLockEnabled.value) _isUnlocked.value = false
    }

    fun addVaultEntry(title: String, username: String, secret: String, category: String) {
        viewModelScope.launch {
            val encrypted = KeystoreCryptoManager.encrypt(secret)
            val entry = VaultEntry(
                id = UUID.randomUUID().toString().take(8),
                title = title.trim(),
                usernameOrLabel = username.trim(),
                encryptedSecret = encrypted,
                category = category
            )
            val updated = listOf(entry) + vaultEntries.value
            prefs.saveVaultEntries(currentUserId, updated)
            _statusBanner.value = "Encrypted with Android Keystore AES-256-GCM."
        }
    }

    fun deleteVaultEntry(id: String) {
        viewModelScope.launch {
            prefs.saveVaultEntries(currentUserId, vaultEntries.value.filterNot { it.id == id })
        }
    }

    // --- Custom API Providers (Per-user isolated & encrypted) ---
    fun addAndTestCustomProvider(
        type: AiProviderType,
        label: String,
        endpointUrl: String,
        modelName: String,
        rawApiKey: String
    ) {
        viewModelScope.launch {
            val encrypted = KeystoreCryptoManager.encrypt(rawApiKey.trim())
            val candidate = CustomProviderConfig(
                id = "prov_${UUID.randomUUID().toString().take(8)}",
                providerType = type,
                label = label.trim().ifBlank { type.displayName },
                endpointUrl = endpointUrl.trim().ifBlank { type.defaultEndpoint },
                modelName = modelName.trim().ifBlank { type.defaultModel },
                encryptedApiKey = encrypted,
                isConnectedVerified = false
            )
            val testResult = aiEngine.testProviderConnection(candidate)
            val finalConfig = testResult.fold(
                onSuccess = { ms ->
                    _statusBanner.value = "Connected to ${candidate.label} in ${ms}ms."
                    candidate.copy(isConnectedVerified = true, lastLatencyMs = ms)
                },
                onFailure = { err ->
                    _statusBanner.value = "Connection test failed: ${err.localizedMessage}"
                    candidate.copy(isConnectedVerified = false)
                }
            )
            prefs.saveCustomProviders(currentUserId, listOf(finalConfig) + customProviders.value)
        }
    }

    fun removeCustomProvider(id: String) {
        viewModelScope.launch {
            prefs.saveCustomProviders(currentUserId, customProviders.value.filterNot { it.id == id })
            if (activeProviderId.value == id) {
                prefs.setActiveProviderId(AiProviderType.FIREBASE_GEMINI.name)
            }
        }
    }

    fun testBuiltInGeminiModel(type: AiProviderType) {
        viewModelScope.launch {
            _builtInModelTestResults.update { it + (type.name to "Testing ${type.defaultModel}...") }
            val probeConfig = CustomProviderConfig(
                id = type.name,
                providerType = type,
                label = type.displayName,
                endpointUrl = type.defaultEndpoint,
                modelName = type.defaultModel,
                encryptedApiKey = ""
            )
            aiEngine.testProviderConnection(probeConfig).fold(
                onSuccess = { ms ->
                    _builtInModelTestResults.update { it + (type.name to "Verified OK in ${ms}ms") }
                    prefs.recordAiUsage(ms)
                    _statusBanner.value = "${type.defaultModel} connected in ${ms}ms."
                },
                onFailure = { err ->
                    val detail = err.localizedMessage ?: "Connection failed"
                    _builtInModelTestResults.update { it + (type.name to "Failed: $detail") }
                    _statusBanner.value = "${type.defaultModel} test failed: $detail"
                }
            )
        }
    }

    // --- Social, Profile & VIP ---
    fun updateProfile(username: String, bio: String, isPublic: Boolean) {
        val pubId = userProfile.value?.publicId ?: LumiqAuthRepository.derivePermanentPublicId(currentUserId)
        viewModelScope.launch {
            repository.updateProfileDetails(currentUserId, pubId, username, bio, isPublic)
                .onSuccess { _statusBanner.value = "Profile updated." }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun searchCommunity(query: String) {
        viewModelScope.launch {
            repository.searchPublicProfiles(query)
                .onSuccess { _discoveredProfiles.value = it.filter { p -> p.userId != currentUserId } }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun followProfile(target: PublicProfile) {
        viewModelScope.launch {
            repository.followUser(currentUserId, target)
                .onSuccess { _statusBanner.value = "Followed ${target.username} (${target.publicId})." }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun startTenMinuteTrial() {
        viewModelScope.launch {
            repository.startTenMinuteTrial(currentUserId)
                .onSuccess { _statusBanner.value = "10-minute server-verified trial activated!" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun redeemLicenseKey(code: String) {
        viewModelScope.launch {
            repository.redeemLicenseKey(currentUserId, code)
                .onSuccess { tier -> _statusBanner.value = "License redeemed! Tier: ${tier.uppercase()}" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch {
            val next = (listOf(title to url) + browserBookmarks.value).distinctBy { it.second }.take(30)
            prefs.saveBrowserBookmarks(next)
        }
    }

    fun setSearchEngine(engine: String) {
        viewModelScope.launch { prefs.setSearchEngine(engine) }
    }

    suspend fun summarizeWebPage(title: String, pageContent: String): Result<String> {
        return try {
            val res = aiEngine.generateOnce(
                prompt = "Summarize this web page clearly in bullet points.\nTitle: $title\n\nContent:\n${pageContent.take(10000)}",
                activeProviderId = activeProviderId.value,
                customProviders = customProviders.value
            )
            Result.success(res.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun handleFlowError(source: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w("LumiqViewModel", "Error in $source", rootError)
    }

    private fun detectLang(path: String): String = when {
        path.endsWith(".kt") -> "Kotlin"
        path.endsWith(".java") -> "Java"
        path.endsWith(".py") -> "Python"
        path.endsWith(".ts") -> "TypeScript"
        path.endsWith(".js") -> "JavaScript"
        path.endsWith(".html") -> "HTML"
        path.endsWith(".css") -> "CSS"
        path.endsWith(".sql") -> "SQL"
        path.endsWith(".json") -> "JSON"
        path.endsWith(".xml") -> "XML"
        else -> "Text"
    }

    private fun defaultCodeFiles(): List<CodeFileItem> = listOf(
        CodeFileItem(
            path = "main.py",
            language = "Python",
            content = """# Termux & Python Automation Script - LUMIQ LABS
import json, datetime

def lumiq_status():
    return {
        "brand": "LUMIQ LABS",
        "founder": "Pranav Patel",
        "timestamp": datetime.datetime.utcnow().isoformat()
    }

if __name__ == "__main__":
    print(json.dumps(lumiq_status(), indent=2))
"""
        ),
        CodeFileItem(
            path = "index.html",
            language = "HTML",
            content = """<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LUMIQ Preview</title></head>
<body style="font-family:sans-serif;background:#f4f7fc;color:#0c1829;padding:24px;">
  <h1>LUMIQ Code Studio</h1>
  <p>Edit HTML/CSS/JS here and preview live inside LUMIQ AI.</p>
</body>
</html>"""
        )
    )

    private fun defaultWebsiteFiles(): Map<String, String> = mapOf(
        "index.html" to """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LUMIQ Storefront Preview</title>
  <style>
    body { margin:0; font-family: system-ui, sans-serif; background: linear-gradient(135deg,#f4f8ff,#e6effc); color:#0c1829; }
    header { display:flex; justify-content:space-between; align-items:center; padding:18px 24px; background:rgba(255,255,255,0.85); backdrop-filter:blur(12px); border-bottom:1px solid #d4e0f2; }
    .hero { max-width:920px; margin:40px auto; padding:32px; background:#fff; border-radius:24px; box-shadow:0 20px 50px rgba(29,107,255,0.12); }
    .btn { display:inline-block; padding:12px 24px; background:linear-gradient(90deg,#1d6bff,#00d4ff); color:#fff; border-radius:999px; text-decoration:none; font-weight:600; }
  </style>
</head>
<body>
  <header><strong>LUMIQ LABS Studio</strong><span>Preview Ready</span></header>
  <section class="hero">
    <h1>Build & Deploy Websites in Seconds</h1>
    <p>Describe your website in LUMIQ Website Studio to generate multi-section responsive pages, export ZIPs, and publish live.</p>
    <a class="btn" href="#start">Explore Features</a>
  </section>
</body>
</html>"""
    )

    private fun defaultAndroidAppFiles(appName: String, pkg: String): Map<String, String> {
        val pkgPath = pkg.replace('.', '/')
        return mapOf(
            "app/build.gradle.kts" to """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "$pkg"
    compileSdk = 35
    defaultConfig {
        applicationId = "$pkg"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}""",
            "app/src/main/AndroidManifest.xml" to """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="$appName">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>""",
            "app/src/main/java/$pkgPath/MainActivity.kt" to """package $pkg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Text("Welcome to $appName — Built with LUMIQ App Studio") }
            }
        }
    }
}"""
        )
    }
}

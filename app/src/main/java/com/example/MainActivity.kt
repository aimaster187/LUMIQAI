package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.AgentRunStatus
import com.example.ai.LumiqAiEngine
import com.example.core.configureAppCheck
import com.example.data.local.LocalPreferencesStore
import com.example.data.repository.LumiqAuthRepository
import com.example.data.repository.LumiqFirestoreRepository
import com.example.ui.components.LumiqBottomBar
import com.example.ui.components.LumiqNavigationDrawerContent
import com.example.ui.components.LumiqRoute
import com.example.ui.components.LumiqTopBar
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AgentsScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AppStudioScreen
import com.example.ui.screens.AuthSignInScreen
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodeStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.screens.PhoneControlScreen
import com.example.ui.screens.ProfileMemoryScreen
import com.example.ui.screens.ProvidersScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.VipUsageScreen
import com.example.ui.screens.VisionDocsScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.screens.WebsiteStudioScreen
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LumiqBackgroundCanvas
import com.example.ui.theme.LumiqGlassCard
import com.example.ui.theme.LumiqTheme
import com.example.ui.viewmodel.LumiqViewModel
import com.example.voice.LumiqVoiceEngine
import com.example.voice.VoiceEngineState
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAppCheck(this, intent)

        setContent {
            val currentUser by LumiqAuthRepository.authStateFlow()
                .collectAsStateWithLifecycle(initialValue = null)

            if (currentUser == null) {
                LumiqTheme(darkTheme = false) {
                    AuthSignInScreen(onAuthenticated = {})
                }
            } else {
                val user = currentUser!!
                key(user.uid) {
                    AuthenticatedLumiqApp(user = user)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedLumiqApp(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestoreDbId = context.getString(R.string.firestore_database_id)

    val viewModel: LumiqViewModel = viewModel(
        key = "lumiq_vm_${user.uid}",
        factory = remember(user.uid, firestoreDbId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    val db = FirebaseFirestore.getInstance(firestoreDbId)
                    return LumiqViewModel(
                        repository = LumiqFirestoreRepository(db),
                        prefs = LocalPreferencesStore(appCtx),
                        aiEngine = LumiqAiEngine(),
                        voiceEngine = LumiqVoiceEngine(appCtx),
                        currentUser = user
                    ) as T
                }
            }
        }
    )

    DisposableEffect(viewModel) {
        onDispose { viewModel.voiceEngine.release() }
    }

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val appLockBiometric by viewModel.appLockBiometric.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val voiceState by viewModel.voiceEngine.engineState.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.voiceEngine.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceEngine.partialTranscript.collectAsStateWithLifecycle()

    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()
    val customProviders by viewModel.customProviders.collectAsStateWithLifecycle()
    val lastLatencyMs by viewModel.lastAiLatencyMs.collectAsStateWithLifecycle()
    val totalRequests by viewModel.totalAiRequests.collectAsStateWithLifecycle()
    val wakeWord by viewModel.wakeWord.collectAsStateWithLifecycle()
    val sttLocale by viewModel.sttLocale.collectAsStateWithLifecycle()
    val activeVoiceId by viewModel.activeVoiceId.collectAsStateWithLifecycle()
    val voiceSpeed by viewModel.voiceSpeed.collectAsStateWithLifecycle()
    val voicePitch by viewModel.voicePitch.collectAsStateWithLifecycle()
    val voiceVolume by viewModel.voiceVolume.collectAsStateWithLifecycle()
    val enrolledVoiceprint by viewModel.enrolledVoiceprint.collectAsStateWithLifecycle()
    val speakerVerifyStatus by viewModel.speakerVerifyStatus.collectAsStateWithLifecycle()
    val isRecordingVoiceprint by viewModel.isRecordingVoiceprint.collectAsStateWithLifecycle()
    val builtInModelTestResults by viewModel.builtInModelTestResults.collectAsStateWithLifecycle()

    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val chatStreamState by viewModel.chatStreamState.collectAsStateWithLifecycle()
    val lastHomeReply by viewModel.lastHomeReply.collectAsStateWithLifecycle()

    val agents by viewModel.agentOrchestrator.agents.collectAsStateWithLifecycle()
    val sharedAgentContext by viewModel.agentOrchestrator.sharedContext.collectAsStateWithLifecycle()
    val activeAgentCount = remember(agents) {
        agents.count { it.status == AgentRunStatus.RUNNING || it.status == AgentRunStatus.QUEUED }
    }

    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val discoveredProfiles by viewModel.discoveredProfiles.collectAsStateWithLifecycle()

    val browserBookmarks by viewModel.browserBookmarks.collectAsStateWithLifecycle()
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
    val codeFiles by viewModel.codeFiles.collectAsStateWithLifecycle()
    val websiteFiles by viewModel.websiteFiles.collectAsStateWithLifecycle()
    val websiteBuildLogs by viewModel.websiteBuildLogs.collectAsStateWithLifecycle()
    val appStudioFiles by viewModel.appStudioFiles.collectAsStateWithLifecycle()
    val appStudioLogs by viewModel.appStudioLogs.collectAsStateWithLifecycle()
    val whatsappDrafts by viewModel.whatsappDrafts.collectAsStateWithLifecycle()
    val vaultEntries by viewModel.vaultEntries.collectAsStateWithLifecycle()

    var currentRoute by rememberSaveable { mutableStateOf(LumiqRoute.HOME) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.voiceEngine.startListeningSession(
                continuous = false,
                onTranscript = { text ->
                    viewModel.handleVoiceOrHomePrompt(context, text, speakReply = true)
                }
            )
        } else {
            viewModel.showBanner("Microphone permission is required for LUMIQ Voice.")
        }
    }

    fun triggerVoiceToggle() {
        when (voiceState) {
            VoiceEngineState.LISTENING -> viewModel.voiceEngine.stopListeningSession()
            VoiceEngineState.SPEAKING -> viewModel.voiceEngine.stopSpeaking()
            else -> {
                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    viewModel.voiceEngine.startListeningSession(
                        continuous = false,
                        onTranscript = { text ->
                            viewModel.handleVoiceOrHomePrompt(context, text, speakReply = true)
                        }
                    )
                } else {
                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    LumiqTheme(darkTheme = isDarkTheme) {
        if (!isUnlocked) {
            AppLockScreen(
                biometricEnabled = appLockBiometric,
                onVerifyPin = { pin, cb -> viewModel.verifyAppLockPin(pin, cb) },
                onBiometricSuccess = { viewModel.unlockWithBiometric() }
            )
            return@LumiqTheme
        }

        if (currentRoute != LumiqRoute.HOME) {
            BackHandler { currentRoute = LumiqRoute.HOME }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                LumiqNavigationDrawerContent(
                    currentRoute = currentRoute,
                    username = userProfile?.username ?: user.displayName ?: "LUMIQ User",
                    publicId = userProfile?.publicId ?: "LUM-PENDING",
                    vipStatus = userProfile?.vipStatus ?: "free",
                    onSelectRoute = { route ->
                        currentRoute = route
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            LumiqBackgroundCanvas {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        LumiqTopBar(
                            title = if (currentRoute == LumiqRoute.HOME) "LUMIQ AI" else currentRoute.label,
                            unreadNotifications = notifications.count { !it.isRead },
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onNotificationsClick = { showNotificationsSheet = true }
                        )
                    },
                    bottomBar = {
                        LumiqBottomBar(
                            currentRoute = currentRoute,
                            voiceState = voiceState,
                            onNavigate = { route -> currentRoute = route },
                            onMicClick = { triggerVoiceToggle() }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        when (currentRoute) {
                            LumiqRoute.HOME -> HomeScreen(
                                userProfile = userProfile,
                                voiceState = voiceState,
                                rmsLevel = rmsLevel,
                                partialTranscript = partialTranscript,
                                lastReply = lastHomeReply,
                                chatStreamState = chatStreamState,
                                activeProviderId = activeProviderId,
                                customProviders = customProviders,
                                lastLatencyMs = lastLatencyMs,
                                wakeWord = wakeWord,
                                recentChats = chats,
                                activeAgentCount = activeAgentCount,
                                onOrbClick = { triggerVoiceToggle() },
                                onSendPrompt = { prompt ->
                                    viewModel.handleVoiceOrHomePrompt(context, prompt, speakReply = false)
                                },
                                onSelectProvider = viewModel::setActiveProvider,
                                onOpenChat = { id ->
                                    if (id != null) viewModel.selectChat(id)
                                    currentRoute = LumiqRoute.CHAT
                                },
                                onNavigate = { currentRoute = it }
                            )

                            LumiqRoute.CHAT -> ChatScreen(
                                chats = chats,
                                selectedChatId = selectedChatId,
                                messages = activeMessages,
                                streamState = chatStreamState,
                                activeProviderId = activeProviderId,
                                customProviders = customProviders,
                                onSelectChat = viewModel::selectChat,
                                onCreateNewChat = { viewModel.createNewChat() },
                                onRenameChat = viewModel::renameChat,
                                onDeleteChat = viewModel::deleteChat,
                                onSelectProvider = viewModel::setActiveProvider,
                                onSendMessage = { prompt, bmp, doc ->
                                    viewModel.sendChatMessage(
                                        prompt = prompt,
                                        speakReply = false,
                                        imageBitmap = bmp,
                                        attachedDocumentText = doc
                                    )
                                },
                                onStopGeneration = viewModel::stopGeneration,
                                onRetry = viewModel::retryLastMessage
                            )

                            LumiqRoute.AGENTS -> AgentsScreen(
                                agents = agents,
                                sharedContext = sharedAgentContext,
                                onDispatchTask = viewModel::dispatchAgentTask,
                                onCancelAgent = { id -> viewModel.agentOrchestrator.cancelAgent(id) },
                                onEmergencyStop = { viewModel.agentOrchestrator.emergencyStopAll() },
                                onCreateCustomAgent = { name, role, instr, tools, reqConf ->
                                    viewModel.agentOrchestrator.addCustomAgent(name, role, instr, tools, reqConf)
                                }
                            )

                            LumiqRoute.VOICE_STUDIO -> VoiceStudioScreen(
                                voiceEngine = viewModel.voiceEngine,
                                sttLocale = sttLocale,
                                wakeWord = wakeWord,
                                activeVoiceId = activeVoiceId,
                                speed = voiceSpeed,
                                pitch = voicePitch,
                                volume = voiceVolume,
                                onSyncVoiceConfig = { loc, wake, profId, sp, pt, vol ->
                                    viewModel.syncVoiceConfiguration(loc, wake, profId, sp, pt, vol)
                                },
                                onVoiceCommandRecognized = { text ->
                                    viewModel.handleVoiceOrHomePrompt(context, text, speakReply = true)
                                },
                                hasEnrolledVoiceprint = enrolledVoiceprint != null,
                                enrolledF0Hz = enrolledVoiceprint?.f0Hz ?: 0f,
                                speakerVerifyStatus = speakerVerifyStatus,
                                isRecordingVoiceprint = isRecordingVoiceprint,
                                onEnrollSpeakerVoice = viewModel::enrollSpeakerVoice,
                                onVerifySpeakerVoice = viewModel::verifySpeakerVoice,
                                onClearSpeakerVoiceprint = viewModel::clearSpeakerVoiceprint
                            )

                            LumiqRoute.BROWSER -> BrowserScreen(
                                searchEngine = searchEngine,
                                bookmarks = browserBookmarks,
                                onSetSearchEngine = viewModel::setSearchEngine,
                                onAddBookmark = viewModel::addBookmark,
                                onSummarizePageWithAi = viewModel::summarizeWebPage
                            )

                            LumiqRoute.CODE_STUDIO -> CodeStudioScreen(
                                files = codeFiles,
                                onUpdateFile = viewModel::updateCodeFile,
                                onCreateFile = viewModel::createCodeFile,
                                onDeleteFile = viewModel::deleteCodeFile,
                                onAiAssist = viewModel::aiAssistCode
                            )

                            LumiqRoute.WEBSITE_STUDIO -> WebsiteStudioScreen(
                                files = websiteFiles,
                                logs = websiteBuildLogs,
                                projects = projects,
                                onGenerateWebsite = viewModel::generateWebsiteWithAi,
                                onDeployNetlify = viewModel::deployWebsiteToNetlify
                            )

                            LumiqRoute.APP_STUDIO -> AppStudioScreen(
                                files = appStudioFiles,
                                logs = appStudioLogs,
                                projects = projects,
                                onGenerateApp = viewModel::generateAndroidProject
                            )

                            LumiqRoute.VISION_DOCS -> VisionDocsScreen(
                                chatStreamState = chatStreamState,
                                onAnalyzeWithAi = { prompt, bitmap, docText ->
                                    viewModel.sendChatMessage(
                                        prompt = prompt,
                                        speakReply = false,
                                        imageBitmap = bitmap,
                                        attachedDocumentText = docText
                                    )
                                }
                            )

                            LumiqRoute.PHONE_CONTROL -> PhoneControlScreen(
                                whatsappDrafts = whatsappDrafts,
                                onDraftWhatsApp = viewModel::draftWhatsAppMessage,
                                onUpdateWhatsAppStage = viewModel::markWhatsAppStage
                            )

                            LumiqRoute.VAULT -> VaultScreen(
                                appLockEnabled = appLockEnabled,
                                biometricEnabled = appLockBiometric,
                                vaultEntries = vaultEntries,
                                onSetAppLockPin = viewModel::setAppLockPin,
                                onDisableAppLock = viewModel::disableAppLock,
                                onLockNow = viewModel::lockAppNow,
                                onAddVaultEntry = viewModel::addVaultEntry,
                                onDeleteVaultEntry = viewModel::deleteVaultEntry,
                                onShowMessage = viewModel::showBanner
                            )

                            LumiqRoute.PROFILE_MEMORY -> ProfileMemoryScreen(
                                userProfile = userProfile,
                                memories = memories,
                                discoveredProfiles = discoveredProfiles,
                                following = following,
                                onUpdateProfile = viewModel::updateProfile,
                                onSearchCommunity = viewModel::searchCommunity,
                                onFollowProfile = viewModel::followProfile,
                                onAddMemory = viewModel::addMemory,
                                onDeleteMemory = viewModel::deleteMemory,
                                onShowMessage = viewModel::showBanner
                            )

                            LumiqRoute.PROVIDERS -> ProvidersScreen(
                                activeProviderId = activeProviderId,
                                customProviders = customProviders,
                                onSelectProvider = viewModel::setActiveProvider,
                                onAddAndTestProvider = viewModel::addAndTestCustomProvider,
                                onRemoveCustomProvider = viewModel::removeCustomProvider,
                                builtInModelTestResults = builtInModelTestResults,
                                onTestBuiltInModel = viewModel::testBuiltInGeminiModel
                            )

                            LumiqRoute.PERMISSIONS -> PermissionCenterScreen()

                            LumiqRoute.VIP_USAGE -> VipUsageScreen(
                                userProfile = userProfile,
                                totalRequests = totalRequests,
                                lastLatencyMs = lastLatencyMs,
                                announcements = announcements,
                                appConfig = appConfig,
                                onStartTrial = viewModel::startTenMinuteTrial,
                                onRedeemLicense = viewModel::redeemLicenseKey
                            )

                            LumiqRoute.ABOUT -> AboutScreen(
                                isDarkTheme = isDarkTheme,
                                userProfile = userProfile,
                                onToggleTheme = viewModel::setDarkTheme,
                                onSignOut = {
                                    scope.launch { LumiqAuthRepository.signOut(context) }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showNotificationsSheet) {
            ModalBottomSheet(onDismissRequest = { showNotificationsSheet = false }) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LUMIQ Notifications & Announcements", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (notifications.isEmpty() && announcements.isEmpty()) {
                        Text(
                            "No notifications yet. Announcements from LUMIQ LABS appear here live.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        announcements.forEach { ann ->
                            LumiqGlassCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column {
                                    Text("[Announcement] ${ann.title}", style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
                                    Text(ann.body, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        notifications.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(item.body, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

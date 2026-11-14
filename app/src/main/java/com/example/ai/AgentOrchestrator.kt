package com.example.ai

import com.example.data.model.AiProviderType
import com.example.data.model.CustomProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

enum class AgentRunStatus { IDLE, QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED }

data class WorkspaceAgent(
    val id: String,
    val name: String,
    val roleType: String,
    val avatarCode: String,
    val instructions: String,
    val providerId: String = AiProviderType.FIREBASE_GEMINI.name,
    val tools: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val status: AgentRunStatus = AgentRunStatus.IDLE,
    val assignedTask: String = "",
    val logs: List<String> = emptyList(),
    val result: String = ""
)

class AgentOrchestrator(
    private val aiEngine: LumiqAiEngine,
    private val scope: CoroutineScope
) {
    private val parallelLimiter = Semaphore(5)
    private val activeJobs = mutableMapOf<String, Job>()

    private val _agents = MutableStateFlow(defaultAgents())
    val agents: StateFlow<List<WorkspaceAgent>> = _agents.asStateFlow()

    private val _sharedContext = MutableStateFlow("")
    val sharedContext: StateFlow<String> = _sharedContext.asStateFlow()

    fun addCustomAgent(
        name: String,
        roleType: String,
        instructions: String,
        tools: List<String>,
        requiresConfirmation: Boolean
    ) {
        val agent = WorkspaceAgent(
            id = "agent_${UUID.randomUUID().toString().take(8)}",
            name = name.trim().take(48),
            roleType = roleType.trim().take(32),
            avatarCode = name.trim().take(2).uppercase().ifBlank { "CA" },
            instructions = instructions.trim(),
            tools = tools,
            requiresConfirmation = requiresConfirmation
        )
        _agents.update { it + agent }
    }

    fun dispatchTask(
        agentId: String,
        taskPrompt: String,
        customProviders: List<CustomProviderConfig>,
        onLogPersisted: suspend (WorkspaceAgent, String, String) -> Unit = { _, _, _ -> }
    ) {
        val target = _agents.value.firstOrNull { it.id == agentId } ?: return
        activeJobs[agentId]?.cancel()

        updateAgent(agentId) {
            it.copy(
                status = AgentRunStatus.QUEUED,
                assignedTask = taskPrompt,
                logs = it.logs + "Queued: ${taskPrompt.take(80)}",
                result = ""
            )
        }

        val job = scope.launch {
            try {
                parallelLimiter.withPermit {
                    updateAgent(agentId) {
                        it.copy(
                            status = AgentRunStatus.RUNNING,
                            logs = it.logs + "Running with max-5 orchestrator slot..."
                        )
                    }
                    val fullPrompt = buildString {
                        append("Agent Identity: ${target.name} (${target.roleType})\n")
                        append("System Instructions: ${target.instructions}\n")
                        if (_sharedContext.value.isNotBlank()) {
                            append("Shared Multi-Agent Context:\n${_sharedContext.value.take(1500)}\n")
                        }
                        append("Assigned Task:\n$taskPrompt")
                    }

                    val reply = aiEngine.generateOnce(
                        prompt = fullPrompt,
                        activeProviderId = target.providerId,
                        customProviders = customProviders
                    )

                    _sharedContext.update { prev ->
                        (prev + "\n[${target.name}]: ${reply.text.take(400)}").takeLast(3000)
                    }
                    updateAgent(agentId) {
                        it.copy(
                            status = AgentRunStatus.COMPLETED,
                            logs = it.logs + "Completed in ${reply.latencyMs}ms",
                            result = reply.text
                        )
                    }
                    onLogPersisted(target, "completed", reply.text)
                }
            } catch (e: CancellationException) {
                updateAgent(agentId) {
                    it.copy(
                        status = AgentRunStatus.CANCELLED,
                        logs = it.logs + "Cancelled by user / Emergency Stop"
                    )
                }
                throw e
            } catch (e: Exception) {
                updateAgent(agentId) {
                    it.copy(
                        status = AgentRunStatus.FAILED,
                        logs = it.logs + "Error: ${e.localizedMessage ?: "Unknown failure"}",
                        result = e.localizedMessage ?: "Failed"
                    )
                }
                onLogPersisted(target, "failed", e.localizedMessage ?: "Failed")
            }
        }
        activeJobs[agentId] = job
    }

    fun cancelAgent(agentId: String) {
        activeJobs[agentId]?.cancel()
        activeJobs.remove(agentId)
    }

    fun emergencyStopAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _agents.update { list ->
            list.map {
                if (it.status == AgentRunStatus.RUNNING || it.status == AgentRunStatus.QUEUED) {
                    it.copy(
                        status = AgentRunStatus.CANCELLED,
                        logs = it.logs + "EMERGENCY STOP triggered"
                    )
                } else it
            }
        }
    }

    private fun updateAgent(id: String, transform: (WorkspaceAgent) -> WorkspaceAgent) {
        _agents.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun defaultAgents(): List<WorkspaceAgent> = listOf(
        WorkspaceAgent(
            id = "coding_agent",
            name = "Coding Agent",
            roleType = "Kotlin / Web / Termux",
            avatarCode = "CD",
            instructions = "Write clean, production-grade Kotlin, Jetpack Compose, HTML/CSS/JS, Python, and Termux scripts.",
            tools = listOf("Code Studio", "Termux Runner", "Syntax Debugger")
        ),
        WorkspaceAgent(
            id = "research_agent",
            name = "Research Agent",
            roleType = "Deep Analysis & Synthesis",
            avatarCode = "RS",
            instructions = "Analyze technical problems, compare free hosting/build architectures, and synthesize structured briefs.",
            tools = listOf("Knowledge Graph", "Memory Context")
        ),
        WorkspaceAgent(
            id = "browser_agent",
            name = "Browser Agent",
            roleType = "Web Page Intelligence",
            avatarCode = "BR",
            instructions = "Summarize web pages, extract structured insights, and answer user questions from browsing sessions.",
            tools = listOf("WebView Extractor", "Bookmarks")
        ),
        WorkspaceAgent(
            id = "phone_agent",
            name = "Phone Assistant Agent",
            roleType = "Device & Accessibility Actions",
            avatarCode = "PH",
            instructions = "Plan safe Android device control steps (alarms, apps, flashlight, navigation). Always require user confirmation.",
            tools = listOf("System Intents", "Accessibility"),
            requiresConfirmation = true
        ),
        WorkspaceAgent(
            id = "whatsapp_agent",
            name = "WhatsApp Assistant Agent",
            roleType = "Drafting & Smart Replies",
            avatarCode = "WA",
            instructions = "Draft polite, concise WhatsApp replies in English, Hindi, or Hinglish. Never auto-send without confirmation.",
            tools = listOf("WhatsApp Share Sheet", "Draft History"),
            requiresConfirmation = true
        )
    )
}

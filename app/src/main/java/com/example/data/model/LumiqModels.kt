package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val publicId: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    @get:PropertyName("isPublic") @set:PropertyName("isPublic")
    var isPublic: Boolean = true,
    val vipStatus: String = "free",
    val deviceId: String = "",
    val activeLicenseCode: String? = null,
    @get:PropertyName("isBanned") @set:PropertyName("isBanned")
    var isBanned: Boolean = false,
    val trialStartedAt: Timestamp? = null,
    val trialExpiresAt: Timestamp? = null,
    val vipExpiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val publicId: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    @get:PropertyName("isPublic") @set:PropertyName("isPublic")
    var isPublic: Boolean = true,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FollowRelation(
    @DocumentId val id: String = "",
    val followerUid: String = "",
    val targetUid: String = "",
    val targetPublicId: String = "",
    val status: String = "accepted",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New LUMIQ Session",
    val provider: String = "Firebase AI Logic",
    val model: String = "gemini-3.8-flash",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessageItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val model: String = "gemini-3.8-flash",
    val latencyMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserMemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "fact",
    val title: String = "",
    val content: String = "",
    @get:PropertyName("isPinned") @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudioProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerPublicId: String = "",
    val name: String = "",
    val projectType: String = "website",
    val framework: String = "HTML5 / Tailwind",
    val packageName: String = "",
    val buildStatus: String = "draft",
    val deploymentStatus: String = "not_deployed",
    val publicUrl: String = "",
    val version: String = "1.0.0",
    val summary: String = "",
    @get:PropertyName("isModerated") @set:PropertyName("isModerated")
    var isModerated: Boolean = false,
    val moderationReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AgentTaskRun(
    @DocumentId val id: String = "",
    val userId: String = "",
    val agentType: String = "coding",
    val agentName: String = "Coding Agent",
    val prompt: String = "",
    val status: String = "queued",
    val resultSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "system",
    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val severity: String = "info",
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "",
    val minSupportedVersion: String = "1.0.0",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// Local encrypted / studio domain models
enum class AiProviderType(val displayName: String, val defaultEndpoint: String, val defaultModel: String) {
    FIREBASE_GEMINI("Firebase AI Logic (Zero-Key)", "firebase://ai-logic", "gemini-3.8-flash"),
    GEMINI_FAST_LITE("Firebase Flash-Lite (Fast)", "firebase://ai-logic", "gemini-3.5-flash-lite"),
    GEMINI_ARGON("Firebase Gemini 4 Argon", "firebase://ai-logic", "gemini-4-argon"),
    OPENAI_COMPAT("OpenAI Compatible", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
    GROQ("Groq Fast Inference", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
    CUSTOM_ENDPOINT("Custom Endpoint", "", "custom-model")
}

data class CustomProviderConfig(
    val id: String,
    val providerType: AiProviderType,
    val label: String,
    val endpointUrl: String,
    val modelName: String,
    val encryptedApiKey: String,
    val isConnectedVerified: Boolean = false,
    val lastLatencyMs: Long = 0L
)

data class VaultEntry(
    val id: String,
    val title: String,
    val usernameOrLabel: String,
    val encryptedSecret: String,
    val category: String = "Password",
    val updatedEpochMs: Long = System.currentTimeMillis()
)

data class LumiqVoiceProfile(
    val id: String,
    val name: String,
    val localeTag: String,
    val pitch: Float,
    val speed: Float,
    val volume: Float,
    val styleLabel: String
)

data class CodeFileItem(
    val path: String,
    val language: String,
    val content: String
)

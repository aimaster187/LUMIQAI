package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AgentTaskRun
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.ChatMessageItem
import com.example.data.model.ChatSession
import com.example.data.model.FollowRelation
import com.example.data.model.PublicProfile
import com.example.data.model.StudioProject
import com.example.data.model.UserMemoryItem
import com.example.data.model.UserNotification
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class LumiqFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun deviceFingerprint(): String {
        val raw = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.DEVICE}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return raw.take(64).ifBlank { "android_device" }
    }

    suspend fun ensureUserProfileProvisioned(user: FirebaseUser): Result<UserProfile> {
        val uid = user.uid
        val userDoc = db.collection("users").document(uid)
        val privateDoc = userDoc.collection("private").document("account")
        return try {
            val snap = userDoc.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }

            val publicId = LumiqAuthRepository.derivePermanentPublicId(uid)
            val baseName = (user.displayName ?: user.email?.substringBefore("@") ?: "LumiqUser")
                .trim()
                .take(30)
                .ifBlank { "LumiqUser" }

            val userPayload = mapOf(
                "userId" to uid,
                "publicId" to publicId,
                "username" to baseName,
                "bio" to "Exploring LUMIQ AI",
                "avatarUrl" to (user.photoUrl?.toString() ?: ""),
                "isPublic" to true,
                "vipStatus" to "free",
                "deviceId" to deviceFingerprint(),
                "isBanned" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val privatePayload = mapOf(
                "userId" to uid,
                "email" to (user.email ?: ""),
                "provider" to "google.com",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val publicPayload = mapOf(
                "userId" to uid,
                "publicId" to publicId,
                "username" to baseName,
                "bio" to "Exploring LUMIQ AI",
                "avatarUrl" to (user.photoUrl?.toString() ?: ""),
                "isPublic" to true,
                "followersCount" to 0L,
                "followingCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val usernameKey = baseName.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_").take(40)
                .ifBlank { "user_${uid.take(6)}" }
            val usernameRef = db.collection("usernames").document(usernameKey)
            val publicRef = db.collection("publicProfiles").document(publicId)

            val batch = db.batch()
            batch.set(userDoc, userPayload)
            batch.set(privateDoc, privatePayload)
            batch.set(publicRef, publicPayload)
            if (!usernameRef.get().await().exists()) {
                batch.set(
                    usernameRef,
                    mapOf(
                        "userId" to uid,
                        "publicId" to publicId,
                        "username" to baseName,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            val created = userDoc.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, publicId = publicId, username = baseName)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun updateProfileDetails(
        userId: String,
        publicId: String,
        username: String,
        bio: String,
        isPublic: Boolean
    ): Result<Unit> {
        val cleanName = username.trim().take(40)
        require(cleanName.length >= 2) { "Username must be at least 2 characters" }
        val userDoc = db.collection("users").document(userId)
        val publicDoc = db.collection("publicProfiles").document(publicId)
        return try {
            val batch = db.batch()
            batch.update(
                userDoc,
                mapOf(
                    "username" to cleanName,
                    "bio" to bio.trim().take(240),
                    "isPublic" to isPublic,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                publicDoc,
                mapOf(
                    "username" to cleanName,
                    "bio" to bio.trim().take(240),
                    "isPublic" to isPublic,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    suspend fun startTenMinuteTrial(userId: String): Result<Unit> {
        val userDoc = db.collection("users").document(userId)
        return try {
            val expiresDate = Date(System.currentTimeMillis() + 10 * 60 * 1000L)
            userDoc.update(
                mapOf(
                    "vipStatus" to "trial",
                    "trialStartedAt" to FieldValue.serverTimestamp(),
                    "trialExpiresAt" to Timestamp(expiresDate),
                    "deviceId" to deviceFingerprint(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    suspend fun redeemLicenseKey(userId: String, rawCode: String): Result<String> {
        val code = rawCode.trim()
        if (!code.matches(Regex("^[a-zA-Z0-9_\\-]{6,64}$"))) {
            return Result.failure(IllegalArgumentException("Invalid license key format"))
        }
        val licenseDoc = db.collection("licenses").document(code)
        val userDoc = db.collection("users").document(userId)
        return try {
            val snap = licenseDoc.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("License key not found"))
            }
            val isActive = snap.getBoolean("isActive") == true
            val alreadyRedeemed = snap.getString("redeemedByUid")
            if (!isActive || !alreadyRedeemed.isNullOrBlank()) {
                return Result.failure(IllegalStateException("License key has already been used or deactivated"))
            }
            val tier = snap.getString("tier") ?: "vip"
            val durationDays = snap.getLong("durationDays") ?: 30L
            val device = deviceFingerprint()

            val batch = db.batch()
            batch.update(
                licenseDoc,
                mapOf(
                    "redeemedByUid" to userId,
                    "boundDeviceId" to device,
                    "isActive" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val userUpdates = mutableMapOf<String, Any>(
                "vipStatus" to tier,
                "activeLicenseCode" to code,
                "deviceId" to device,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (tier != "lifetime" && durationDays > 0) {
                val expiry = Date(System.currentTimeMillis() + durationDays * 86_400_000L)
                userUpdates["vipExpiresAt"] = Timestamp(expiry)
            }
            batch.update(userDoc, userUpdates)
            batch.commit().await()
            Result.success(tier)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, licenseDoc.path)
            Result.failure(e)
        }
    }

    // --- Chats & Messages ---
    fun observeChats(userId: String): Flow<List<ChatSession>> =
        db.collection("users").document(userId).collection("chats")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(ChatSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeMessages(userId: String, chatId: String): Flow<List<ChatMessageItem>> =
        db.collection("users").document(userId).collection("chats")
            .document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { it.toObjects(ChatMessageItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun createChatSession(
        userId: String,
        title: String,
        provider: String,
        model: String
    ): Result<String> {
        val ref = db.collection("users").document(userId).collection("chats").document()
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().take(160).ifBlank { "New LUMIQ Chat" },
            "provider" to provider.take(64),
            "model" to model.take(120),
            "lastMessagePreview" to "",
            "messageCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun renameChatSession(userId: String, chatId: String, newTitle: String): Result<Unit> {
        val ref = db.collection("users").document(userId).collection("chats").document(chatId)
        return try {
            ref.update(
                mapOf(
                    "title" to newTitle.trim().take(160).ifBlank { "LUMIQ Chat" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteChatSession(userId: String, chatId: String): Result<Unit> {
        val ref = db.collection("users").document(userId).collection("chats").document(chatId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun appendChatMessage(
        userId: String,
        chatId: String,
        role: String,
        content: String,
        model: String,
        latencyMs: Long = 0L
    ): Result<String> {
        val chatRef = db.collection("users").document(userId).collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val msgPayload = mapOf(
            "userId" to userId,
            "role" to role,
            "content" to content.take(31000).ifBlank { " " },
            "model" to model.take(120),
            "latencyMs" to latencyMs.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessagePreview" to content.take(240),
                    "messageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    // --- AI Memory ---
    fun observeMemories(userId: String): Flow<List<UserMemoryItem>> =
        db.collection("users").document(userId).collection("memories")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(UserMemoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun saveMemory(
        userId: String,
        category: String,
        title: String,
        content: String,
        isPinned: Boolean = false
    ): Result<String> {
        val ref = db.collection("users").document(userId).collection("memories").document()
        val payload = mapOf(
            "userId" to userId,
            "category" to category,
            "title" to title.trim().take(120),
            "content" to content.trim().take(4000),
            "isPinned" to isPinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
        val ref = db.collection("users").document(userId).collection("memories").document(memoryId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    // --- Studio Projects (Websites & Android Apps) ---
    fun observeUserProjects(userId: String): Flow<List<StudioProject>> =
        db.collection("projects")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(StudioProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }

    suspend fun createProject(
        userId: String,
        ownerPublicId: String,
        name: String,
        projectType: String,
        framework: String,
        packageName: String = "",
        summary: String = ""
    ): Result<String> {
        val ref = db.collection("projects").document()
        val payload = mapOf(
            "userId" to userId,
            "ownerPublicId" to ownerPublicId.take(32),
            "name" to name.trim().take(120),
            "projectType" to projectType,
            "framework" to framework.take(64),
            "packageName" to packageName.take(128),
            "buildStatus" to "ready",
            "deploymentStatus" to "not_deployed",
            "publicUrl" to "",
            "version" to "1.0.0",
            "summary" to summary.take(1000),
            "isModerated" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateProjectDeployment(
        projectId: String,
        buildStatus: String,
        deploymentStatus: String,
        publicUrl: String
    ): Result<Unit> {
        val ref = db.collection("projects").document(projectId)
        return try {
            ref.update(
                mapOf(
                    "buildStatus" to buildStatus,
                    "deploymentStatus" to deploymentStatus,
                    "publicUrl" to publicUrl.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val ref = db.collection("projects").document(projectId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    // --- Agent Tasks ---
    fun observeAgentTasks(userId: String): Flow<List<AgentTaskRun>> =
        db.collection("users").document(userId).collection("agentTasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(AgentTaskRun::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun logAgentTask(
        userId: String,
        agentType: String,
        agentName: String,
        prompt: String,
        status: String,
        resultSummary: String = ""
    ): Result<String> {
        val ref = db.collection("users").document(userId).collection("agentTasks").document()
        val payload = mapOf(
            "userId" to userId,
            "agentType" to agentType.take(64),
            "agentName" to agentName.take(80),
            "prompt" to prompt.take(4000),
            "status" to status,
            "resultSummary" to resultSummary.take(8000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateAgentTaskStatus(
        userId: String,
        taskId: String,
        status: String,
        resultSummary: String
    ): Result<Unit> {
        val ref = db.collection("users").document(userId).collection("agentTasks").document(taskId)
        return try {
            ref.update(
                mapOf(
                    "status" to status,
                    "resultSummary" to resultSummary.take(8000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    // --- Notifications, Announcements & Public Social ---
    fun observeNotifications(userId: String): Flow<List<UserNotification>> =
        db.collection("users").document(userId).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(UserNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun pushNotification(userId: String, title: String, body: String, type: String = "system") {
        val ref = db.collection("users").document(userId).collection("notifications").document()
        runCatching {
            ref.set(
                mapOf(
                    "userId" to userId,
                    "title" to title.take(120),
                    "body" to body.take(1000),
                    "type" to type.take(40),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }.onFailure {
            handleFirestoreError(it, OperationType.CREATE, ref.path)
        }
    }

    fun observeAnnouncements(): Flow<List<Announcement>> =
        db.collection("announcements")
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { it.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeAppConfig(): Flow<AppConfig?> =
        db.collection("config").document("app_status")
            .snapshots()
            .map { it.toObject(AppConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun searchPublicProfiles(query: String): Result<List<PublicProfile>> {
        return try {
            val snap = db.collection("publicProfiles")
                .whereEqualTo("isPublic", true)
                .limit(30)
                .get()
                .await()
            val all = snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val q = query.trim().lowercase()
            val filtered = if (q.isBlank()) all else all.filter {
                it.publicId.lowercase().contains(q) || it.username.lowercase().contains(q)
            }
            Result.success(filtered)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "publicProfiles")
            Result.failure(e)
        }
    }

    suspend fun followUser(followerUid: String, target: PublicProfile): Result<Unit> {
        val docId = "${followerUid}_${target.userId}".take(120)
        val ref = db.collection("follows").document(docId)
        return try {
            ref.set(
                mapOf(
                    "followerUid" to followerUid,
                    "targetUid" to target.userId,
                    "targetPublicId" to target.publicId,
                    "status" to if (target.isPublic) "accepted" else "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeFollowing(followerUid: String): Flow<List<FollowRelation>> =
        db.collection("follows")
            .whereEqualTo("followerUid", followerUid)
            .snapshots()
            .map { it.toObjects(FollowRelation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
}

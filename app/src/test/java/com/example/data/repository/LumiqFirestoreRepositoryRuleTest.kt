package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LumiqFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfileProvisioned_authenticatedUser_createsProfileAndPrivateAccount() = runBlocking {
        signInTestUser("alice_${UUID.randomUUID()}@lumiqlabs.ai")
        val user = checkNotNull(auth.currentUser)
        val repo = LumiqFirestoreRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfileProvisioned(user)
        }
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(user.uid, profile.userId)
        assertTrue(profile.publicId.startsWith("LUM-"))
    }

    @Test
    fun observeMemories_authenticatedOwner_createsAndObservesMemory() = runBlocking {
        val uid = signInTestUser("alice_mem_${UUID.randomUUID()}@lumiqlabs.ai")
        val repo = LumiqFirestoreRepository(firestore)
        val title = "Pref_${UUID.randomUUID().toString().take(8)}"

        val memId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveMemory(uid, "preference", title, "Respond in concise Hinglish", true).getOrThrow()
        }
        val list = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMemories(uid).first { items -> items.any { it.id == memId } }
        }
        assertTrue(list.any { it.title == title })
    }

    @Test
    fun observeUserProjects_crossUserAndOwnerIsolation_enforcedByRules() = runBlocking {
        val aliceUid = signInTestUser("alice_proj_${UUID.randomUUID()}@lumiqlabs.ai")
        val repo = LumiqFirestoreRepository(firestore)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createProject(
                userId = aliceUid,
                ownerPublicId = "LUM-TEST1234",
                name = "Lumiq Store",
                projectType = "website",
                framework = "HTML5",
                summary = "E-commerce test"
            ).getOrThrow()
        }

        val aliceProjects = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProjects(aliceUid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(aliceProjects.any { it.id == projectId })

        // Switch to Bob and verify Bob cannot query Alice's projects
        signInTestUser("bob_proj_${UUID.randomUUID()}@lumiqlabs.ai")
        var failedWithPermissionDenied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserProjects(aliceUid).first()
            }
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            failedWithPermissionDenied = ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED for cross-user project query", failedWithPermissionDenied)
    }

    @Test
    fun observeUserProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = LumiqFirestoreRepository(firestore)

        var failedWithPermissionDenied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserProjects("some_uid_123").first()
            }
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            failedWithPermissionDenied = ffe?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED for unauthenticated caller", failedWithPermissionDenied)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

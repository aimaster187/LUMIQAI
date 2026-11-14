package com.example

import com.example.data.model.AiProviderType
import com.example.data.repository.LumiqAuthRepository
import com.example.security.KeystoreCryptoManager
import com.example.voice.SpeakerVoiceprintEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LumiqCoreUnitTest {

    @Test
    fun pinHashingAndVerificationWorksWithSalt() {
        val salt = KeystoreCryptoManager.generateSaltBase64()
        val hash = KeystoreCryptoManager.hashPin("4829", salt)
        assertTrue(KeystoreCryptoManager.verifyPin("4829", salt, hash))
        assertFalse(KeystoreCryptoManager.verifyPin("0000", salt, hash))
        assertFalse(KeystoreCryptoManager.verifyPin("4829", "", hash))
    }

    @Test
    fun fallbackEncryptionRoundTripsPlaintext() {
        val secret = "sk-lumiq-test-secret-9911"
        val cipher = KeystoreCryptoManager.encrypt(secret)
        assertNotEquals(secret, cipher)
        assertEquals(secret, KeystoreCryptoManager.decrypt(cipher))
    }

    @Test
    fun publicIdIsDeterministicAndFormatted() {
        val idA1 = LumiqAuthRepository.derivePermanentPublicId("uid_alpha_123")
        val idA2 = LumiqAuthRepository.derivePermanentPublicId("uid_alpha_123")
        val idB = LumiqAuthRepository.derivePermanentPublicId("uid_beta_456")

        assertEquals(idA1, idA2)
        assertNotEquals(idA1, idB)
        assertTrue(idA1.matches(Regex("^LUM-[A-Z2-9]{8}$")))
        assertTrue(idB.matches(Regex("^LUM-[A-Z2-9]{8}$")))
    }

    @Test
    fun speakerVoiceprintMatchesSameSpeakerAndRejectsDifferentSpeaker() {
        val ownerSample1 = synthesizeHarmonicVoice(f0Hz = 125.0, harmonicBoost = 0.25, durationMs = 1200)
        val ownerSample2 = synthesizeHarmonicVoice(f0Hz = 128.0, harmonicBoost = 0.27, durationMs = 1200)
        val otherSpeaker = synthesizeHarmonicVoice(f0Hz = 245.0, harmonicBoost = 0.85, durationMs = 1200, highBrightness = true)

        val enrolled = SpeakerVoiceprintEngine.extractVoiceprint(ownerSample1).getOrThrow()
        val ownerProbe = SpeakerVoiceprintEngine.extractVoiceprint(ownerSample2).getOrThrow()
        val otherProbe = SpeakerVoiceprintEngine.extractVoiceprint(otherSpeaker).getOrThrow()

        val ownerRes = SpeakerVoiceprintEngine.verify(enrolled, ownerProbe)
        val otherRes = SpeakerVoiceprintEngine.verify(enrolled, otherProbe)

        assertTrue("Expected owner voiceprint to match (sim=${ownerRes.similarity})", ownerRes.isMatch)
        assertTrue(
            "Expected distinct speaker similarity (${otherRes.similarity}) to be lower than owner (${ownerRes.similarity})",
            otherRes.similarity < ownerRes.similarity
        )

        val decoded = SpeakerVoiceprintEngine.decodeProfile(SpeakerVoiceprintEngine.encodeProfile(enrolled))
        assertNotNull(decoded)
        assertEquals(enrolled.f0Hz, decoded!!.f0Hz, 0.01f)
    }

    @Test
    fun firebaseModelIdsMatchOfficialIdsWithoutUnverifiedLatencyClaim() {
        assertEquals("gemini-3.8-flash", AiProviderType.FIREBASE_GEMINI.defaultModel)
        assertEquals("gemini-3.5-flash-lite", AiProviderType.GEMINI_FAST_LITE.defaultModel)
        assertEquals("gemini-4-argon", AiProviderType.GEMINI_ARGON.defaultModel)
        assertFalse(AiProviderType.GEMINI_FAST_LITE.displayName.contains("0.2s"))
    }

    private fun synthesizeHarmonicVoice(
        f0Hz: Double,
        harmonicBoost: Double,
        durationMs: Int,
        highBrightness: Boolean = false
    ): ShortArray {
        val sr = SpeakerVoiceprintEngine.SAMPLE_RATE
        val n = (sr * durationMs) / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sr
            var v = 0.45 * sin(2.0 * PI * f0Hz * t) +
                harmonicBoost * sin(2.0 * PI * (f0Hz * 2.0) * t) +
                0.15 * sin(2.0 * PI * (f0Hz * 3.0) * t)
            if (highBrightness) {
                v += 0.35 * sin(2.0 * PI * 3200.0 * t) + 0.25 * sin(2.0 * PI * 4600.0 * t)
            }
            (v * 14000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}


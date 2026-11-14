package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.device.PhoneControlManager
import com.example.service.LumiqVoiceForegroundService
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LumiqGlassCard
import com.example.voice.LumiqVoiceEngine
import com.example.voice.VoiceEngineState

@Composable
fun VoiceStudioScreen(
    voiceEngine: LumiqVoiceEngine,
    sttLocale: String,
    wakeWord: String,
    activeVoiceId: String,
    speed: Float,
    pitch: Float,
    volume: Float,
    onSyncVoiceConfig: (String, String, String, Float, Float, Float) -> Unit,
    onVoiceCommandRecognized: (String) -> Unit,
    hasEnrolledVoiceprint: Boolean = false,
    enrolledF0Hz: Float = 0f,
    speakerVerifyStatus: String? = null,
    isRecordingVoiceprint: Boolean = false,
    onEnrollSpeakerVoice: () -> Unit = {},
    onVerifySpeakerVoice: () -> Unit = {},
    onClearSpeakerVoiceprint: () -> Unit = {}
) {
    val context = LocalContext.current
    val engineState by voiceEngine.engineState.collectAsState()
    val partialText by voiceEngine.partialTranscript.collectAsState()
    val history by voiceEngine.recognitionHistory.collectAsState()
    val serviceRunning by LumiqVoiceForegroundService.isRunning.collectAsState()

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voiceEngine.startListeningSession(continuous = true, onTranscript = onVoiceCommandRecognized)
        }
    }

    val servicePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (audioOk) {
            LumiqVoiceForegroundService.start(context)
            voiceEngine.startListeningSession(continuous = true, onTranscript = onVoiceCommandRecognized)
        }
    }

    val speakerEnrollPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onEnrollSpeakerVoice()
    }
    val speakerVerifyPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onVerifySpeakerVoice()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 104.dp, top = 6.dp)
    ) {
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LUMIQ Voice Studio & Engine", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Engine State: ${engineState.name} • Echo-Gating Active (Mic automatically gates during TTS playback)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricBlue
                    )
                    if (partialText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Live Partial: $partialText", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (engineState == VoiceEngineState.LISTENING) {
                                    voiceEngine.stopListeningSession()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        voiceEngine.startListeningSession(
                                            continuous = true,
                                            onTranscript = onVoiceCommandRecognized
                                        )
                                    } else {
                                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (engineState == VoiceEngineState.LISTENING) CrimsonError else ElectricBlue
                            ),
                            modifier = Modifier.weight(1f).testTag("voice_studio_toggle_mic")
                        ) {
                            Icon(
                                if (engineState == VoiceEngineState.LISTENING) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (engineState == VoiceEngineState.LISTENING) "Stop Continuous Mic" else "Start Continuous Mic")
                        }
                    }
                }
            }
        }

        // Wake Word & Background Foreground Service
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Wake Word & Background Service", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LumiqVoiceEngine.WAKE_WORDS.forEach { w ->
                            FilterChip(
                                selected = wakeWord == w,
                                onClick = { onSyncVoiceConfig(sttLocale, w, activeVoiceId, speed, pitch, volume) },
                                label = { Text(w) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: Continuous background listening uses Android Foreground Microphone Service with a persistent notification. OEM battery savers or Force Stop may halt background services; no app can guarantee wake-word survival after Force Stop or reboot without system privileges.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (serviceRunning) {
                                    LumiqVoiceForegroundService.stop(context)
                                    voiceEngine.stopListeningSession()
                                } else {
                                    val perms = buildList {
                                        add(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }.toTypedArray()
                                    servicePermLauncher.launch(perms)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("background_voice_service_btn")
                        ) {
                            Text(if (serviceRunning) "Stop BG Service" else "Start BG Service")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.BatterySaver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (PhoneControlManager.isIgnoringBatteryOptimizations(context)) "Unrestricted" else "Battery Guide")
                        }
                    }
                }
            }
        }

        // Owner Speaker Recognition (Voiceprint Identity)
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Owner Speaker Recognition (Voiceprint)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (hasEnrolledVoiceprint) {
                            "Enrolled on-device voiceprint active (~${enrolledF0Hz.toInt()} Hz fundamental pitch, Keystore AES encrypted)."
                        } else {
                            "Not enrolled. Record a 2.6s sample so LUMIQ can verify your acoustic voiceprint (F0 pitch + 12-band Mel spectrum)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasEnrolledVoiceprint) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!speakerVerifyStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            speakerVerifyStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) onEnrollSpeakerVoice()
                                else speakerEnrollPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = !isRecordingVoiceprint,
                            modifier = Modifier.weight(1f).testTag("enroll_speaker_voice_btn")
                        ) {
                            Text(if (hasEnrolledVoiceprint) "Re-Enroll Voice" else "Enroll Voice")
                        }
                        OutlinedButton(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) onVerifySpeakerVoice()
                                else speakerVerifyPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = hasEnrolledVoiceprint && !isRecordingVoiceprint,
                            modifier = Modifier.weight(1f).testTag("verify_speaker_voice_btn")
                        ) {
                            Text("Verify Speaker")
                        }
                        if (hasEnrolledVoiceprint) {
                            OutlinedButton(
                                onClick = onClearSpeakerVoiceprint,
                                enabled = !isRecordingVoiceprint
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }

        // STT Language Selector
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Speech Recognition Language (STT)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LumiqVoiceEngine.SUPPORTED_LOCALES.forEach { (tag, label) ->
                            FilterChip(
                                selected = sttLocale == tag,
                                onClick = { onSyncVoiceConfig(tag, wakeWord, activeVoiceId, speed, pitch, volume) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // TTS Tuning & Profiles
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("LUMIQ TTS Voice Tuning", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Speed: ${"%.2f".format(speed)}x", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = speed,
                        onValueChange = { onSyncVoiceConfig(sttLocale, wakeWord, activeVoiceId, it, pitch, volume) },
                        valueRange = 0.6f..1.6f
                    )
                    Text("Pitch: ${"%.2f".format(pitch)}", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = pitch,
                        onValueChange = { onSyncVoiceConfig(sttLocale, wakeWord, activeVoiceId, speed, it, volume) },
                        valueRange = 0.6f..1.5f
                    )
                    Text("Volume: ${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = volume,
                        onValueChange = { onSyncVoiceConfig(sttLocale, wakeWord, activeVoiceId, speed, pitch, it) },
                        valueRange = 0.2f..1.0f
                    )
                }
            }
        }

        items(LumiqVoiceEngine.CURATED_PROFILES, key = { it.id }) { profile ->
            val selected = profile.id == activeVoiceId
            LumiqGlassCard(
                onClick = {
                    onSyncVoiceConfig(sttLocale, wakeWord, profile.id, profile.speed, profile.pitch, profile.volume)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text("${profile.styleLabel} • ${profile.localeTag}", style = MaterialTheme.typography.labelSmall, color = if (selected) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { voiceEngine.previewProfile(profile) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview voice", tint = ElectricBlue)
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Text("Recognition History", style = MaterialTheme.typography.titleMedium)
            }
            items(history.take(10)) { rec ->
                LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(rec.text, style = MaterialTheme.typography.bodyLarge)
                        Text("Locale: ${rec.locale}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

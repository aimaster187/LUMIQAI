package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiProviderType
import com.example.data.model.ChatSession
import com.example.data.model.CustomProviderConfig
import com.example.data.model.UserProfile
import com.example.ui.components.LumiqOrbCanvas
import com.example.ui.components.LumiqRoute
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LumiqGlassCard
import com.example.ui.viewmodel.ChatStreamState
import com.example.voice.VoiceEngineState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    userProfile: UserProfile?,
    voiceState: VoiceEngineState,
    rmsLevel: Float,
    partialTranscript: String,
    lastReply: String,
    chatStreamState: ChatStreamState,
    activeProviderId: String,
    customProviders: List<CustomProviderConfig>,
    lastLatencyMs: Long,
    wakeWord: String,
    recentChats: List<ChatSession>,
    activeAgentCount: Int,
    onOrbClick: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onSelectProvider: (String) -> Unit,
    onOpenChat: (String?) -> Unit,
    onNavigate: (LumiqRoute) -> Unit
) {
    var quickInput by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning,"
        hour < 17 -> "Good afternoon,"
        else -> "Good evening,"
    }
    val displayName = userProfile?.username?.ifBlank { "BOSS" } ?: "BOSS"
    val todayDay = SimpleDateFormat("dd", Locale.getDefault()).format(Date())
    val todayLabel = SimpleDateFormat("EEE, MMM", Locale.getDefault()).format(Date())

    val activeModelLabel = when (activeProviderId) {
        AiProviderType.GEMINI_FAST_LITE.name -> "Flash-Lite"
        AiProviderType.FIREBASE_GEMINI.name -> "3.8 Flash"
        AiProviderType.GEMINI_ARGON.name -> "4 Argon"
        else -> customProviders.firstOrNull { it.id == activeProviderId }?.label ?: "Custom AI"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Greeting Header + Floating Energy/Model Pill (inspired by reference aesthetic)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID: ${userProfile?.publicId ?: "LUM-SYNCING"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlue
                )
            }

            Box {
                LumiqGlassCard(
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    onClick = { showModelMenu = true },
                    modifier = Modifier.testTag("home_model_selector")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Model & Speed",
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(activeModelLabel, style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (lastLatencyMs > 0L) "${lastLatencyMs}ms • Tap" else "-- ms • Tap",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false }
                ) {
                    listOf(
                        AiProviderType.GEMINI_FAST_LITE.name to "Gemini 3.5 Flash-Lite (Ultra Fast)",
                        AiProviderType.FIREBASE_GEMINI.name to "Gemini 3.8 Flash (Balanced Default)",
                        AiProviderType.GEMINI_ARGON.name to "Gemini 4 Argon (Complex Reasoning)"
                    ).forEach { (id, title) ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                onSelectProvider(id)
                                showModelMenu = false
                            }
                        )
                    }
                    customProviders.filter { it.isConnectedVerified }.forEach { custom ->
                        DropdownMenuItem(
                            text = { Text("${custom.label} (${custom.modelName})") },
                            onClick = {
                                onSelectProvider(custom.id)
                                showModelMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Centerpiece 10D Neural Orb
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LumiqOrbCanvas(
                voiceState = voiceState,
                rmsLevel = rmsLevel,
                orbSize = 210.dp,
                onClick = onOrbClick
            )
        }

        // Live Listening / Streaming Status Pill
        val orbBannerText = when {
            voiceState == VoiceEngineState.LISTENING && partialTranscript.isNotBlank() ->
                "Listening: \"$partialTranscript\""
            voiceState == VoiceEngineState.LISTENING ->
                "Listening ($wakeWord)... Speak now"
            voiceState == VoiceEngineState.SPEAKING ->
                "LUMIQ Speaking... (Tap Orb to interrupt)"
            chatStreamState is ChatStreamState.Thinking ->
                "LUMIQ processing..."
            chatStreamState is ChatStreamState.Streaming ->
                chatStreamState.text
            else -> lastReply
        }

        LumiqGlassCard(
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = orbBannerText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Action Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionPill("Voice Studio", Icons.Rounded.GraphicEq) { onNavigate(LumiqRoute.VOICE_STUDIO) }
            QuickActionPill("5-Agent Hub", Icons.Rounded.Hub) { onNavigate(LumiqRoute.AGENTS) }
            QuickActionPill("Code & Termux", Icons.Rounded.Code) { onNavigate(LumiqRoute.CODE_STUDIO) }
            QuickActionPill("Web Studio", Icons.Rounded.AutoAwesome) { onNavigate(LumiqRoute.WEBSITE_STUDIO) }
            QuickActionPill("App Builder", Icons.Rounded.Android) { onNavigate(LumiqRoute.APP_STUDIO) }
            QuickActionPill("Browser", Icons.Rounded.Language) { onNavigate(LumiqRoute.BROWSER) }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3-Column Bento Status Grid (matching screenshot structure)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LumiqGlassCard(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(12.dp),
                onClick = { onNavigate(LumiqRoute.PROVIDERS) }
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Engine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (lastLatencyMs > 0L) "${lastLatencyMs}ms" else "-- ms",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(activeModelLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Connected", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                }
            }

            LumiqGlassCard(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(12.dp),
                onClick = { onNavigate(LumiqRoute.AGENTS) }
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aaj", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(todayDay, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(todayLabel, style = MaterialTheme.typography.bodyMedium)
                    Text("$activeAgentCount/5 Agents", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                }
            }

            LumiqGlassCard(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(12.dp),
                onClick = { onNavigate(LumiqRoute.VOICE_STUDIO) }
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Voice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(voiceState.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(wakeWord, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("HI / EN / Mix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Floating Pill Prompt Bar (Attachments + Camera + Ask LUMIQ + Send)
        LumiqGlassCard(
            shape = RoundedCornerShape(30.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { onNavigate(LumiqRoute.VISION_DOCS) },
                    modifier = Modifier.testTag("home_attach_button")
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach document or image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = { onNavigate(LumiqRoute.VISION_DOCS) },
                    modifier = Modifier.testTag("home_camera_button")
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Open Camera Vision", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    if (quickInput.isEmpty()) {
                        Text(
                            "Ask LUMIQ anything...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = quickInput,
                        onValueChange = { quickInput = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(ElectricBlue),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("home_prompt_input")
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricBlue, CyanGlow)))
                        .clickable(role = Role.Button) {
                            if (quickInput.isNotBlank()) {
                                val prompt = quickInput
                                quickInput = ""
                                onSendPrompt(prompt)
                            }
                        }
                        .testTag("home_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send prompt",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Recent Chats strip
        if (recentChats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("Recent Conversations", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            recentChats.take(3).forEach { chat ->
                LumiqGlassCard(
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(12.dp),
                    onClick = { onOpenChat(chat.id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (chat.lastMessagePreview.isNotBlank()) {
                                Text(
                                    chat.lastMessagePreview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${chat.messageCount} msgs",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    LumiqGlassCard(
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontSize = 13.sp)
        }
    }
}

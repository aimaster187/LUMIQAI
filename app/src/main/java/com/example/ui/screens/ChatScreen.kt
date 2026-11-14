package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AiProviderType
import com.example.data.model.ChatMessageItem
import com.example.data.model.ChatSession
import com.example.data.model.CustomProviderConfig
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LumiqGlassCard
import com.example.ui.viewmodel.ChatStreamState

@Composable
fun ChatScreen(
    chats: List<ChatSession>,
    selectedChatId: String?,
    messages: List<ChatMessageItem>,
    streamState: ChatStreamState,
    activeProviderId: String,
    customProviders: List<CustomProviderConfig>,
    onSelectChat: (String) -> Unit,
    onCreateNewChat: () -> Unit,
    onRenameChat: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSendMessage: (String, Bitmap?, String) -> Unit,
    onStopGeneration: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachedDocText by remember { mutableStateOf("") }
    var attachedDocName by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<ChatSession?>(null) }
    var historySearch by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            attachedBitmap = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
        }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val txt = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                attachedDocText = txt.take(12000)
                attachedDocName = uri.lastPathSegment ?: "document.txt"
            }
        }
    }

    LaunchedEffect(messages.size, streamState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Chat Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = false,
                onClick = { showHistoryDialog = true },
                label = { Text("History (${chats.size})") },
                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("chat_history_button")
            )
            FilterChip(
                selected = false,
                onClick = onCreateNewChat,
                label = { Text("New Chat") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("new_chat_button")
            )
            Box {
                FilterChip(
                    selected = true,
                    onClick = { showModelMenu = true },
                    label = {
                        Text(
                            when (activeProviderId) {
                                AiProviderType.GEMINI_FAST_LITE.name -> "Flash-Lite"
                                AiProviderType.FIREBASE_GEMINI.name -> "3.8 Flash"
                                AiProviderType.GEMINI_ARGON.name -> "4 Argon"
                                else -> customProviders.firstOrNull { it.id == activeProviderId }?.label ?: "Custom"
                            }
                        )
                    }
                )
                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                    DropdownMenuItem(text = { Text("Gemini 3.5 Flash-Lite (Fast)") }, onClick = {
                        onSelectProvider(AiProviderType.GEMINI_FAST_LITE.name); showModelMenu = false
                    })
                    DropdownMenuItem(text = { Text("Gemini 3.8 Flash (Default)") }, onClick = {
                        onSelectProvider(AiProviderType.FIREBASE_GEMINI.name); showModelMenu = false
                    })
                    DropdownMenuItem(text = { Text("Gemini 4 Argon (Deep)") }, onClick = {
                        onSelectProvider(AiProviderType.GEMINI_ARGON.name); showModelMenu = false
                    })
                    customProviders.filter { it.isConnectedVerified }.forEach { prov ->
                        DropdownMenuItem(text = { Text(prov.label) }, onClick = {
                            onSelectProvider(prov.id); showModelMenu = false
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty() && streamState is ChatStreamState.Idle) {
                item {
                    LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Multi-Model LUMIQ Chat", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Ask anything, attach photos or documents, switch between Gemini Flash-Lite / 3.8 Flash / 4 Argon or your own OpenAI/Groq API endpoints.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id.ifBlank { it.hashCode() } }) { msg ->
                MessageBubble(
                    message = msg,
                    onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg.content)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share LUMIQ reply"))
                    }
                )
            }

            when (streamState) {
                is ChatStreamState.Thinking -> item {
                    LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("LUMIQ is thinking...", style = MaterialTheme.typography.bodyMedium, color = ElectricBlue)
                    }
                }
                is ChatStreamState.Streaming -> item {
                    MessageBubble(
                        message = ChatMessageItem(role = "model", content = streamState.text, model = activeProviderId),
                        onCopy = { clipboard.setText(AnnotatedString(streamState.text)) },
                        onShare = {}
                    )
                }
                is ChatStreamState.Error -> item {
                    LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(streamState.message, color = CrimsonError, modifier = Modifier.weight(1f))
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = ElectricBlue)
                            }
                        }
                    }
                }
                ChatStreamState.Idle -> {}
            }
        }

        // Attachment Preview Chips
        if (attachedBitmap != null || attachedDocName.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                attachedBitmap?.let { bmp ->
                    Box {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached image",
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                        )
                        IconButton(
                            onClick = { attachedBitmap = null },
                            modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove image", tint = Color.White)
                        }
                    }
                }
                if (attachedDocName.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(attachedDocName, style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { attachedDocName = ""; attachedDocText = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove document", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Composer Row
        LumiqGlassCard(
            shape = RoundedCornerShape(26.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 92.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Rounded.Image, contentDescription = "Pick Photo")
                }
                IconButton(onClick = { docPicker.launch(arrayOf("text/*", "application/json")) }) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach Text Document")
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Message LUMIQ...") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f).testTag("chat_message_input")
                )
                if (streamState is ChatStreamState.Thinking || streamState is ChatStreamState.Streaming) {
                    IconButton(onClick = onStopGeneration, modifier = Modifier.testTag("chat_stop_button")) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop Generation", tint = CrimsonError)
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() || attachedBitmap != null || attachedDocText.isNotBlank()) {
                                val prompt = inputText.ifBlank { "Analyze the attached content." }
                                val bmp = attachedBitmap
                                val doc = attachedDocText
                                inputText = ""
                                attachedBitmap = null
                                attachedDocText = ""
                                attachedDocName = ""
                                onSendMessage(prompt, bmp, doc)
                            }
                        },
                        modifier = Modifier.testTag("chat_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = ElectricBlue)
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        val filtered = chats.filter {
            historySearch.isBlank() || it.title.contains(historySearch, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Chat History") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = historySearch,
                        onValueChange = { historySearch = it },
                        label = { Text("Search chats") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(filtered, key = { it.id }) { session ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        onSelectChat(session.id)
                                        showHistoryDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        session.title,
                                        fontWeight = if (session.id == selectedChatId) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                IconButton(onClick = { showRenameDialog = session }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename chat")
                                }
                                IconButton(onClick = { onDeleteChat(session.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete chat", tint = CrimsonError)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
            }
        )
    }

    showRenameDialog?.let { session ->
        var newName by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    onRenameChat(session.id, newName)
                    showRenameDialog = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageItem,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val isUser = message.role == "user"
    val segments = remember(message.content) { parseCodeSegments(message.content) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        LumiqGlassCard(
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.96f)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "LUMIQ (${message.model})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isUser && message.latencyMs > 0L) {
                        Text("${message.latencyMs}ms", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                segments.forEach { (isCode, chunk) ->
                    if (isCode) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0B1424),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = chunk,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = Color(0xFF7BE8FF),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(chunk, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (!isUser) {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun parseCodeSegments(raw: String): List<Pair<Boolean, String>> {
    val parts = raw.split("```")
    return parts.mapIndexedNotNull { index, s ->
        val trimmed = s.trim()
        if (trimmed.isEmpty()) null else (index % 2 == 1) to trimmed
    }
}

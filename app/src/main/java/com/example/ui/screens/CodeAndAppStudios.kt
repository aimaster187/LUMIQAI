package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.CodeFileItem
import com.example.data.model.StudioProject
import com.example.device.PhoneControlManager
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LumiqGlassCard
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeStudioScreen(
    files: List<CodeFileItem>,
    onUpdateFile: (String, String) -> Unit,
    onCreateFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onAiAssist: suspend (String, String, String) -> Result<String>
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedPath by remember(files.size) { mutableStateOf(files.firstOrNull()?.path ?: "main.py") }
    val activeFile = files.firstOrNull { it.path == selectedPath } ?: files.firstOrNull()

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var termuxCmd by remember { mutableStateOf("python3 -c \"print('LUMIQ LABS Termux Ready')\"") }
    var consoleOutput by remember { mutableStateOf("LUMIQ Code & Termux Console ready.") }

    val termuxPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val res = PhoneControlManager.runTermuxCommand(context, termuxCmd)
            consoleOutput = res.message
        } else {
            consoleOutput = "Termux RUN_COMMAND permission denied. Allow permission in App Info or Termux settings."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .padding(bottom = 92.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // File Tabs
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            files.forEach { file ->
                FilterChip(
                    selected = file.path == activeFile?.path,
                    onClick = { selectedPath = file.path },
                    label = { Text(file.path) },
                    trailingIcon = if (files.size > 1) {
                        {
                            IconButton(onClick = { onDeleteFile(file.path) }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete file", modifier = Modifier.size(12.dp))
                            }
                        }
                    } else null
                )
            }
            IconButton(onClick = { showNewFileDialog = true }, modifier = Modifier.testTag("code_new_file_btn")) {
                Icon(Icons.Rounded.Add, contentDescription = "Create file")
            }
        }

        // Editor Card
        activeFile?.let { file ->
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${file.path} (${file.language})", style = MaterialTheme.typography.titleMedium)
                        Row {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(file.content)) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy code")
                            }
                            IconButton(onClick = {
                                val zipMap = files.associate { it.path to it.content }
                                PhoneControlManager.exportFilesAsZip(context, "lumiq-code-studio", zipMap)
                                    .onSuccess { PhoneControlManager.shareFile(context, it) }
                            }) {
                                Icon(Icons.Rounded.FolderZip, contentDescription = "Export ZIP")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = file.content,
                        onValueChange = { onUpdateFile(file.path, it) },
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.5.sp),
                        minLines = 9,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth().testTag("code_editor_field")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        label = { Text("Ask AI to generate, debug, or explain code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    consoleOutput = "Running AI Code Assist..."
                                    val res = onAiAssist(aiPrompt.ifBlank { "Improve and document this code" }, file.content, file.language)
                                    consoleOutput = res.getOrElse { "Error: ${it.localizedMessage}" }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Generate / Debug")
                        }
                        if (file.language == "HTML") {
                            OutlinedButton(onClick = { showPreview = !showPreview }) {
                                Text(if (showPreview) "Hide Preview" else "Preview")
                            }
                        }
                    }
                }
            }

            if (showPreview && file.language == "HTML") {
                LumiqGlassCard(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                loadDataWithBaseURL(null, file.content, "text/html", "UTF-8", null)
                            }
                        },
                        update = { it.loadDataWithBaseURL(null, file.content, "text/html", "UTF-8", null) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Termux Coding Bridge Card
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Termux Coding Terminal", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                val termuxInstalled = PhoneControlManager.isPackageInstalled(context, "com.termux")
                Text(
                    if (termuxInstalled) "Termux detected (com.termux). Commands dispatch via RUN_COMMAND."
                    else "Termux not detected on this device. Install Termux or copy script to run in Termux.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (termuxInstalled) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = termuxCmd,
                    onValueChange = { termuxCmd = it },
                    label = { Text("Shell command") },
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!PhoneControlManager.isPackageInstalled(context, "com.termux")) {
                                consoleOutput = PhoneControlManager.runTermuxCommand(context, termuxCmd).message
                            } else if (!PhoneControlManager.hasTermuxPermission(context)) {
                                termuxPermLauncher.launch(PhoneControlManager.TERMUX_RUN_COMMAND_PERMISSION)
                            } else {
                                val res = PhoneControlManager.runTermuxCommand(context, termuxCmd)
                                consoleOutput = res.message
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("run_termux_command_btn")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run in Termux")
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(termuxCmd))
                        consoleOutput = "Copied Termux command to clipboard."
                    }) {
                        Text("Copy Cmd")
                    }
                }
            }
        }

        // Console Output
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0A1424),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = consoleOutput,
                fontFamily = JetBrainsMonoFontFamily,
                color = Color(0xFF7CE8FF),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    if (showNewFileDialog) {
        var newName by remember { mutableStateOf("script.kt") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create File") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Filename (e.g. app.py, main.kt)") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    onCreateFile(newName)
                    selectedPath = newName
                    showNewFileDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AppStudioScreen(
    files: Map<String, String>,
    logs: List<String>,
    projects: List<StudioProject>,
    onGenerateApp: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var appName by remember { mutableStateOf("Nova Shopping 3D") }
    var packageName by remember { mutableStateOf("com.lumiqlabs.novashop") }
    var requirements by remember { mutableStateOf("Premium 3D pearl-white shopping app with product catalog, cart, and order summary") }
    var selectedFile by remember(files) { mutableStateOf(files.keys.firstOrNull().orEmpty()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 104.dp, top = 6.dp)
    ) {
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Android, contentDescription = null, tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LUMIQ Android App Studio", style = MaterialTheme.typography.titleLarge)
                    }
                    OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = packageName, onValueChange = { packageName = it }, label = { Text("Package Name (e.g. com.company.app)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = requirements, onValueChange = { requirements = it }, label = { Text("App Requirements") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onGenerateApp(appName, packageName, requirements) },
                            modifier = Modifier.weight(1f).testTag("app_studio_generate_btn")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate Android Project")
                        }
                        OutlinedButton(onClick = {
                            PhoneControlManager.exportFilesAsZip(context, appName.lowercase().replace(" ", "-"), files)
                                .onSuccess { PhoneControlManager.shareFile(context, it) }
                        }) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export ZIP")
                        }
                    }
                }
            }
        }

        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Generated Project Files", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        files.keys.forEach { path ->
                            FilterChip(
                                selected = path == selectedFile,
                                onClick = { selectedFile = path },
                                label = { Text(path.substringAfterLast("/")) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0A1424), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = files[selectedFile].orEmpty(),
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color(0xFF8BE9FD),
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("APK Build & Device Install Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "On-device Android phones cannot run Gradle/AAPT2 natively inside the app sandbox without a cloud CI runner. Export the generated project ZIP and trigger the included GitHub Actions workflow (.github/workflows/android-apk.yml) to compile, sign, and download LUMIQ-AI-APK, then install via Android Package Installer (enable 'Install unknown apps').",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    logs.takeLast(5).forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                    }
                }
            }
        }

        items(projects.filter { it.projectType == "android_app" }, key = { it.id }) { proj ->
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("${proj.name} (${proj.packageName})", style = MaterialTheme.typography.titleMedium)
                    Text("Status: ${proj.buildStatus} • Version ${proj.version}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                }
            }
        }
    }
}

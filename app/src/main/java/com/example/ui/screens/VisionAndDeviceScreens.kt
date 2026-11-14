package com.example.ui.screens

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.device.PhoneControlManager
import com.example.service.LumiqAccessibilityService
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LumiqGlassCard
import com.example.ui.viewmodel.ChatStreamState
import com.example.ui.viewmodel.WhatsAppDraftItem
import com.example.ui.viewmodel.WhatsAppStage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VisionDocsScreen(
    chatStreamState: ChatStreamState,
    onAnalyzeWithAi: (prompt: String, bitmap: Bitmap?, docText: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showLiveCamera by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var detectedCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var docName by remember { mutableStateOf("") }
    var questionPrompt by remember { mutableStateOf("Summarize this image/document and highlight key details.") }
    var localStatus by remember { mutableStateOf("Ready: Capture photo, pick from gallery, or open PDF/TXT/MD.") }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
        if (granted) {
            showLiveCamera = true
            localStatus = "CameraX live preview active."
        } else {
            localStatus = "Camera permission denied. You can still use Photo Picker or Document Picker."
        }
    }

    suspend fun runOnDeviceMlKit(bitmap: Bitmap) {
        try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val ocrClient = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val qrClient = BarcodeScanning.getClient()
            val ocrResult = ocrClient.process(input).await()
            val barcodes = qrClient.process(input).await()
            extractedText = ocrResult.text
            detectedCodes = barcodes.mapNotNull { it.rawValue }
            localStatus = "On-device ML Kit finished: ${extractedText.length} chars OCR, ${detectedCodes.size} QR/barcodes."
        } catch (e: Exception) {
            localStatus = "On-device ML Kit notice: ${e.localizedMessage}"
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                if (bmp != null) {
                    capturedBitmap = bmp
                    docName = "Selected Image"
                    showLiveCamera = false
                    runOnDeviceMlKit(bmp)
                }
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val mime = context.contentResolver.getType(uri).orEmpty()
                docName = uri.lastPathSegment ?: "Document"
                showLiveCamera = false
                if (mime == "application/pdf" || docName.endsWith(".pdf", ignoreCase = true)) {
                    val pdfBitmap = withContext(Dispatchers.IO) {
                        renderFirstPdfPage(context, uri)
                    }
                    if (pdfBitmap != null) {
                        capturedBitmap = pdfBitmap
                        runOnDeviceMlKit(pdfBitmap)
                        localStatus = "PDF rendered (Page 1) & scanned on-device via ML Kit OCR."
                    } else {
                        localStatus = "Could not render PDF preview."
                    }
                } else {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    }
                    extractedText = text.take(15000)
                    capturedBitmap = null
                    localStatus = "Loaded text document (${extractedText.length} chars)."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Vision, QR & Document AI", style = MaterialTheme.typography.titleLarge)
                Text(
                    "CameraX live capture, Android Photo Picker (zero storage permission), SAF PDF/Text reader, and on-device ML Kit OCR + QR scanning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (hasCameraPerm) {
                                showLiveCamera = !showLiveCamera
                            } else {
                                cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("vision_camera_toggle")
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showLiveCamera) "Close Cam" else "CameraX")
                    }
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("vision_photo_picker")
                    ) {
                        Icon(Icons.Rounded.PermMedia, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo")
                    }
                    OutlinedButton(
                        onClick = {
                            docPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "*/*"))
                        },
                        modifier = Modifier.weight(1f).testTag("vision_doc_picker")
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Doc")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(localStatus, style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
            }
        }

        if (showLiveCamera && hasCameraPerm) {
            Spacer(modifier = Modifier.height(12.dp))
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val previewView = remember { PreviewView(context) }
                    DisposableEffect(lifecycleOwner) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val executor = ContextCompat.getMainExecutor(context)
                        cameraProviderFuture.addListener({
                            runCatching {
                                val provider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val imgCap = ImageCapture.Builder().build()
                                imageCaptureRef = imgCap
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imgCap
                                )
                            }
                        }, executor)
                        onDispose {
                            runCatching {
                                ProcessCameraProvider.getInstance(context).get().unbindAll()
                            }
                        }
                    }

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val imgCap = imageCaptureRef ?: return@Button
                            val outFile = File(context.cacheDir, "lumiq_capture_${System.currentTimeMillis()}.jpg")
                            val options = ImageCapture.OutputFileOptions.Builder(outFile).build()
                            imgCap.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        val bmp = BitmapFactory.decodeFile(outFile.absolutePath)
                                        if (bmp != null) {
                                            capturedBitmap = bmp
                                            docName = "CameraX Capture"
                                            showLiveCamera = false
                                            scope.launch { runOnDeviceMlKit(bmp) }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        localStatus = "Capture failed: ${exception.localizedMessage}"
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("vision_capture_button")
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capture & Run On-Device OCR + QR")
                    }
                }
            }
        }

        if (capturedBitmap != null) {
            Spacer(modifier = Modifier.height(12.dp))
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(docName.ifBlank { "Captured Preview" }, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured preview image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        if (detectedCodes.isNotEmpty() || extractedText.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (detectedCodes.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detected QR / Barcodes", style = MaterialTheme.typography.titleMedium)
                        }
                        detectedCodes.forEach { code ->
                            Text("• $code", style = MaterialTheme.typography.bodyMedium, color = ElectricBlue)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (extractedText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TextFields, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extracted Text (On-Device)", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            extractedText.take(1500),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Cloud Gemini Multimodal Q&A", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Notice: Clicking Analyze sends the preview/extracted text to Firebase AI Logic (Gemini) for deep reasoning.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = questionPrompt,
                    onValueChange = { questionPrompt = it },
                    label = { Text("Ask LUMIQ about this image or document") },
                    modifier = Modifier.fillMaxWidth().testTag("vision_prompt_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onAnalyzeWithAi(questionPrompt, capturedBitmap, extractedText) },
                    enabled = capturedBitmap != null || extractedText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("vision_ai_analyze_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze with LUMIQ Multimodal AI")
                }

                when (chatStreamState) {
                    is ChatStreamState.Thinking -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Analyzing content...", color = ElectricBlue)
                    }
                    is ChatStreamState.Streaming -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(chatStreamState.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    is ChatStreamState.Error -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(chatStreamState.message, color = MaterialTheme.colorScheme.error)
                    }
                    ChatStreamState.Idle -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

private fun renderFirstPdfPage(context: Context, uri: Uri): Bitmap? {
    return try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(
                        (page.width * 2).coerceIn(400, 1600),
                        (page.height * 2).coerceIn(400, 2200),
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PhoneControlScreen(
    whatsappDrafts: List<WhatsAppDraftItem>,
    onDraftWhatsApp: (phone: String, message: String, useAiPolish: Boolean) -> Unit,
    onUpdateWhatsAppStage: (draftId: String, stage: WhatsAppStage) -> Unit
) {
    val context = LocalContext.current
    val accessibilityConnected by LumiqAccessibilityService.isConnected.collectAsStateWithLifecycle()
    var isDefaultAssistant by remember { mutableStateOf(PhoneControlManager.isDefaultAssistant(context)) }
    var actionFeedback by remember { mutableStateOf("Ready for explicit user-confirmed device actions.") }
    var appLaunchQuery by remember { mutableStateOf("YouTube") }

    // WhatsApp state
    var waPhone by remember { mutableStateOf("") }
    var waMessage by remember { mutableStateOf("") }
    var waAiPolish by remember { mutableStateOf(true) }

    val assistantRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultAssistant = PhoneControlManager.isDefaultAssistant(context)
        actionFeedback = if (isDefaultAssistant) "LUMIQ AI is now set as Default Assistant!" else "Default Assistant role unchanged."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Status & Accessibility + Default Assistant
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Device Control & Accessibility", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Explicit user-triggered system actions only. Zero silent background clicks or hidden harvesting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (accessibilityConnected) EmeraldSuccess.copy(alpha = 0.14f) else AmberWarning.copy(alpha = 0.14f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Accessibility", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (accessibilityConnected) "Connected" else "Not Enabled",
                                color = if (accessibilityConnected) EmeraldSuccess else AmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDefaultAssistant) EmeraldSuccess.copy(alpha = 0.14f) else ElectricBlue.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Default Assistant", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (isDefaultAssistant) "Active" else "Not Default",
                                color = if (isDefaultAssistant) EmeraldSuccess else ElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { PhoneControlManager.openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                        modifier = Modifier.weight(1f).testTag("open_accessibility_settings")
                    ) {
                        Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Accessibility")
                    }
                    Button(
                        onClick = { assistantRoleLauncher.launch(PhoneControlManager.createDefaultAssistantIntent(context)) },
                        modifier = Modifier.weight(1f).testTag("set_default_assistant_button")
                    ) {
                        Icon(Icons.Rounded.Assistant, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set Assistant")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(actionFeedback, style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hardware & System Controls
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Quick Hardware & Navigation", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { actionFeedback = PhoneControlManager.toggleFlashlight(context).message },
                        modifier = Modifier.weight(1f).testTag("ctrl_flashlight")
                    ) {
                        Icon(Icons.Rounded.FlashlightOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Torch")
                    }
                    OutlinedButton(
                        onClick = { actionFeedback = PhoneControlManager.toggleMediaPlayPause(context).message },
                        modifier = Modifier.weight(1f).testTag("ctrl_media")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play/Pause")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { actionFeedback = PhoneControlManager.adjustVolume(context, true).message },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vol +")
                    }
                    OutlinedButton(
                        onClick = { actionFeedback = PhoneControlManager.adjustVolume(context, false).message },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.VolumeDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vol -")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { actionFeedback = PhoneControlManager.setQuickAlarm(context, 7, 30, "LUMIQ Morning").message },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Alarm 07:30")
                    }
                    OutlinedButton(
                        onClick = { actionFeedback = PhoneControlManager.setQuickTimer(context, 300, "LUMIQ Focus").message },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("5m Timer")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(
                        onClick = {
                            actionFeedback = PhoneControlManager.performAccessibilityNavigation(
                                AccessibilityService.GLOBAL_ACTION_HOME, "Home"
                            ).message
                        },
                        label = { Text("Home") },
                        leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            actionFeedback = PhoneControlManager.performAccessibilityNavigation(
                                AccessibilityService.GLOBAL_ACTION_BACK, "Back"
                            ).message
                        },
                        label = { Text("Back") }
                    )
                    AssistChip(
                        onClick = {
                            actionFeedback = PhoneControlManager.performAccessibilityNavigation(
                                AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents"
                            ).message
                        },
                        label = { Text("Recents") }
                    )
                    AssistChip(
                        onClick = {
                            actionFeedback = PhoneControlManager.performAccessibilityNavigation(
                                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Quick Settings"
                            ).message
                        },
                        label = { Text("Panel") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = appLaunchQuery,
                        onValueChange = { appLaunchQuery = it },
                        label = { Text("Launch App by Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        actionFeedback = PhoneControlManager.openAppByName(context, appLaunchQuery).message
                    }) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // WhatsApp Assistant
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("WhatsApp Assistant (Explicit Confirmation)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Drafts and optionally polishes messages with AI. Never auto-sends without your confirmation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = waPhone,
                    onValueChange = { waPhone = it },
                    label = { Text("Phone with Country Code (optional, e.g. 9198XXXXXXXX)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wa_phone_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = waMessage,
                    onValueChange = { waMessage = it },
                    label = { Text("Message or Intent") },
                    modifier = Modifier.fillMaxWidth().testTag("wa_message_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = waAiPolish, onCheckedChange = { waAiPolish = it })
                    Text("Polish draft with Gemini Flash-Lite")
                }
                Button(
                    onClick = {
                        if (waMessage.isNotBlank()) {
                            onDraftWhatsApp(waPhone, waMessage, waAiPolish)
                            waMessage = ""
                        }
                    },
                    enabled = waMessage.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("wa_draft_button")
                ) {
                    Text("Create WhatsApp Draft")
                }

                whatsappDrafts.forEach { draft ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (draft.phone.isBlank()) "Share Target" else "+${draft.phone.filter(Char::isDigit)}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = draft.stage.label,
                                    color = when (draft.stage) {
                                        WhatsAppStage.SENT_CONFIRMED -> EmeraldSuccess
                                        WhatsAppStage.OPENED -> ElectricBlue
                                        WhatsAppStage.DRAFTED -> AmberWarning
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(draft.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val res = PhoneControlManager.openWhatsAppDraft(context, draft.phone, draft.message)
                                        actionFeedback = res.message
                                        if (res.executed) onUpdateWhatsAppStage(draft.id, WhatsAppStage.OPENED)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open WhatsApp")
                                }
                                OutlinedButton(
                                    onClick = { onUpdateWhatsAppStage(draft.id, WhatsAppStage.SENT_CONFIRMED) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sent Confirmed")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

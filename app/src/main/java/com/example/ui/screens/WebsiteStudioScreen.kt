package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.StudioProject
import com.example.device.PhoneControlManager
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LumiqGlassCard

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebsiteStudioScreen(
    files: Map<String, String>,
    logs: List<String>,
    projects: List<StudioProject>,
    onGenerateWebsite: (String, String) -> Unit,
    onDeployNetlify: (String, String, String?) -> Unit
) {
    val context = LocalContext.current
    var siteName by remember { mutableStateOf("Lumiq Commerce") }
    var requirements by remember { mutableStateOf("Modern E-commerce website with product showcase, cart counter, and mobile responsive layout") }
    var mobilePreview by remember { mutableStateOf(true) }
    var showDeployDialog by remember { mutableStateOf(false) }

    val htmlContent = files["index.html"].orEmpty()
    val websiteProjects = projects.filter { it.projectType == "website" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 104.dp, top = 6.dp)
    ) {
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LUMIQ Website Studio", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = siteName, onValueChange = { siteName = it }, label = { Text("Website Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = requirements, onValueChange = { requirements = it }, label = { Text("Describe your website") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onGenerateWebsite(siteName, requirements) },
                            modifier = Modifier.weight(1f).testTag("website_generate_btn")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Build Website")
                        }
                        OutlinedButton(onClick = {
                            PhoneControlManager.exportFilesAsZip(context, siteName.lowercase().replace(" ", "-"), files)
                                .onSuccess { PhoneControlManager.shareFile(context, it) }
                        }) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ZIP")
                        }
                    }
                    Button(
                        onClick = { showDeployDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("website_deploy_btn")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish Live to Free Hosting (Netlify / GitHub / Firebase)")
                    }
                }
            }
        }

        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Preview", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = mobilePreview,
                                onClick = { mobilePreview = true },
                                label = { Text("Mobile") },
                                leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                            FilterChip(
                                selected = !mobilePreview,
                                onClick = { mobilePreview = false },
                                label = { Text("Desktop") },
                                leadingIcon = { Icon(Icons.Rounded.DesktopWindows, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(if (mobilePreview) 0.88f else 1f)
                            .height(270.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.useWideViewPort = !mobilePreview
                                    settings.loadWithOverviewMode = !mobilePreview
                                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                }
                            },
                            update = {
                                it.settings.useWideViewPort = !mobilePreview
                                it.settings.loadWithOverviewMode = !mobilePreview
                                it.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Free Hosting & Build Logs", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Supported Free Subdomains: Netlify Free Tier, GitHub Pages, Cloudflare Pages, Firebase Hosting Spark Quota. Custom domains require separate domain registration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0A1424), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = logs.takeLast(6).joinToString("\n"),
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color(0xFF7BE8FF),
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        items(websiteProjects, key = { it.id }) { proj ->
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(proj.name, style = MaterialTheme.typography.titleMedium)
                    Text("Deploy: ${proj.deploymentStatus} • ${proj.publicUrl.ifBlank { "Preview Only" }}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                }
            }
        }
    }

    if (showDeployDialog) {
        var netlifyToken by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeployDialog = false },
            title = { Text("Confirm Public Deployment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Publishing makes your website publicly accessible on a free Netlify subdomain. Enter your Netlify Personal Access Token (kept in memory for this deploy only) or export ZIP for GitHub Pages / Firebase Hosting.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = netlifyToken,
                        onValueChange = { netlifyToken = it },
                        label = { Text("Netlify Personal Access Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val latestProjId = websiteProjects.firstOrNull()?.id
                    onDeployNetlify(siteName, netlifyToken, latestProjId)
                    showDeployDialog = false
                }) { Text("Approve & Publish Live") }
            },
            dismissButton = {
                TextButton(onClick = { showDeployDialog = false }) { Text("Cancel") }
            }
        )
    }
}

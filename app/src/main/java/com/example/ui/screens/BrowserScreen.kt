package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LumiqGlassCard
import kotlinx.coroutines.launch

data class BrowserTab(
    val id: Int,
    val title: String,
    val url: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    searchEngine: String,
    bookmarks: List<Pair<String, String>>,
    onSetSearchEngine: (String) -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onSummarizePageWithAi: suspend (String, String) -> Result<String>
) {
    val scope = rememberCoroutineScope()
    val tabs = remember { mutableStateListOf(BrowserTab(1, "LUMIQ Search", "https://duckduckgo.com")) }
    var activeTabIndex by remember { mutableIntStateOf(0) }
    val currentTab = tabs.getOrElse(activeTabIndex) { tabs.first() }

    var addressInput by remember(currentTab.id) { mutableStateOf(currentTab.url) }
    var pageProgress by remember { mutableIntStateOf(100) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var aiSummaryResult by remember { mutableStateOf<String?>(null) }
    var showEngineMenu by remember { mutableStateOf(false) }

    fun resolveInputUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> {
                val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                when (searchEngine) {
                    "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
                    "Bing" -> "https://www.bing.com/search?q=$encoded"
                    else -> "https://www.google.com/search?q=$encoded"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 88.dp)
    ) {
        // Tabs Strip
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { idx, tab ->
                FilterChip(
                    selected = idx == activeTabIndex,
                    onClick = {
                        activeTabIndex = idx
                        addressInput = tab.url
                        webViewRef?.loadUrl(tab.url)
                    },
                    label = { Text(tab.title.take(16)) },
                    trailingIcon = if (tabs.size > 1) {
                        {
                            IconButton(onClick = {
                                tabs.removeAt(idx)
                                activeTabIndex = activeTabIndex.coerceAtMost(tabs.lastIndex)
                            }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close tab", modifier = Modifier.size(12.dp))
                            }
                        }
                    } else null
                )
            }
            IconButton(onClick = {
                val nextId = (tabs.maxOfOrNull { it.id } ?: 0) + 1
                tabs.add(BrowserTab(nextId, "New Tab", "https://duckduckgo.com"))
                activeTabIndex = tabs.lastIndex
                addressInput = "https://duckduckgo.com"
                webViewRef?.loadUrl("https://duckduckgo.com")
            }) {
                Icon(Icons.Rounded.Add, contentDescription = "New Browser Tab")
            }
        }

        // Address Bar + HTTPS + Controls
        LumiqGlassCard(
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "HTTPS status",
                        tint = if (currentTab.url.startsWith("https://")) EmeraldSuccess else CrimsonError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("browser_address_bar")
                    )
                    IconButton(onClick = {
                        val target = resolveInputUrl(addressInput)
                        tabs[activeTabIndex] = currentTab.copy(url = target)
                        webViewRef?.loadUrl(target)
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Go / Reload", tint = ElectricBlue)
                    }
                }
                if (pageProgress in 1..99) {
                    LinearProgressIndicator(progress = { pageProgress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { webViewRef?.goBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { webViewRef?.goForward() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = { onAddBookmark(currentTab.title, currentTab.url) }) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Bookmark page")
                    }
                    IconButton(onClick = {
                        webViewRef?.clearCache(true)
                        webViewRef?.clearHistory()
                        CookieManager.getInstance().removeAllCookies(null)
                    }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear browsing data")
                    }
                    FilterChip(
                        selected = true,
                        onClick = { showEngineMenu = true },
                        label = { Text(searchEngine) }
                    )
                    DropdownMenu(expanded = showEngineMenu, onDismissRequest = { showEngineMenu = false }) {
                        listOf("Google", "DuckDuckGo", "Bing").forEach { eng ->
                            DropdownMenuItem(text = { Text(eng) }, onClick = {
                                onSetSearchEngine(eng); showEngineMenu = false
                            })
                        }
                    }
                    Button(
                        onClick = {
                            webViewRef?.evaluateJavascript(
                                "(function(){ return document.body ? document.body.innerText.slice(0, 6000) : ''; })();"
                            ) { extracted ->
                                scope.launch {
                                    val clean = extracted.orEmpty().trim('"').replace("\\n", "\n")
                                    val res = onSummarizePageWithAi(currentTab.url, clean)
                                    aiSummaryResult = res.getOrElse { "Summary failed: ${it.localizedMessage}" }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("browser_ai_summary_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Summary")
                    }
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bookmarks.take(8).forEach { (title, url) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            addressInput = url
                            tabs[activeTabIndex] = currentTab.copy(url = url, title = title)
                            webViewRef?.loadUrl(url)
                        },
                        label = { Text(title.take(18)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Hardened Android WebView (no private file/content access, no JS bridge to app secrets)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            pageProgress = newProgress
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val scheme = request?.url?.scheme?.lowercase()
                            return scheme != "https" && scheme != "http"
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) {
                                addressInput = url
                                tabs[activeTabIndex] = tabs[activeTabIndex].copy(
                                    url = url,
                                    title = view?.title?.ifBlank { url } ?: url
                                )
                            }
                        }
                    }
                    loadUrl(currentTab.url)
                    webViewRef = this
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }

    aiSummaryResult?.let { summary ->
        AlertDialog(
            onDismissRequest = { aiSummaryResult = null },
            title = { Text("LUMIQ AI Page Summary") },
            text = { Text(summary) },
            confirmButton = {
                TextButton(onClick = { aiSummaryResult = null }) { Text("Close") }
            }
        )
    }
}

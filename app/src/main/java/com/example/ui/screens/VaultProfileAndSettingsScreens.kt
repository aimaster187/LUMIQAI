package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiProviderType
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.CustomProviderConfig
import com.example.data.model.FollowRelation
import com.example.data.model.PublicProfile
import com.example.data.model.UserMemoryItem
import com.example.data.model.UserProfile
import com.example.data.model.VaultEntry
import com.example.device.PhoneControlManager
import com.example.security.KeystoreCryptoManager
import com.example.service.LumiqAccessibilityService
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LumiqGlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun VaultScreen(
    appLockEnabled: Boolean,
    biometricEnabled: Boolean,
    vaultEntries: List<VaultEntry>,
    onSetAppLockPin: (pin: String, biometric: Boolean) -> Unit,
    onDisableAppLock: () -> Unit,
    onLockNow: () -> Unit,
    onAddVaultEntry: (title: String, username: String, secret: String, category: String) -> Unit,
    onDeleteVaultEntry: (id: String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newPin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(biometricEnabled) }
    var entryTitle by remember { mutableStateOf("") }
    var entryUser by remember { mutableStateOf("") }
    var entrySecret by remember { mutableStateOf("") }
    var entryCategory by remember { mutableStateOf("Password") }
    val revealedMap = remember { mutableStateMapOf<String, Boolean>() }

    fun copyWithAutoClear(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        onShowMessage("Copied '$label'. Clipboard auto-clears in 30s.")
        scope.launch {
            delay(30_000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { cm.clearPrimaryClip() }
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // App Lock Config
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("LUMIQ App Lock", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (appLockEnabled) "Active (Salted PBKDF2-SHA256 + 5-attempt lockout)" else "Disabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (appLockEnabled) EmeraldSuccess else AmberWarning
                        )
                    }
                    if (appLockEnabled) {
                        OutlinedButton(onClick = onLockNow) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock Now")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("Set / Update PIN (4–8 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vault_pin_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useBiometric, onCheckedChange = { useBiometric = it })
                    Text("Allow Biometric / Fingerprint unlock")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            onSetAppLockPin(newPin, useBiometric)
                            newPin = ""
                        },
                        enabled = newPin.length >= 4,
                        modifier = Modifier.weight(1f).testTag("vault_save_pin_button")
                    ) {
                        Text(if (appLockEnabled) "Update PIN" else "Enable App Lock")
                    }
                    if (appLockEnabled) {
                        OutlinedButton(onClick = onDisableAppLock, modifier = Modifier.weight(1f)) {
                            Text("Disable Lock")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add Vault Secret
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Encrypted Password & Notes Vault", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Secrets are encrypted on-device with Android Keystore AES-256-GCM before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Password", "API Key", "Secure Note").forEach { cat ->
                        FilterChip(
                            selected = entryCategory == cat,
                            onClick = { entryCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = entryTitle,
                    onValueChange = { entryTitle = it },
                    label = { Text("Title (e.g. GitHub, Server SSH)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vault_title_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = entryUser,
                    onValueChange = { entryUser = it },
                    label = { Text("Username / Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = entrySecret,
                    onValueChange = { entrySecret = it },
                    label = { Text("Secret / Password / Note") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("vault_secret_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            onAddVaultEntry(entryTitle, entryUser, entrySecret, entryCategory)
                            entryTitle = ""
                            entryUser = ""
                            entrySecret = ""
                        },
                        enabled = entryTitle.isNotBlank() && entrySecret.isNotBlank(),
                        modifier = Modifier.weight(1f).testTag("vault_add_entry_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Encrypt & Save")
                    }
                    OutlinedButton(
                        onClick = {
                            val arr = JSONArray()
                            vaultEntries.forEach { e ->
                                arr.put(JSONObject().apply {
                                    put("title", e.title)
                                    put("category", e.category)
                                    put("usernameOrLabel", e.usernameOrLabel)
                                    put("encryptedSecret", e.encryptedSecret)
                                })
                            }
                            PhoneControlManager.exportFilesAsZip(
                                context,
                                "lumiq_encrypted_vault_backup",
                                mapOf("lumiq_vault_encrypted.json" to arr.toString(2))
                            ).onSuccess { zip ->
                                PhoneControlManager.shareFile(context, zip)
                            }.onFailure {
                                onShowMessage("Export failed: ${it.localizedMessage}")
                            }
                        },
                        enabled = vaultEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup ZIP")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        vaultEntries.forEach { entry ->
            val revealed = revealedMap[entry.id] == true
            val plainSecret = remember(entry.encryptedSecret, revealed) {
                if (revealed) KeystoreCryptoManager.decrypt(entry.encryptedSecret) else "••••••••••••"
            }
            LumiqGlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text("${entry.category} • ${entry.usernameOrLabel}", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                        }
                        Row {
                            IconButton(onClick = { revealedMap[entry.id] = !revealed }) {
                                Icon(
                                    if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle secret visibility"
                                )
                            }
                            IconButton(onClick = {
                                val decrypted = KeystoreCryptoManager.decrypt(entry.encryptedSecret)
                                copyWithAutoClear(entry.title, decrypted)
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy secret")
                            }
                            IconButton(onClick = { onDeleteVaultEntry(entry.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete entry", tint = CrimsonError)
                            }
                        }
                    }
                    Text(plainSecret, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
fun ProfileMemoryScreen(
    userProfile: UserProfile?,
    memories: List<UserMemoryItem>,
    discoveredProfiles: List<PublicProfile>,
    following: List<FollowRelation>,
    onUpdateProfile: (username: String, bio: String, isPublic: Boolean) -> Unit,
    onSearchCommunity: (String) -> Unit,
    onFollowProfile: (PublicProfile) -> Unit,
    onAddMemory: (category: String, title: String, content: String, isPinned: Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var username by remember(userProfile?.username) { mutableStateOf(userProfile?.username ?: "") }
    var bio by remember(userProfile?.bio) { mutableStateOf(userProfile?.bio ?: "") }
    var isPublic by remember(userProfile?.isPublic) { mutableStateOf(userProfile?.isPublic ?: true) }

    var searchQuery by remember { mutableStateOf("") }
    var memTitle by remember { mutableStateOf("") }
    var memContent by remember { mutableStateOf("") }
    var memCategory by remember { mutableStateOf("Preference") }
    var memPinned by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Profile & Immutable Public ID
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Profile & Permanent Public ID", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ElectricBlue.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Immutable Public ID", style = MaterialTheme.typography.labelSmall)
                            Text(
                                userProfile?.publicId ?: "LUM-SYNCING",
                                style = MaterialTheme.typography.titleLarge,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = {
                            val id = userProfile?.publicId ?: return@IconButton
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("LUMIQ Public ID", id))
                            onShowMessage("Copied Public ID: $id")
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Public ID", tint = ElectricBlue)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Display Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Public Profile Discoverable")
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                Button(
                    onClick = { onUpdateProfile(username, bio, isPublic) },
                    enabled = username.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                ) {
                    Text("Save Profile")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Community Search & Follow
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Community Discovery (${following.size} Following)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search username or LUM-ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onSearchCommunity(searchQuery) }) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                }
                discoveredProfiles.forEach { profile ->
                    val alreadyFollowing = following.any { it.targetUid == profile.userId }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.username, fontWeight = FontWeight.Bold)
                            Text("${profile.publicId} • ${profile.bio}", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onFollowProfile(profile) },
                            enabled = !alreadyFollowing
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (alreadyFollowing) "Following" else "Follow")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // User-Controlled AI Memory
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("User-Controlled AI Memory (${memories.size})", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Only items you explicitly save here are used to personalize LUMIQ replies.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Preference", "Project", "Fact").forEach { cat ->
                        FilterChip(selected = memCategory == cat, onClick = { memCategory = cat }, label = { Text(cat) })
                    }
                }
                OutlinedTextField(
                    value = memTitle,
                    onValueChange = { memTitle = it },
                    label = { Text("Memory Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("memory_title_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = memContent,
                    onValueChange = { memContent = it },
                    label = { Text("Memory Fact / Context") },
                    modifier = Modifier.fillMaxWidth().testTag("memory_content_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = memPinned, onCheckedChange = { memPinned = it })
                    Text("Pin for priority context")
                }
                Button(
                    onClick = {
                        onAddMemory(memCategory, memTitle, memContent, memPinned)
                        memTitle = ""
                        memContent = ""
                    },
                    enabled = memTitle.isNotBlank() && memContent.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("save_memory_button")
                ) {
                    Text("Save to AI Memory")
                }

                memories.forEach { item ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text("[${item.category}] ${item.content}", style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { onDeleteMemory(item.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete memory", tint = CrimsonError)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
fun ProvidersScreen(
    activeProviderId: String,
    customProviders: List<CustomProviderConfig>,
    onSelectProvider: (String) -> Unit,
    onAddAndTestProvider: (AiProviderType, String, String, String, String) -> Unit,
    onRemoveCustomProvider: (String) -> Unit,
    builtInModelTestResults: Map<String, String> = emptyMap(),
    onTestBuiltInModel: (AiProviderType) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(AiProviderType.GROQ) }
    var label by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(AiProviderType.GROQ.defaultEndpoint) }
    var modelName by remember { mutableStateOf(AiProviderType.GROQ.defaultModel) }
    var rawApiKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Zero-Key Firebase AI Logic Engines", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Authenticated via Firebase App Check — zero client-side Gemini API key errors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                listOf(
                    AiProviderType.GEMINI_FAST_LITE,
                    AiProviderType.FIREBASE_GEMINI,
                    AiProviderType.GEMINI_ARGON
                ).forEach { prov ->
                    val active = activeProviderId == prov.name
                    val testNote = builtInModelTestResults[prov.name]
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) ElectricBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        onClick = { onSelectProvider(prov.name) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prov.displayName, fontWeight = FontWeight.Bold)
                                Text(
                                    if (prov == AiProviderType.GEMINI_ARGON) {
                                        "Model: ${prov.defaultModel} (Blaze billing required)"
                                    } else {
                                        "Model: ${prov.defaultModel} (Free Spark tier supported)"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (!testNote.isNullOrBlank()) {
                                    Text(
                                        testNote,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (testNote.startsWith("Verified")) EmeraldSuccess else AmberWarning
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { onTestBuiltInModel(prov) }) {
                                    Text("Test")
                                }
                                if (active) Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = ElectricBlue)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Add & Verify Custom AI Provider (Keystore AES)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AiProviderType.GROQ, AiProviderType.OPENAI_COMPAT, AiProviderType.CUSTOM_ENDPOINT).forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = {
                                selectedType = t
                                if (t.defaultEndpoint.isNotBlank()) endpoint = t.defaultEndpoint
                                modelName = t.defaultModel
                            },
                            label = { Text(t.displayName.substringBefore(" ")) }
                        )
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("HTTPS Endpoint URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = rawApiKey,
                    onValueChange = { rawApiKey = it },
                    label = { Text("API Key (Encrypted locally per user)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onAddAndTestProvider(selectedType, label, endpoint, modelName, rawApiKey)
                        rawApiKey = ""
                    },
                    enabled = endpoint.startsWith("https://") && rawApiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("test_save_provider_button")
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Encrypt, Test Connection & Save")
                }

                customProviders.forEach { cfg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (activeProviderId == cfg.id) ElectricBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        onClick = { onSelectProvider(cfg.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cfg.label, fontWeight = FontWeight.Bold)
                                Text(
                                    if (cfg.isConnectedVerified) "Verified • ${cfg.lastLatencyMs}ms • ${cfg.modelName}" else "Unverified • ${cfg.modelName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cfg.isConnectedVerified) EmeraldSuccess else AmberWarning
                                )
                            }
                            IconButton(onClick = { onRemoveCustomProvider(cfg.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove provider", tint = CrimsonError)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
fun PermissionCenterScreen() {
    val context = LocalContext.current
    val accessibilityConnected by LumiqAccessibilityService.isConnected.collectAsStateWithLifecycle()

    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    val isAssistant = PhoneControlManager.isDefaultAssistant(context)
    val batteryExempt = PhoneControlManager.isIgnoringBatteryOptimizations(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LUMIQ Permission & Privacy Center", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Every sensitive capability is explicit, transparent, and Google Play compliant. No broad storage access requested.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        PermissionStatusRow("Microphone (Voice & Wake Word)", micGranted) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        PermissionStatusRow("Camera (CameraX Vision & QR)", cameraGranted) {
            camLauncher.launch(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStatusRow("Notifications (Voice Service & Alerts)", notifGranted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PermissionStatusRow("Accessibility (User-Confirmed Actions)", accessibilityConnected) {
            PhoneControlManager.openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        PermissionStatusRow("Default Digital Assistant Role", isAssistant) {
            context.startActivity(PhoneControlManager.createDefaultAssistantIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        PermissionStatusRow("Battery Optimization Exemption", batteryExempt) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    LumiqGlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (granted) "Granted / Active" else "Not Active",
                    color = if (granted) EmeraldSuccess else AmberWarning,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            OutlinedButton(onClick = onAction) {
                Text(if (granted) "Manage" else "Allow")
            }
        }
    }
}

@Composable
fun VipUsageScreen(
    userProfile: UserProfile?,
    totalRequests: Long,
    lastLatencyMs: Long,
    announcements: List<Announcement>,
    appConfig: AppConfig?,
    onStartTrial: () -> Unit,
    onRedeemLicense: (String) -> Unit
) {
    var licenseInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VIP, Server Trial & Telemetry", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Current Tier: ${(userProfile?.vipStatus ?: "free").uppercase()}", style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
                val latencyDisplay = if (lastLatencyMs > 0L) "${lastLatencyMs}ms" else "-- ms"
                Text("Total AI Requests: $totalRequests • Last Latency: $latencyDisplay", style = MaterialTheme.typography.bodyMedium)
                Text("Active License: ${userProfile?.activeLicenseCode ?: "None"}", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartTrial,
                    enabled = userProfile?.vipStatus == "free",
                    modifier = Modifier.fillMaxWidth().testTag("start_trial_button")
                ) {
                    Text(if (userProfile?.vipStatus == "trial") "10-Min Trial Active" else "Start 10-Min Server-Verified Trial")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = licenseInput,
                    onValueChange = { licenseInput = it },
                    label = { Text("Redeem VIP / Lifetime License Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("license_key_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onRedeemLicense(licenseInput)
                        licenseInput = ""
                    },
                    enabled = licenseInput.trim().length >= 6,
                    modifier = Modifier.fillMaxWidth().testTag("redeem_license_button")
                ) {
                    Text("Redeem License")
                }
            }
        }

        if (appConfig?.maintenanceMode == true) {
            Spacer(modifier = Modifier.height(10.dp))
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Maintenance Notice: ${appConfig.maintenanceMessage}", color = AmberWarning)
            }
        }

        if (announcements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("LUMIQ LABS Announcements", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            announcements.forEach { ann ->
                LumiqGlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column {
                        Text(ann.title, fontWeight = FontWeight.Bold)
                        Text(ann.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
fun AboutScreen(
    isDarkTheme: Boolean,
    userProfile: UserProfile?,
    onToggleTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("LUMIQ AI • LUMIQ LABS", style = MaterialTheme.typography.titleLarge)
                Text("Founder & Lead Developer: Pranav Patel", style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Package: com.lumiqlabs.lumiqai\nPublic ID: ${userProfile?.publicId ?: "LUM-PENDING"}\nVersion: 1.0.0",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Obsidian Dark Theme")
                    Switch(checked = isDarkTheme, onCheckedChange = onToggleTheme, modifier = Modifier.testTag("theme_toggle_switch"))
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gen-lang-client-0099902352.web.app"))
                        runCatching { context.startActivity(urlIntent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Public Website Target (Firebase Hosting)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChromeSilverBorder
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LumiqCircleIconSurface
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SoftShadowBlue
import com.example.voice.VoiceEngineState

enum class LumiqRoute(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    CHAT("AI Chat", Icons.AutoMirrored.Rounded.Chat),
    AGENTS("5-Agent Hub", Icons.Rounded.Hub),
    VOICE_STUDIO("Voice Studio", Icons.Rounded.GraphicEq),
    BROWSER("Web Browser", Icons.Rounded.Language),
    CODE_STUDIO("Code & Termux", Icons.Rounded.Code),
    WEBSITE_STUDIO("Website Studio", Icons.Rounded.AutoAwesome),
    APP_STUDIO("Android App Studio", Icons.Rounded.Android),
    VISION_DOCS("Vision & Docs", Icons.Rounded.QrCodeScanner),
    PHONE_CONTROL("Phone & WhatsApp", Icons.Rounded.PhoneAndroid),
    VAULT("App Lock & Vault", Icons.Rounded.Lock),
    PROFILE_MEMORY("Profile & Memory", Icons.Rounded.Psychology),
    PROVIDERS("API Providers", Icons.Rounded.Key),
    PERMISSIONS("Permission Center", Icons.Rounded.Security),
    VIP_USAGE("VIP, Trial & Usage", Icons.Rounded.WorkspacePremium),
    ABOUT("About LUMIQ LABS", Icons.Rounded.Info)
}

@Composable
fun LumiqTopBar(
    title: String,
    unreadNotifications: Int,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LumiqCircleIconSurface(
                modifier = Modifier.testTag("hamburger_menu_button"),
                onClick = onMenuClick
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open navigation menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LUMIQ LABS • Online",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LumiqCircleIconSurface(
            modifier = Modifier.testTag("notifications_button"),
            onClick = onNotificationsClick
        ) {
            BadgedBox(
                badge = {
                    if (unreadNotifications > 0) {
                        Badge(containerColor = ElectricBlue) {
                            Text(unreadNotifications.coerceAtMost(9).toString(), fontSize = 10.sp)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun LumiqBottomBar(
    currentRoute: LumiqRoute,
    voiceState: VoiceEngineState,
    onNavigate: (LumiqRoute) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val pillBg = if (isDark) ObsidianSurface.copy(alpha = 0.96f) else GlassWhite
    val borderCol = if (isDark) ObsidianBorder else ChromeSilverBorder
    val shadowCol = if (isDark) Color.Black.copy(alpha = 0.6f) else SoftShadowBlue.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(14.dp, RoundedCornerShape(32.dp), ambientColor = shadowCol, spotColor = shadowCol),
            shape = RoundedCornerShape(32.dp),
            color = pillBg,
            border = BorderStroke(1.dp, borderCol)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BottomNavItem(
                    label = "Home",
                    icon = Icons.Rounded.Home,
                    selected = currentRoute == LumiqRoute.HOME,
                    tag = "nav_home",
                    onClick = { onNavigate(LumiqRoute.HOME) }
                )
                BottomNavItem(
                    label = "Scan",
                    icon = Icons.Rounded.QrCodeScanner,
                    selected = currentRoute == LumiqRoute.VISION_DOCS,
                    tag = "nav_scan",
                    onClick = { onNavigate(LumiqRoute.VISION_DOCS) }
                )
                Spacer(modifier = Modifier.width(64.dp))
                BottomNavItem(
                    label = "Memory",
                    icon = Icons.Rounded.Psychology,
                    selected = currentRoute == LumiqRoute.PROFILE_MEMORY,
                    tag = "nav_memory",
                    onClick = { onNavigate(LumiqRoute.PROFILE_MEMORY) }
                )
                BottomNavItem(
                    label = "Chat",
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    selected = currentRoute == LumiqRoute.CHAT,
                    tag = "nav_chat",
                    onClick = { onNavigate(LumiqRoute.CHAT) }
                )
            }
        }

        // Elevated center glowing 10D Voice Mic Orb
        val micGradient = if (voiceState == VoiceEngineState.LISTENING) {
            Brush.linearGradient(listOf(CyanGlow, ElectricBlue))
        } else {
            Brush.linearGradient(listOf(ElectricBlue, Color(0xFF428BFF)))
        }
        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(64.dp)
                .shadow(16.dp, CircleShape, ambientColor = ElectricBlue.copy(alpha = 0.6f), spotColor = ElectricBlue)
                .clip(CircleShape)
                .background(micGradient)
                .clickable(role = Role.Button, onClick = onMicClick)
                .testTag("bottom_mic_button")
                .semantics {
                    contentDescription = "Activate LUMIQ Voice Assistant"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val activeBg = if (selected) ElectricBlue.copy(alpha = 0.14f) else Color.Transparent
    val contentCol = if (selected) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(activeBg)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentCol,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentCol
        )
    }
}

@Composable
fun LumiqNavigationDrawerContent(
    currentRoute: LumiqRoute,
    username: String,
    publicId: String,
    vipStatus: String,
    onSelectRoute: (LumiqRoute) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(305.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricBlue, CyanGlow))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("LUMIQ AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$username • $publicId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tier: ${vipStatus.uppercase()} • LUMIQ LABS", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

            LumiqRoute.entries.forEach { route ->
                NavigationDrawerItem(
                    label = { Text(route.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
                    icon = { Icon(route.icon, contentDescription = route.label) },
                    selected = route == currentRoute,
                    onClick = { onSelectRoute(route) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = ElectricBlue.copy(alpha = 0.12f),
                        selectedIconColor = ElectricBlue,
                        selectedTextColor = ElectricBlue
                    ),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag("drawer_${route.name.lowercase()}")
                )
            }
        }
    }
}

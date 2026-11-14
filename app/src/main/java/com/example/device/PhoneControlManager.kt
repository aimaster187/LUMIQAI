package com.example.device

import android.accessibilityservice.AccessibilityService
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.service.LumiqAccessibilityService
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DeviceActionResult(
    val executed: Boolean,
    val message: String,
    val requiresConfirmation: Boolean = false
)

object PhoneControlManager {

    private var torchEnabled = false

    fun toggleFlashlight(context: Context): DeviceActionResult {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull()
                ?: return DeviceActionResult(false, "No camera flash hardware found.")
            torchEnabled = !torchEnabled
            cm.setTorchMode(cameraId, torchEnabled)
            DeviceActionResult(true, if (torchEnabled) "Flashlight turned ON." else "Flashlight turned OFF.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Flashlight control unavailable: ${e.localizedMessage}")
        }
    }

    fun adjustVolume(context: Context, raise: Boolean): DeviceActionResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            DeviceActionResult(true, if (raise) "Media volume increased." else "Media volume decreased.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Could not adjust volume: ${e.localizedMessage}")
        }
    }

    fun toggleMediaPlayPause(context: Context): DeviceActionResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            DeviceActionResult(true, "Media play/pause key dispatched.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Media toggle unsupported: ${e.localizedMessage}")
        }
    }

    fun setQuickAlarm(context: Context, hour: Int, minute: Int, label: String): DeviceActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Alarm intent opened for %02d:%02d.".format(hour, minute))
        } catch (e: Exception) {
            DeviceActionResult(false, "No alarm clock handler found.")
        }
    }

    fun setQuickTimer(context: Context, seconds: Int, label: String): DeviceActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "Timer intent opened for ${seconds}s.")
        } catch (e: Exception) {
            DeviceActionResult(false, "No timer app found.")
        }
    }

    fun performAccessibilityNavigation(globalAction: Int, actionLabel: String): DeviceActionResult {
        val service = LumiqAccessibilityService.instance
            ?: return DeviceActionResult(
                false,
                "Enable 'LUMIQ AI Phone Control' in Android Accessibility Settings to use $actionLabel."
            )
        val ok = service.performUserAction(globalAction)
        return if (ok) {
            DeviceActionResult(true, "$actionLabel executed.")
        } else {
            DeviceActionResult(false, "System rejected $actionLabel on this screen.")
        }
    }

    fun openAppByName(context: Context, appQuery: String): DeviceActionResult {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val q = appQuery.trim().lowercase()
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(q)
        } ?: return DeviceActionResult(false, "No installed app matching '$appQuery' found.")

        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return DeviceActionResult(false, "Cannot launch ${match.loadLabel(pm)}.")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return DeviceActionResult(true, "Opened ${match.loadLabel(pm)}.")
    }

    fun openSystemSettings(context: Context, action: String = Settings.ACTION_SETTINGS): DeviceActionResult {
        return try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            DeviceActionResult(true, "Opened system settings.")
        } catch (e: Exception) {
            DeviceActionResult(false, "Settings screen unavailable: ${e.localizedMessage}")
        }
    }

    fun isDefaultAssistant(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            false
        }
    }

    fun createDefaultAssistantIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            }
        }
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openWhatsAppDraft(context: Context, phoneDigits: String, message: String): DeviceActionResult {
        return try {
            val encoded = URLEncoder.encode(message, "UTF-8")
            val cleanPhone = phoneDigits.filter { it.isDigit() }
            val intent = if (cleanPhone.isNotBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=$encoded"))
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            DeviceActionResult(true, "Opened WhatsApp with your approved draft. Confirm once sent.")
        } catch (_: Exception) {
            try {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(Intent.createChooser(share, "Share Draft via WhatsApp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                DeviceActionResult(true, "Opened share sheet with your approved draft.")
            } catch (e: Exception) {
                DeviceActionResult(false, "WhatsApp not installed or share unavailable.")
            }
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

    fun hasTermuxPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, TERMUX_RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun runTermuxCommand(context: Context, command: String): DeviceActionResult {
        val cleanCmd = command.trim()
        if (cleanCmd.isBlank()) {
            return DeviceActionResult(false, "Enter a shell command to run in Termux.")
        }
        if (!isPackageInstalled(context, "com.termux")) {
            return DeviceActionResult(
                false,
                "Termux (com.termux) is not installed on this device. Copy the generated script and run it inside Termux."
            )
        }
        if (!hasTermuxPermission(context)) {
            return DeviceActionResult(
                false,
                "Missing runtime permission com.termux.permission.RUN_COMMAND. Grant Termux Run Command permission and retry."
            )
        }
        return try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", cleanCmd))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            context.startService(intent)
            DeviceActionResult(true, "Dispatched command to Termux RUN_COMMAND service.")
        } catch (se: SecurityException) {
            DeviceActionResult(
                false,
                "Termux denied permission (ensure com.termux is installed cleanly & allow-external-apps=true in ~/.termux/termux.properties): ${se.localizedMessage}"
            )
        } catch (ise: IllegalStateException) {
            DeviceActionResult(
                false,
                "Android blocked background start for Termux service. Open Termux once so its session service is active, then retry: ${ise.localizedMessage}"
            )
        } catch (e: Exception) {
            DeviceActionResult(
                false,
                "Termux blocked external execution (enable allow-external-apps=true in ~/.termux/termux.properties): ${e.localizedMessage}"
            )
        }
    }

    fun tryFastLocalCommand(context: Context, command: String): DeviceActionResult? {
        val lower = command.trim().lowercase()
        return when {
            lower.contains("flashlight on") || lower.contains("torch on") || lower == "torch" || lower == "flashlight" ->
                toggleFlashlight(context)
            lower.contains("flashlight off") || lower.contains("torch off") ->
                toggleFlashlight(context)
            lower == "go home" || lower == "home screen" ->
                performAccessibilityNavigation(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
            lower == "go back" || lower == "navigate back" ->
                performAccessibilityNavigation(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
            lower == "recent apps" || lower == "recents" ->
                performAccessibilityNavigation(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recent Apps")
            lower == "quick settings" ->
                performAccessibilityNavigation(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Quick Settings")
            lower.contains("volume up") || lower.contains("increase volume") ->
                adjustVolume(context, true)
            lower.contains("volume down") || lower.contains("decrease volume") ->
                adjustVolume(context, false)
            lower.contains("pause music") || lower.contains("play music") || lower.contains("pause media") ->
                toggleMediaPlayPause(context)
            lower.startsWith("open ") && !lower.contains("website") && !lower.contains("file") -> {
                val appName = lower.removePrefix("open ").trim()
                if (appName.isNotBlank()) openAppByName(context, appName) else null
            }
            else -> null
        }
    }

    fun exportFilesAsZip(
        context: Context,
        archiveName: String,
        files: Map<String, String>
    ): Result<File> {
        return try {
            val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
            val safeName = archiveName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val zipFile = File(exportDir, "$safeName.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { (path, content) ->
                    zos.putNextEntry(ZipEntry(path.trimStart('/')))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            Result.success(zipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String = "application/zip") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

package com.example.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceTelemetry(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val batteryTempC: Float,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val mediaVolumePercent: Int,
    val ringVolumePercent: Int,
    val deviceModel: String
)

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

sealed interface ExecutionOutcome {
    data class Success(val message: String) : ExecutionOutcome
    data class PermissionRequired(val permission: String, val rationale: String) : ExecutionOutcome
    data class Failure(val message: String) : ExecutionOutcome
}

class PhoneControlExecutor(private val context: Context) {

    fun getDeviceTelemetry(): DeviceTelemetry {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.availableBytes.toDouble()
        val bytesTotal = stat.totalBytes.toDouble()
        val gb = 1024.0 * 1024.0 * 1024.0

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        val curRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)

        return DeviceTelemetry(
            batteryPercent = capacity,
            isCharging = isCharging,
            batteryTempC = tempTenths / 10f,
            storageFreeGb = bytesAvailable / gb,
            storageTotalGb = bytesTotal / gb,
            mediaVolumePercent = (curMusic * 100) / maxMusic,
            ringVolumePercent = (curRing * 100) / maxRing,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    fun setMediaVolumePercent(percent: Int): ExecutionOutcome {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val clamped = ((percent.coerceIn(0, 100) / 100.0) * max).toInt().coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, AudioManager.FLAG_SHOW_UI)
            ExecutionOutcome.Success("Media volume set to ${percent.coerceIn(0, 100)}%.")
        } catch (e: Exception) {
            ExecutionOutcome.Failure("Unable to adjust volume: ${e.localizedMessage}")
        }
    }

    fun adjustMediaVolume(deltaSteps: Int): ExecutionOutcome {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val direction = if (deltaSteps >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            ExecutionOutcome.Success("Media volume adjusted to ${(cur * 100) / max}%.")
        } catch (e: Exception) {
            ExecutionOutcome.Failure("Volume adjustment failed: ${e.localizedMessage}")
        }
    }

    fun launchAppByName(appQuery: String): ExecutionOutcome {
        val q = appQuery.lowercase().trim()
        val packageCandidates = when {
            "facebook" in q || "fb" in q -> listOf("com.facebook.katana")
            "whatsapp" in q -> listOf("com.whatsapp")
            "youtube" in q -> listOf("com.google.android.youtube")
            "chrome" in q || "browser" in q -> listOf("com.android.chrome")
            "map" in q -> listOf("com.google.android.apps.maps")
            "translate" in q -> listOf("com.google.android.apps.translate")
            "clock" in q || "alarm" in q -> listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock")
            "camera" in q -> listOf("com.sec.android.app.camera")
            "message" in q || "sms" in q -> listOf("com.samsung.android.messaging", "com.google.android.apps.messaging")
            "calendar" in q -> listOf("com.samsung.android.calendar", "com.google.android.calendar")
            "note" in q -> listOf("com.samsung.android.app.notes")
            "spotify" in q || "music" in q -> listOf("com.spotify.music")
            "instagram" in q -> listOf("com.instagram.android")
            "setting" in q -> return openPhoneSettings()
            else -> emptyList()
        }

        val pm = context.packageManager
        for (pkg in packageCandidates) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return ExecutionOutcome.Success("Opened $appQuery.")
            }
        }

        if ("facebook" in q) {
            return openWebUrl("https://m.facebook.com", "Facebook Web")
        }
        if ("camera" in q) {
            val camIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return tryStartActivity(camIntent, "Opened Camera.")
        }

        return ExecutionOutcome.Failure("App '$appQuery' is not installed or visible on this device.")
    }

    fun openAppInfoToClose(appName: String): ExecutionOutcome {
        val pkg = when {
            "facebook" in appName.lowercase() -> "com.facebook.katana"
            "whatsapp" in appName.lowercase() -> "com.whatsapp"
            "youtube" in appName.lowercase() -> "com.google.android.youtube"
            "chrome" in appName.lowercase() -> "com.android.chrome"
            else -> context.packageName
        }
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(
            intent,
            "Opened App Info for $appName so you can Force Stop it (Android requires manual confirmation to close third-party apps)."
        )
    }

    fun searchContacts(query: String): Pair<ExecutionOutcome, List<ContactMatch>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ExecutionOutcome.PermissionRequired(
                Manifest.permission.READ_CONTACTS,
                "Contacts permission is needed to find phone numbers for '$query'."
            ) to emptyList()
        }

        val matches = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = if (query.isNotBlank()) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        } else null
        val selectionArgs = if (query.isNotBlank()) arrayOf("%${query.trim()}%") else null

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && matches.size < 12) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        matches.add(ContactMatch(name, num))
                    }
                }
            }
            val msg = if (matches.isEmpty()) "No contacts found matching '$query'." else "Found ${matches.size} contact(s)."
            return ExecutionOutcome.Success(msg) to matches
        } catch (e: Exception) {
            return ExecutionOutcome.Failure("Contact query failed: ${e.localizedMessage}") to emptyList()
        }
    }

    fun dialOrCall(phoneNumber: String, directCallConfirmed: Boolean): ExecutionOutcome {
        val clean = phoneNumber.filter { it.isDigit() || it == '+' }
        if (clean.isBlank()) return ExecutionOutcome.Failure("Please provide a valid phone number.")
        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (directCallConfirmed && hasCallPerm) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$clean")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return tryStartActivity(callIntent, "Calling $clean now.")
        }
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(dialIntent, "Opened phone dialer for $clean.")
    }

    fun sendOrComposeSms(phoneNumber: String, message: String, directSendConfirmed: Boolean): ExecutionOutcome {
        val clean = phoneNumber.filter { it.isDigit() || it == '+' }
        if (clean.isBlank() || message.isBlank()) {
            return ExecutionOutcome.Failure("Phone number and SMS text are both required.")
        }
        val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (directSendConfirmed && hasSmsPerm) {
            return try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(clean, null, parts, null, null)
                ExecutionOutcome.Success("SMS sent to $clean.")
            } catch (e: Exception) {
                ExecutionOutcome.Failure("SMS send failed: ${e.localizedMessage}")
            }
        }
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(smsIntent, "Opened Messages draft to $clean.")
    }

    fun shareToWhatsApp(phoneNumber: String?, message: String): ExecutionOutcome {
        val cleanPhone = phoneNumber.orEmpty().filter { it.isDigit() }
        if (cleanPhone.isNotBlank()) {
            val encoded = URLEncoder.encode(message, "UTF-8")
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return tryStartActivity(waIntent, "Opened WhatsApp chat draft.")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(sendIntent, "Opened WhatsApp share composer.") {
            // Fallback to generic share sheet if WhatsApp isn't installed
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                },
                "Share message"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tryStartActivity(chooser, "Opened share sheet.")
        }
    }

    fun shareSocialPost(platform: String, caption: String, imageUri: Uri?): ExecutionOutcome {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, caption)
            if (imageUri != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            if (platform.equals("FACEBOOK", ignoreCase = true)) {
                setPackage("com.facebook.katana")
            } else if (platform.equals("WHATSAPP", ignoreCase = true)) {
                setPackage("com.whatsapp")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(sendIntent, "Opened $platform composer with your draft.") {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, caption)
                if (imageUri != null) {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
            }
            val chooser = Intent.createChooser(fallback, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tryStartActivity(chooser, "Opened system share sheet for your post.")
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): ExecutionOutcome {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis VIP Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(intent, "Alarm prepared for ${String.format(Locale.US, "%02d:%02d", hour, minute)}.")
    }

    fun insertCalendarEvent(title: String, description: String): ExecutionOutcome {
        val startMillis = System.currentTimeMillis() + 3600_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "Jarvis VIP Event" })
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3600_000L)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(intent, "Opened Calendar event editor for '$title'.")
    }

    fun openWifiSettings(): ExecutionOutcome {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return tryStartActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Opened Wi-Fi controls.")
    }

    fun openBluetoothSettings(): ExecutionOutcome {
        return tryStartActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened Bluetooth settings."
        )
    }

    fun openPhoneSettings(): ExecutionOutcome {
        return tryStartActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened Android device settings."
        )
    }

    fun openBatteryOptimizationSettings(): ExecutionOutcome {
        return tryStartActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened Battery Optimization settings for Galaxy A51 / Android background reliability."
        )
    }

    fun openNotificationListenerSettings(): ExecutionOutcome {
        return tryStartActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened Notification Access settings."
        )
    }

    fun openAccessibilitySettings(): ExecutionOutcome {
        return tryStartActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened Accessibility settings."
        )
    }

    fun openWebSearch(query: String): ExecutionOutcome {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return openWebUrl("https://www.google.com/search?q=$encoded", "Google Search: $query")
    }

    fun openNavigation(destination: String): ExecutionOutcome {
        val encoded = URLEncoder.encode(destination.trim(), "UTF-8")
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(navIntent, "Started navigation to $destination.") {
            openWebUrl("https://maps.google.com/?q=$encoded", "Maps: $destination")
        }
    }

    fun openWebUrl(url: String, label: String): ExecutionOutcome {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(intent, "Opened $label.")
    }

    fun createCameraImageUri(): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "jarvis_capture_$stamp.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    @SuppressLint("MissingPermission")
    fun getLastCoarseCoordinates(): Pair<Double, Double>? {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            loc?.let { it.latitude to it.longitude }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryStartActivity(
        intent: Intent,
        successMessage: String,
        onFallback: (() -> ExecutionOutcome)? = null
    ): ExecutionOutcome {
        return try {
            context.startActivity(intent)
            ExecutionOutcome.Success(successMessage)
        } catch (e: Exception) {
            if (onFallback != null) {
                onFallback()
            } else {
                ExecutionOutcome.Failure("No compatible app found (${e.localizedMessage ?: "ActivityNotFound"}).")
            }
        }
    }
}

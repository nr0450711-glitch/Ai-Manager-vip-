package com.example.assistant

import com.example.data.model.AssistantTask
import com.example.data.model.MemoryCategory
import com.example.data.model.PreferredLanguage
import com.example.data.model.SocialPlatform
import com.example.data.model.TaskCategory
import com.example.data.model.UserMemory
import com.example.data.remote.OpenAiChatClient
import com.example.data.remote.OpenAiResult
import com.example.data.remote.SmartServicesClient
import com.example.data.repository.JarvisRepository
import java.util.Locale

sealed interface PendingSensitiveAction {
    val title: String
    val summary: String

    data class PhoneCall(
        val contactName: String?,
        val phoneNumber: String
    ) : PendingSensitiveAction {
        override val title: String = "Confirm Phone Call"
        override val summary: String = "Call ${contactName ?: phoneNumber} ($phoneNumber)?"
    }

    data class SendSms(
        val recipient: String,
        val message: String
    ) : PendingSensitiveAction {
        override val title: String = "Confirm SMS Message"
        override val summary: String = "Send SMS to $recipient: \"$message\"?"
    }

    data class WhatsAppDraft(
        val phoneOrLabel: String?,
        val message: String
    ) : PendingSensitiveAction {
        override val title: String = "Confirm WhatsApp Share"
        override val summary: String = "Open WhatsApp draft${phoneOrLabel?.let { " for $it" } ?: ""}: \"$message\"?"
    }

    data class SocialShare(
        val platform: SocialPlatform,
        val caption: String,
        val draftId: String? = null
    ) : PendingSensitiveAction {
        override val title: String = "Confirm ${platform.label} Post"
        override val summary: String = "Prepare & share on ${platform.label}: \"$caption\"?"
    }

    data class SaveMemory(
        val key: String,
        val value: String
    ) : PendingSensitiveAction {
        override val title: String = "Remember Preference?"
        override val summary: String = "Save \"$key = $value\" to your JARVIS VIP memory?"
    }
}

data class CommandExecutionResult(
    val replyText: String,
    val actionSummary: String? = null,
    val pendingConfirmation: PendingSensitiveAction? = null,
    val permissionRequest: Pair<String, String>? = null,
    val openCameraRequested: Boolean = false,
    val openPhotoPickerRequested: Boolean = false
)

class JarvisCommandRouter(
    private val phoneControl: PhoneControlExecutor,
    private val smartServices: SmartServicesClient,
    private val openAiClient: OpenAiChatClient,
    private val repository: JarvisRepository
) {
    suspend fun processCommand(
        rawInput: String,
        preferredLanguage: PreferredLanguage,
        automationEnabled: Boolean,
        weatherCity: String,
        backendProxyUrl: String,
        history: List<com.example.data.model.ChatMessageRecord>,
        memories: List<UserMemory>,
        tasks: List<AssistantTask>
    ): CommandExecutionResult {
        val text = rawInput.trim()
        val lower = text.lowercase()

        // 1. Math evaluation fast path (offline instant)
        MathEvaluator.tryExtractAndEvaluate(text)?.let { result ->
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else String.format(Locale.US, "%.4f", result).trimEnd('0').trimEnd('.')
            val reply = if (containsDevanagari(text)) "इस गणना का उत्तर $formatted है।" else "The result is $formatted."
            return CommandExecutionResult(replyText = reply, actionSummary = "Calculated: $formatted")
        }

        // 2. Wake greeting check
        if (lower in setOf("hey jarvis", "jarvis", "hello jarvis", "hi jarvis", "हे जार्विस", "नमस्ते जार्विस")) {
            val reply = if (preferredLanguage == PreferredLanguage.HINDI || containsDevanagari(text)) {
                "नमस्ते! मैं जार्विस वीआईपी हूँ। बताइए, आज मैं आपकी क्या मदद कर सकती हूँ?"
            } else {
                "Hello! I am JARVIS VIP, online and ready. How may I assist you?"
            }
            return CommandExecutionResult(replyText = reply, actionSummary = "Greeting ready")
        }

        // 3. Hardware telemetry fast paths (Battery / Storage)
        if ("battery" in lower || "बैटरी" in lower) {
            val t = phoneControl.getDeviceTelemetry()
            val chargingTxt = if (t.isCharging) "charging" else "on battery"
            return CommandExecutionResult(
                replyText = "Your ${t.deviceModel} battery is at ${t.batteryPercent}% ($chargingTxt, ${t.batteryTempC}°C).",
                actionSummary = "Battery: ${t.batteryPercent}%"
            )
        }

        if ("storage" in lower || "space" in lower || "स्टोरेज" in lower) {
            val t = phoneControl.getDeviceTelemetry()
            return CommandExecutionResult(
                replyText = String.format(
                    Locale.US,
                    "Internal storage has %.1f GB free out of %.1f GB total.",
                    t.storageFreeGb,
                    t.storageTotalGb
                ),
                actionSummary = String.format(Locale.US, "%.1f GB free", t.storageFreeGb)
            )
        }

        // 4. Live Weather fast path
        if ("weather" in lower || "mausam" in lower || "मौसम" in lower || "temperature" in lower) {
            val coords = phoneControl.getLastCoarseCoordinates()
            val weatherRes = if (coords != null) {
                smartServices.fetchWeatherByCoordinates(coords.first, coords.second, "Current Location")
            } else {
                smartServices.fetchWeatherByCity(weatherCity)
            }
            return weatherRes.fold(
                onSuccess = { w ->
                    CommandExecutionResult(
                        replyText = "Weather in ${w.locationName}: ${w.temperatureC}°C (${w.condition}), feels like ${w.feelsLikeC}°C, humidity ${w.humidityPercent}%, wind ${w.windKmh} km/h.",
                        actionSummary = "${w.locationName}: ${w.temperatureC}°C"
                    )
                },
                onFailure = { err ->
                    CommandExecutionResult(
                        replyText = "Could not fetch live weather right now: ${err.localizedMessage ?: "Network unavailable"}.",
                        actionSummary = "Weather failed"
                    )
                }
            )
        }

        // 5. Live News fast path
        if ("news" in lower || "headlines" in lower || "समाचार" in lower || "खबर" in lower) {
            val useHindi = preferredLanguage == PreferredLanguage.HINDI || containsDevanagari(text)
            return smartServices.fetchDailyNews(hindi = useHindi).fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        CommandExecutionResult("No headlines returned right now.")
                    } else {
                        val top3 = list.take(3).mapIndexed { idx, h -> "${idx + 1}. ${h.title}" }.joinToString("\n")
                        CommandExecutionResult(
                            replyText = "Top headlines:\n$top3",
                            actionSummary = "Fetched ${list.size} headlines"
                        )
                    }
                },
                onFailure = { err ->
                    CommandExecutionResult("Failed to load live news: ${err.localizedMessage ?: "Network error"}")
                }
            )
        }

        // 6. Notifications reader
        if ("notification" in lower || "नोटिफिकेशन" in lower) {
            if (!NotificationInboxStore.isConnected.value) {
                phoneControl.openNotificationListenerSettings()
                return CommandExecutionResult(
                    replyText = "Notification Reader permission is not enabled yet. I opened Notification Access settings so you can allow Jarvis VIP.",
                    actionSummary = "Opened Notification Settings"
                )
            }
            val recent = NotificationInboxStore.notifications.value.take(3)
            val reply = if (recent.isEmpty()) {
                "You have no recent notifications in the queue."
            } else {
                "Recent notifications:\n" + recent.joinToString("\n") { "• ${it.title}: ${it.text}" }
            }
            return CommandExecutionResult(replyText = reply, actionSummary = "Read ${recent.size} notifications")
        }

        // 7. Automation gate check for device control commands
        if (automationEnabled) {
            if ("volume up" in lower || "increase volume" in lower || "आवाज़ बढ़ाओ" in lower) {
                val res = phoneControl.adjustMediaVolume(+1)
                return outcomeToResult(res, "Volume +")
            }
            if ("volume down" in lower || "decrease volume" in lower || "आवाज़ कम" in lower) {
                val res = phoneControl.adjustMediaVolume(-1)
                return outcomeToResult(res, "Volume -")
            }
            if ("mute" in lower) {
                val res = phoneControl.setMediaVolumePercent(0)
                return outcomeToResult(res, "Muted")
            }
            if ("wifi" in lower || "wi-fi" in lower || "वाईफाई" in lower) {
                return outcomeToResult(phoneControl.openWifiSettings(), "Wi-Fi settings")
            }
            if ("bluetooth" in lower || "ब्लूटूथ" in lower) {
                return outcomeToResult(phoneControl.openBluetoothSettings(), "Bluetooth settings")
            }
            if ("open settings" in lower || "phone settings" in lower || "सेटिंग्स खोलो" in lower) {
                return outcomeToResult(phoneControl.openPhoneSettings(), "Phone settings")
            }
            if ("open camera" in lower || "take photo" in lower || "कैमरा खोलो" in lower) {
                return CommandExecutionResult(
                    replyText = "Launching camera capture for you.",
                    actionSummary = "Camera ready",
                    openCameraRequested = true
                )
            }
            if ("select photo" in lower || "upload photo" in lower || "gallery" in lower) {
                return CommandExecutionResult(
                    replyText = "Opening Android Photo Picker so you can safely select a photo.",
                    actionSummary = "Photo Picker",
                    openPhotoPickerRequested = true
                )
            }
            if (lower.startsWith("navigate to ") || lower.startsWith("directions to ")) {
                val dest = text.substringAfter("to ").trim()
                return outcomeToResult(phoneControl.openNavigation(dest), "Navigate: $dest")
            }
            if (lower.startsWith("search ") || lower.startsWith("google ")) {
                val q = text.substringAfter(" ").trim()
                return outcomeToResult(phoneControl.openWebSearch(q), "Search: $q")
            }
            if (lower.startsWith("open ")) {
                val app = text.removePrefix("open ").removePrefix("Open ").trim()
                return outcomeToResult(phoneControl.launchAppByName(app), "Open: $app")
            }
            if (lower.startsWith("remind me to ") || lower.startsWith("add reminder ") || lower.startsWith("add task ")) {
                val itemTitle = text
                    .replace(Regex("^(?i)(remind me to|add reminder|add task)\\s+"), "")
                    .trim()
                if (itemTitle.isNotEmpty()) {
                    val cat = if ("remind" in lower) TaskCategory.REMINDER else TaskCategory.TASK
                    val saved = repository.addTask(itemTitle, cat)
                    return if (saved.isSuccess) {
                        CommandExecutionResult(
                            replyText = "Saved ${cat.label.lowercase()}: \"$itemTitle\".",
                            actionSummary = "Saved ${cat.label}"
                        )
                    } else {
                        CommandExecutionResult(
                            replyText = "Failed to save ${cat.label.lowercase()}: ${saved.exceptionOrNull()?.localizedMessage}"
                        )
                    }
                }
            }
            if (lower.startsWith("set alarm")) {
                val timeMatch = Regex("""(\d{1,2})[:.](\d{2})""").find(lower)
                if (timeMatch != null) {
                    val h = timeMatch.groupValues[1].toIntOrNull() ?: 7
                    val m = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    val res = phoneControl.setAlarm(h, m, "Jarvis VIP Alarm")
                    repository.addTask("Alarm ${String.format(Locale.US, "%02d:%02d", h, m)}", TaskCategory.ALARM, String.format(Locale.US, "%02d:%02d", h, m))
                    return outcomeToResult(res, "Alarm $h:$m")
                }
            }
        }

        // 8. OpenAI ChatGPT Brain (Natural bilingual conversation, reasoning, translation, drafting, multi-step intents)
        return when (val ai = openAiClient.chat(text, preferredLanguage, history, memories, tasks, backendProxyUrl)) {
            is OpenAiResult.Success -> {
                handleAiAction(ai, automationEnabled, weatherCity)
            }
            is OpenAiResult.NeedsConfiguration -> {
                CommandExecutionResult(
                    replyText = ai.message,
                    actionSummary = "OpenAI configuration needed"
                )
            }
            is OpenAiResult.Error -> {
                CommandExecutionResult(
                    replyText = ai.message,
                    actionSummary = "AI API error"
                )
            }
        }
    }

    private suspend fun handleAiAction(
        ai: OpenAiResult.Success,
        automationEnabled: Boolean,
        weatherCity: String
    ): CommandExecutionResult {
        val memoryConfirmation = if (!ai.memorySuggestionKey.isNullOrBlank() && !ai.memorySuggestionValue.isNullOrBlank()) {
            PendingSensitiveAction.SaveMemory(ai.memorySuggestionKey, ai.memorySuggestionValue)
        } else null

        val action = ai.actionType.orEmpty().uppercase()
        val target = ai.actionTarget.orEmpty()
        val payload = ai.actionPayload.orEmpty()

        if (!automationEnabled && action.isNotBlank()) {
            return CommandExecutionResult(
                replyText = "${ai.reply}\n\n(Automation is currently paused in Security Controls.)",
                actionSummary = "Automation paused",
                pendingConfirmation = memoryConfirmation
            )
        }

        return when (action) {
            "OPEN_APP" -> {
                val res = phoneControl.launchAppByName(target.ifBlank { payload })
                mergeOutcome(ai.reply, res, "Open ${target.ifBlank { payload }}")
            }
            "CALL" -> {
                val (outcome, contacts) = if (target.any { it.isDigit() }) {
                    ExecutionOutcome.Success("Ready") to listOf(ContactMatch(target, target))
                } else {
                    phoneControl.searchContacts(target)
                }
                if (outcome is ExecutionOutcome.PermissionRequired) {
                    CommandExecutionResult(
                        replyText = ai.reply,
                        permissionRequest = outcome.permission to outcome.rationale
                    )
                } else {
                    val match = contacts.firstOrNull()
                    if (match != null) {
                        CommandExecutionResult(
                            replyText = ai.reply,
                            actionSummary = "Call confirmation needed",
                            pendingConfirmation = PendingSensitiveAction.PhoneCall(match.name, match.phoneNumber)
                        )
                    } else {
                        CommandExecutionResult(
                            replyText = "${ai.reply}\n\nI could not find a phone number for '$target'.",
                            actionSummary = "Contact not found"
                        )
                    }
                }
            }
            "SMS" -> CommandExecutionResult(
                replyText = ai.reply,
                actionSummary = "SMS confirmation needed",
                pendingConfirmation = PendingSensitiveAction.SendSms(
                    recipient = target.ifBlank { "Select contact" },
                    message = payload.ifBlank { ai.reply }
                )
            )
            "WHATSAPP" -> CommandExecutionResult(
                replyText = ai.reply,
                actionSummary = "WhatsApp confirmation needed",
                pendingConfirmation = PendingSensitiveAction.WhatsAppDraft(
                    phoneOrLabel = target.takeIf { it.isNotBlank() },
                    message = payload.ifBlank { ai.reply }
                )
            )
            "FACEBOOK_DRAFT" -> {
                val caption = payload.ifBlank { ai.reply }
                val res = repository.createSocialDraft(SocialPlatform.FACEBOOK, caption, target.takeIf { it.isNotBlank() }, false)
                CommandExecutionResult(
                    replyText = ai.reply,
                    actionSummary = "Facebook draft saved",
                    pendingConfirmation = PendingSensitiveAction.SocialShare(
                        platform = SocialPlatform.FACEBOOK,
                        caption = caption,
                        draftId = res.getOrNull()
                    )
                )
            }
            "REMINDER", "TASK", "BIRTHDAY" -> {
                val cat = TaskCategory.fromRaw(action)
                val title = payload.ifBlank { target }.ifBlank { "Assistant item" }
                repository.addTask(title, cat, target.takeIf { it.isNotBlank() })
                CommandExecutionResult(
                    replyText = ai.reply,
                    actionSummary = "Saved ${cat.label}: $title",
                    pendingConfirmation = memoryConfirmation
                )
            }
            "ALARM" -> {
                val m = Regex("""(\d{1,2}):(\d{2})""").find(target)
                if (m != null) {
                    val h = m.groupValues[1].toIntOrNull() ?: 7
                    val min = m.groupValues[2].toIntOrNull() ?: 0
                    val res = phoneControl.setAlarm(h, min, payload.ifBlank { "Jarvis Alarm" })
                    mergeOutcome(ai.reply, res, "Alarm $h:$min")
                } else {
                    CommandExecutionResult(ai.reply, pendingConfirmation = memoryConfirmation)
                }
            }
            "WEATHER" -> {
                val w = smartServices.fetchWeatherByCity(target.ifBlank { weatherCity }).getOrNull()
                val extra = w?.let { "\nLive Weather (${it.locationName}): ${it.temperatureC}°C, ${it.condition}." }.orEmpty()
                CommandExecutionResult(ai.reply + extra, actionSummary = "Weather")
            }
            "SEARCH" -> mergeOutcome(ai.reply, phoneControl.openWebSearch(payload.ifBlank { target }), "Web Search")
            "NAVIGATE" -> mergeOutcome(ai.reply, phoneControl.openNavigation(target.ifBlank { payload }), "Navigation")
            "WIFI_SETTINGS" -> mergeOutcome(ai.reply, phoneControl.openWifiSettings(), "Wi-Fi")
            "BLUETOOTH_SETTINGS" -> mergeOutcome(ai.reply, phoneControl.openBluetoothSettings(), "Bluetooth")
            "CAMERA" -> CommandExecutionResult(ai.reply, actionSummary = "Camera", openCameraRequested = true)
            else -> CommandExecutionResult(
                replyText = ai.reply,
                actionSummary = if (ai.usedBackendProxy) "ChatGPT via Secure Proxy" else "ChatGPT API",
                pendingConfirmation = memoryConfirmation
            )
        }
    }

    private fun mergeOutcome(aiReply: String, outcome: ExecutionOutcome, summary: String): CommandExecutionResult {
        return when (outcome) {
            is ExecutionOutcome.Success -> CommandExecutionResult(
                replyText = "$aiReply\n\n[${outcome.message}]",
                actionSummary = summary
            )
            is ExecutionOutcome.PermissionRequired -> CommandExecutionResult(
                replyText = aiReply,
                permissionRequest = outcome.permission to outcome.rationale
            )
            is ExecutionOutcome.Failure -> CommandExecutionResult(
                replyText = "$aiReply\n\n[Action failed: ${outcome.message}]",
                actionSummary = "Action failed"
            )
        }
    }

    private fun outcomeToResult(outcome: ExecutionOutcome, summary: String): CommandExecutionResult {
        return when (outcome) {
            is ExecutionOutcome.Success -> CommandExecutionResult(outcome.message, summary)
            is ExecutionOutcome.PermissionRequired -> CommandExecutionResult(
                replyText = outcome.rationale,
                permissionRequest = outcome.permission to outcome.rationale
            )
            is ExecutionOutcome.Failure -> CommandExecutionResult("Action failed: ${outcome.message}", "Failed")
        }
    }

    private fun containsDevanagari(s: String): Boolean = s.any { it in '\u0900'..'\u097F' }
}

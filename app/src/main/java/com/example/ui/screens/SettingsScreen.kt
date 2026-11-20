package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MemoryCategory
import com.example.data.model.PreferredLanguage
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.example.data.repository.LocalDeviceSettings
import com.example.ui.components.GlassHudCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.PureIce
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.VoidNavy
import com.example.ui.viewmodel.UiState

@Composable
fun SettingsScreen(
    profileState: UiState<UserProfile?>,
    memoriesState: UiState<List<UserMemory>>,
    localSettings: LocalDeviceSettings,
    activeVoiceName: String,
    wakeGuardRunning: Boolean,
    notificationAccessConnected: Boolean,
    accessibilityConnected: Boolean,
    openAiConfigured: Boolean,
    onLanguageChange: (PreferredLanguage) -> Unit,
    onVoiceTuningChange: (Float, Float) -> Unit,
    onTestFemaleVoice: () -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onToggleAutomation: (Boolean) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onUpdateWeatherCity: (String) -> Unit,
    onUpdateBackendProxyUrl: (String) -> Unit,
    onAddMemory: (String, String, MemoryCategory) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = (profileState as? UiState.Success)?.data
    val selectedLang = PreferredLanguage.fromRaw(profile?.preferredLanguage)
    var pitch by remember(profile?.voicePitch) { mutableFloatStateOf((profile?.voicePitch ?: 1.08).toFloat()) }
    var rate by remember(profile?.voiceRate) { mutableFloatStateOf((profile?.voiceRate ?: 1.0).toFloat()) }
    var cityInput by remember(localSettings.weatherCity) { mutableStateOf(localSettings.weatherCity) }
    var proxyInput by remember(localSettings.backendProxyUrl) { mutableStateOf(localSettings.backendProxyUrl) }
    var memKey by remember { mutableStateOf("") }
    var memVal by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("VIP SETTINGS & SECURITY", style = MaterialTheme.typography.headlineMedium, color = ElectricCyan)
            Text("Voice, Language, Privacy Kill-Switches & Galaxy A51 Optimization", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        }

        // Security Kill-Switches & Hands-Free Mode
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = "Security", tint = ElectricCyan)
                    Text("PRIVACY & AUTOMATION CONTROLS", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Microphone Master Switch",
                    subtitle = "Disable to block all voice recording immediately",
                    checked = localSettings.micEnabled,
                    onCheckedChange = onToggleMic,
                    tag = "mic_kill_switch"
                )
                SettingToggleRow(
                    title = "Device Automation",
                    subtitle = "Allow JARVIS to open apps, adjust volume & set alarms",
                    checked = localSettings.automationEnabled,
                    onCheckedChange = onToggleAutomation,
                    tag = "automation_kill_switch"
                )
                SettingToggleRow(
                    title = "Hands-Free \"Hey JARVIS\" Guard",
                    subtitle = if (wakeGuardRunning) "Foreground service active" else "Foreground microphone wake listener",
                    checked = localSettings.handsFreeWakeWord && wakeGuardRunning,
                    onCheckedChange = onToggleWakeWord,
                    tag = "wake_word_switch"
                )
                SettingToggleRow(
                    title = "Auto-Speak AI Replies",
                    subtitle = "Speak assistant responses aloud using Female AI Voice",
                    checked = localSettings.autoSpeakReplies,
                    onCheckedChange = onToggleAutoSpeak,
                    tag = "auto_speak_switch"
                )
            }
        }

        // Bilingual Language & Female AI Voice Calibration
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Language, contentDescription = "Language", tint = NeonTeal)
                    Text("LANGUAGE & FEMALE AI VOICE", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferredLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = VoidNavy
                            )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Active Voice: $activeVoiceName", style = MaterialTheme.typography.labelSmall, color = NeonTeal)
                Text("Pitch: ${String.format("%.2f", pitch)}x", style = MaterialTheme.typography.bodyMedium, color = PureIce)
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    onValueChangeFinished = { onVoiceTuningChange(pitch, rate) },
                    valueRange = 0.7f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
                Text("Speech Rate: ${String.format("%.2f", rate)}x", style = MaterialTheme.typography.bodyMedium, color = PureIce)
                Slider(
                    value = rate,
                    onValueChange = { rate = it },
                    onValueChangeFinished = { onVoiceTuningChange(pitch, rate) },
                    valueRange = 0.7f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
                OutlinedButton(onClick = onTestFemaleVoice) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test Female AI Voice")
                }
            }
        }

        // OpenAI Brain & Secure Backend Proxy
        item {
            GlassHudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("OPENAI CHATGPT BRAIN", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    StatusPillBadge(label = if (openAiConfigured) "Connected" else "Unconfigured", active = openAiConfigured)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "For production security, deploy the included /backend/server.js proxy so your OpenAI key never lives inside the APK, and enter your HTTPS backend URL below:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = proxyInput,
                        onValueChange = { proxyInput = it },
                        label = { Text("Secure Backend Proxy URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onUpdateBackendProxyUrl(proxyInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
                    ) {
                        Text("Save")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        label = { Text("Default Weather City") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onUpdateWeatherCity(cityInput) }) {
                        Text("Set City")
                    }
                }
            }
        }

        // Approved Personal Memory
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Memory, contentDescription = "Approved Memory", tint = ElectricCyan)
                    Text("USER-APPROVED MEMORY", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = memKey,
                        onValueChange = { memKey = it },
                        label = { Text("Key (e.g. Tea)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = memVal,
                        onValueChange = { memVal = it },
                        label = { Text("Value (e.g. Ginger Chai)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (memKey.isNotBlank() && memVal.isNotBlank()) {
                            onAddMemory(memKey, memVal, MemoryCategory.PREFERENCE)
                            memKey = ""
                            memVal = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
                ) {
                    Text("Save Preference")
                }
                val memories = (memoriesState as? UiState.Success)?.data.orEmpty()
                if (memories.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        memories.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DeepNavy.copy(alpha = 0.75f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.memoryKey, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                                    Text(m.memoryValue, style = MaterialTheme.typography.bodyMedium, color = PureIce)
                                }
                                IconButton(onClick = { onDeleteMemory(m.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = MutedSteel, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Samsung Galaxy A51 Battery & Optional System Services
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = "Galaxy A51 Optimization", tint = ElectricCyan)
                    Text("GALAXY A51 & OPTIONAL SERVICES", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "For uninterrupted Hey JARVIS voice guard on Samsung One UI (Galaxy A51), set Jarvis VIP Battery to Unrestricted and opt-in to Notification/Accessibility only if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenBatteryOptimization, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Battery Optimization Settings")
                    }
                    OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(if (notificationAccessConnected) "Notification Reader: Enabled" else "Enable Notification Reader (Optional)")
                    }
                    OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                        Text(if (accessibilityConnected) "Accessibility Helper: Enabled" else "Accessibility Service (Disabled by Default)")
                    }
                }
            }
        }

        // Account Sign Out
        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = StatusCrimson)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out of Google Account", color = StatusCrimson)
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = PureIce)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VoidNavy,
                checkedTrackColor = ElectricCyan
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

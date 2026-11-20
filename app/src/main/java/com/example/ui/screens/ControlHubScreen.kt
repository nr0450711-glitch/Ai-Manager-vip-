package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.assistant.CapturedNotification
import com.example.assistant.ContactMatch
import com.example.assistant.DeviceTelemetry
import com.example.ui.components.GlassHudCard
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.PureIce
import com.example.ui.theme.VoidNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlHubScreen(
    telemetry: DeviceTelemetry,
    contactMatches: List<ContactMatch>,
    notifications: List<CapturedNotification>,
    notificationAccessConnected: Boolean,
    onLaunchApp: (String) -> Unit,
    onOpenAppInfoToClose: (String) -> Unit,
    onSearchContacts: (String) -> Unit,
    onCallContact: (String?, String) -> Unit,
    onSendSms: (String, String) -> Unit,
    onWhatsApp: (String?, String) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSetAlarm: (Int, Int, String) -> Unit,
    onAddCalendarEvent: (String, String) -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenNavigation: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onClearNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contactQuery by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var searchOrNavInput by remember { mutableStateOf("") }
    var alarmHour by remember { mutableStateOf("07") }
    var alarmMinute by remember { mutableStateOf("30") }
    var calendarTitle by remember { mutableStateOf("") }
    var volumeSlider by remember(telemetry.mediaVolumePercent) {
        mutableFloatStateOf(telemetry.mediaVolumePercent.toFloat())
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("DEVICE CONTROL HUB", style = MaterialTheme.typography.headlineMedium, color = ElectricCyan)
            Text("Samsung Galaxy A51 & Android System Automation", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        }

        // System Hardware & Shortcuts
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", tint = ElectricCyan)
                    Text("SYSTEM CONTROLS & VOLUME (${volumeSlider.toInt()}%)", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Slider(
                    value = volumeSlider,
                    onValueChange = { volumeSlider = it },
                    onValueChangeFinished = { onSetVolume(volumeSlider.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onOpenWifi, modifier = Modifier.testTag("wifi_settings_button")) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wi-Fi")
                    }
                    OutlinedButton(onClick = onOpenBluetooth, modifier = Modifier.testTag("bluetooth_settings_button")) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bluetooth")
                    }
                    OutlinedButton(onClick = onOpenCamera, modifier = Modifier.testTag("camera_launch_button")) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Camera")
                    }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.testTag("phone_settings_button")) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Settings")
                    }
                }
            }
        }

        // App Launcher & App Management
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Apps, contentDescription = "Apps", tint = NeonTeal)
                    Text("APP LAUNCHER & FORCE-STOP SHORTCUT", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Facebook", "WhatsApp", "YouTube", "Chrome", "Maps", "Clock", "Calendar", "Translate").forEach { app ->
                        AssistChip(
                            onClick = { onLaunchApp(app) },
                            label = { Text(app) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = DeepNavy, labelColor = PureIce)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Need to close an app? Open Android App Info to Force Stop safely:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpenAppInfoToClose("Facebook") }) { Text("Manage Facebook", color = ElectricCyan) }
                    TextButton(onClick = { onOpenAppInfoToClose("WhatsApp") }) { Text("Manage WhatsApp", color = ElectricCyan) }
                }
            }
        }

        // Contacts, Call, SMS & WhatsApp
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Contacts, contentDescription = "Communications", tint = ElectricCyan)
                    Text("CONTACTS, CALL, SMS & WHATSAPP", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = contactQuery,
                        onValueChange = { contactQuery = it },
                        label = { Text("Search Contact Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSearchContacts(contactQuery) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
                    ) {
                        Text("Find")
                    }
                }
                if (contactMatches.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        contactMatches.take(5).forEach { match ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DeepNavy.copy(alpha = 0.7f))
                                    .clickable { phoneInput = match.phoneNumber }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(match.name, style = MaterialTheme.typography.bodyLarge, color = PureIce)
                                    Text(match.phoneNumber, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
                                }
                                Text("Select", color = ElectricCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("phone_number_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    label = { Text("Message Text (SMS / WhatsApp)") },
                    modifier = Modifier.fillMaxWidth().testTag("message_body_input")
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCallContact(null, phoneInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy),
                        modifier = Modifier.testTag("call_action_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Call")
                    }
                    OutlinedButton(
                        onClick = { onSendSms(phoneInput, messageBody) },
                        modifier = Modifier.testTag("sms_action_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SMS")
                    }
                    OutlinedButton(
                        onClick = { onWhatsApp(phoneInput.ifBlank { null }, messageBody) },
                        modifier = Modifier.testTag("whatsapp_action_button")
                    ) {
                        Text("WhatsApp")
                    }
                }
            }
        }

        // Alarm, Calendar, Search & Navigation
        item {
            GlassHudCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Alarm, contentDescription = "Productivity", tint = NeonTeal)
                    Text("ALARM, CALENDAR, SEARCH & MAPS", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = alarmHour,
                        onValueChange = { alarmHour = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Hr") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = alarmMinute,
                        onValueChange = { alarmMinute = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Min") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onSetAlarm(alarmHour.toIntOrNull() ?: 7, alarmMinute.toIntOrNull() ?: 30, "Jarvis VIP")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
                    ) {
                        Text("Set Alarm")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = calendarTitle,
                        onValueChange = { calendarTitle = it },
                        label = { Text("Calendar Event Title") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onAddCalendarEvent(calendarTitle.ifBlank { "Jarvis Meeting" }, "Scheduled via Jarvis VIP") }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Event")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchOrNavInput,
                    onValueChange = { searchOrNavInput = it },
                    label = { Text("Web Search or Maps Destination") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (searchOrNavInput.isNotBlank()) onOpenSearch(searchOrNavInput) }) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Search Web")
                    }
                    OutlinedButton(onClick = { if (searchOrNavInput.isNotBlank()) onOpenNavigation(searchOrNavInput) }) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Navigate")
                    }
                }
            }
        }

        // Notification Reader Card
        item {
            GlassHudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = ElectricCyan)
                        Text("NOTIFICATION READER", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    }
                    if (!notificationAccessConnected) {
                        TextButton(onClick = onOpenNotificationSettings) {
                            Text("Enable Access", color = NeonTeal)
                        }
                    } else {
                        TextButton(onClick = onClearNotifications) {
                            Text("Clear", color = MutedSteel)
                        }
                    }
                }
                if (!notificationAccessConnected) {
                    Text(
                        text = "Grant Notification Access in Android Settings so JARVIS can read incoming alerts aloud when asked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                } else if (notifications.isEmpty()) {
                    Text("Listening for new notifications…", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        notifications.take(5).forEach { n ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DeepNavy.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Text(n.title, style = MaterialTheme.typography.bodyLarge, color = PureIce)
                                Text(n.text, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                            }
                        }
                    }
                }
            }
        }
    }
}

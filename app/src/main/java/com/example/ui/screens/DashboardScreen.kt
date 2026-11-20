package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.assistant.DeviceTelemetry
import com.example.assistant.VoiceListeningState
import com.example.data.model.AssistantTask
import com.example.data.model.TaskCategory
import com.example.data.remote.NewsHeadline
import com.example.data.remote.WeatherReport
import com.example.ui.components.AnimatedVoiceWaveform
import com.example.ui.components.GlassHudCard
import com.example.ui.components.HolographicCoreVisualizer
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.PanelNavy
import com.example.ui.theme.PureIce
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.VoidNavy
import com.example.ui.viewmodel.SmartDataState
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun DashboardScreen(
    displayName: String,
    listeningState: VoiceListeningState,
    isSpeaking: Boolean,
    isThinking: Boolean,
    micEnabled: Boolean,
    wakeGuardRunning: Boolean,
    openAiConfigured: Boolean,
    telemetry: DeviceTelemetry,
    weatherState: SmartDataState<WeatherReport>,
    newsState: SmartDataState<List<NewsHeadline>>,
    tasksState: UiState<List<AssistantTask>>,
    onMicToggle: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onRefreshWeather: () -> Unit,
    onRefreshNews: () -> Unit,
    onOpenNewsLink: (String, String) -> Unit,
    onAddTask: (String, TaskCategory, String?) -> Unit,
    onToggleTask: (String, Boolean) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val isListening = listeningState is VoiceListeningState.Listening
    val rmsDb = (listeningState as? VoiceListeningState.Listening)?.rmsDb ?: 0f
    val partialText = (listeningState as? VoiceListeningState.Listening)?.partialText.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hud_banner),
                    contentDescription = "Holographic HUD command deck",
                    modifier = Modifier
                        .matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoidNavy.copy(alpha = 0.55f))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WELCOME, ${displayName.uppercase()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                            Text(
                                text = "JARVIS VIP CORE",
                                style = MaterialTheme.typography.headlineMedium,
                                color = PureIce
                            )
                        }
                        StatusPillBadge(
                            label = if (openAiConfigured) "ChatGPT Live" else "Setup Key",
                            active = openAiConfigured
                        )
                    }

                    HolographicCoreVisualizer(
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        isThinking = isThinking,
                        micEnabled = micEnabled,
                        rmsDb = rmsDb,
                        onMicClick = onMicToggle
                    )

                    AnimatedVoiceWaveform(
                        active = isListening || isSpeaking,
                        rmsDb = if (isSpeaking) 8f else rmsDb
                    )

                    Text(
                        text = when {
                            !micEnabled -> "Microphone Kill-Switch Active"
                            isListening && partialText.isNotBlank() -> partialText
                            isListening -> "Listening… Speak in Hindi or English"
                            isThinking -> "Processing with AI Brain…"
                            isSpeaking -> "Speaking response…"
                            wakeGuardRunning -> "Hey JARVIS Wake Guard Active • Tap Orb to Speak"
                            else -> "Tap Core Orb & say \"Hey JARVIS\""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isListening) NeonTeal else MutedSteel
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickPrompts = listOf(
                            "Weather update" to "What is today's weather?",
                            "आज की खबरें" to "आज के मुख्य समाचार बताओ",
                            "Battery status" to "Check battery status",
                            "Set alarm 07:00" to "Set alarm 07:00"
                        )
                        quickPrompts.forEach { (label, cmd) ->
                            AssistChip(
                                onClick = { onQuickCommand(cmd) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = PanelNavy.copy(alpha = 0.9f),
                                    labelColor = PureIce
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = ElectricCyan.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Telemetry Row: Battery & Storage
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassHudCard(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = if (telemetry.batteryPercent > 20) StatusEmerald else StatusCrimson
                        )
                        Text("BATTERY", style = MaterialTheme.typography.labelLarge, color = MutedSteel)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${telemetry.batteryPercent}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureIce
                    )
                    Text(
                        text = "${if (telemetry.isCharging) "Charging" else "Discharging"} • ${telemetry.batteryTempC}°C",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }

                GlassHudCard(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Storage",
                            tint = ElectricCyan
                        )
                        Text("STORAGE", style = MaterialTheme.typography.labelLarge, color = MutedSteel)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f GB", telemetry.storageFreeGb),
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureIce
                    )
                    Text(
                        text = String.format(Locale.US, "Free of %.0f GB", telemetry.storageTotalGb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
            }
        }

        // Live Weather Card
        item {
            GlassHudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.WbSunny, contentDescription = "Weather", tint = StatusAmber)
                        Text("LIVE WEATHER", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    }
                    IconButton(onClick = onRefreshWeather, modifier = Modifier.testTag("refresh_weather_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh weather", tint = PureIce)
                    }
                }
                when (weatherState) {
                    SmartDataState.Idle, SmartDataState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ElectricCyan)
                            Text("Syncing Open-Meteo telemetry…", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        }
                    }
                    is SmartDataState.Error -> {
                        Text(weatherState.message, style = MaterialTheme.typography.bodyMedium, color = StatusCrimson)
                    }
                    is SmartDataState.Ready -> {
                        val w = weatherState.data
                        Text(
                            text = "${w.temperatureC}°C • ${w.condition}",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureIce
                        )
                        Text(
                            text = "${w.locationName} • Feels like ${w.feelsLikeC}°C • Humidity ${w.humidityPercent}% • Wind ${w.windKmh} km/h",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                }
            }
        }

        // Smart Tasks, Reminders, Schedule & Birthdays
        item {
            GlassHudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Smart Tasks", tint = NeonTeal)
                        Text("TASKS, REMINDERS & BIRTHDAYS", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    }
                    IconButton(onClick = { showAddTaskDialog = true }, modifier = Modifier.testTag("add_task_button")) {
                        Icon(Icons.Default.Add, contentDescription = "Add task or reminder", tint = ElectricCyan)
                    }
                }
                Spacer(Modifier.height(6.dp))
                when (tasksState) {
                    UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan)
                    is UiState.Error -> Text(tasksState.message, color = StatusCrimson, style = MaterialTheme.typography.bodyMedium)
                    is UiState.Success -> {
                        val list = tasksState.data
                        if (list.isEmpty()) {
                            Text(
                                text = "No reminders, tasks, or birthdays yet. Tap + or say \"Remind me to call Mom\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                list.take(8).forEach { task ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DeepNavy.copy(alpha = 0.7f))
                                            .clickable { onToggleTask(task.id, !task.completed) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                task.completed -> Icons.Default.CheckCircle
                                                task.category == TaskCategory.BIRTHDAY.name -> Icons.Default.Cake
                                                else -> Icons.Default.RadioButtonUnchecked
                                            },
                                            contentDescription = "Toggle completion",
                                            tint = if (task.completed) StatusEmerald else ElectricCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (task.completed) MutedSteel else PureIce,
                                                textDecoration = if (task.completed) TextDecoration.LineThrough else null
                                            )
                                            Text(
                                                text = "${task.category}${task.dueLabel?.let { " • $it" } ?: ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ArcBlue
                                            )
                                        }
                                        IconButton(onClick = { onDeleteTask(task.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete item", tint = MutedSteel, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Daily News Headlines
        item {
            GlassHudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Newspaper, contentDescription = "Daily News", tint = ElectricCyan)
                        Text("DAILY NEWS BRIEFING", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    }
                    IconButton(onClick = onRefreshNews, modifier = Modifier.testTag("refresh_news_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh news", tint = PureIce)
                    }
                }
                when (newsState) {
                    SmartDataState.Idle, SmartDataState.Loading -> {
                        Text("Fetching live news headlines…", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                    }
                    is SmartDataState.Error -> {
                        Text(newsState.message, style = MaterialTheme.typography.bodyMedium, color = StatusCrimson)
                    }
                    is SmartDataState.Ready -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            newsState.data.take(4).forEach { headline ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DeepNavy.copy(alpha = 0.65f))
                                        .clickable {
                                            if (headline.link.isNotBlank()) {
                                                onOpenNewsLink(headline.link, headline.source)
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = headline.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PureIce,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${headline.source} • ${headline.publishedAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedSteel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, cat, due ->
                onAddTask(title, cat, due)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (String, TaskCategory, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dueLabel by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TaskCategory.REMINDER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelNavy,
        title = { Text("New Smart Item", color = ElectricCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (English or Hindi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                )
                OutlinedTextField(
                    value = dueLabel,
                    onValueChange = { dueLabel = it },
                    label = { Text("Time / Date (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = VoidNavy
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onSave(title, selectedCategory, dueLabel.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy),
                modifier = Modifier.testTag("save_task_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedSteel) }
        }
    )
}

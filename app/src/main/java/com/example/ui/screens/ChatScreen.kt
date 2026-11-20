package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.assistant.VoiceListeningState
import com.example.data.model.ChatMessageRecord
import com.example.ui.components.AnimatedVoiceWaveform
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.PanelNavy
import com.example.ui.theme.PureIce
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.VoidNavy
import com.example.ui.viewmodel.UiState

@Composable
fun ChatScreen(
    messagesState: UiState<List<ChatMessageRecord>>,
    listeningState: VoiceListeningState,
    isThinking: Boolean,
    isSpeaking: Boolean,
    openAiConfigured: Boolean,
    onSendMessage: (String) -> Unit,
    onMicToggle: () -> Unit,
    onSpeakText: (String) -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isListening = listeningState is VoiceListeningState.Listening
    val rmsDb = (listeningState as? VoiceListeningState.Listening)?.rmsDb ?: 0f

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepNavy.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("JARVIS AI BRAIN", style = MaterialTheme.typography.headlineSmall, color = ElectricCyan)
                Text("Hindi + English • ChatGPT Powered", style = MaterialTheme.typography.labelSmall, color = MutedSteel)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPillBadge(
                    label = if (openAiConfigured) "API Ready" else "Needs Key",
                    active = openAiConfigured
                )
                IconButton(onClick = onClearChat, modifier = Modifier.testTag("clear_chat_button")) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear conversation", tint = MutedSteel)
                }
            }
        }

        if (isListening || isSpeaking) {
            AnimatedVoiceWaveform(
                active = true,
                rmsDb = if (isSpeaking) 8f else rmsDb,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Messages list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                UiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = ElectricCyan
                )
                is UiState.Error -> Text(
                    text = messagesState.message,
                    color = StatusCrimson,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Hey JARVIS Online", style = MaterialTheme.typography.headlineSmall, color = ElectricCyan)
                            Text(
                                text = "Ask anything in Hindi or English, create reminders, translate phrases, draft Facebook posts, or run phone commands.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isUser = msg.role == "USER"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 310.dp)
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomStart = if (isUser) 18.dp else 4.dp,
                                                    bottomEnd = if (isUser) 4.dp else 18.dp
                                                )
                                            )
                                            .background(if (isUser) ArcBlue.copy(alpha = 0.35f) else PanelNavy.copy(alpha = 0.9f))
                                            .border(
                                                1.dp,
                                                if (isUser) ElectricCyan.copy(alpha = 0.55f) else GlassBorder,
                                                RoundedCornerShape(18.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = msg.content,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = PureIce
                                        )
                                        if (!isUser) {
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = msg.actionSummary ?: "JARVIS VIP",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = NeonTeal
                                                )
                                                IconButton(
                                                    onClick = { onSpeakText(msg.content) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = "Speak aloud",
                                                        tint = ElectricCyan,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (isThinking) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
                                        Text("JARVIS is thinking…", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick bilingual command chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Translate to Hindi: Good morning",
                "मेरा दिन प्लान करो",
                "2500 का 18 प्रतिशत",
                "Open YouTube"
            ).forEach { prompt ->
                AssistChip(
                    onClick = { onSendMessage(prompt) },
                    label = { Text(prompt, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = PanelNavy,
                        labelColor = PureIce
                    )
                )
            }
        }

        // Input Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepNavy.copy(alpha = 0.95f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask JARVIS in Hindi or English…", color = MutedSteel) },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = PureIce,
                    unfocusedTextColor = PureIce
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).testTag("chat_input_field")
            )

            Surface(
                color = if (isListening) StatusCrimson else PanelNavy,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, ElectricCyan, CircleShape)
            ) {
                IconButton(onClick = onMicToggle, modifier = Modifier.testTag("chat_mic_button")) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isListening) PureIce else ElectricCyan
                    )
                }
            }

            Surface(
                color = ElectricCyan,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = VoidNavy
                    )
                }
            }
        }
    }
}

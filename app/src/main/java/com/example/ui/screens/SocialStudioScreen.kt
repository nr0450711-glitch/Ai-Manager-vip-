package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.SocialDraft
import com.example.data.model.SocialPlatform
import com.example.ui.components.GlassHudCard
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.PureIce
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.VoidNavy
import com.example.ui.viewmodel.UiState

@Composable
fun SocialStudioScreen(
    selectedPhotoUri: Uri?,
    draftsState: UiState<List<SocialDraft>>,
    isThinking: Boolean,
    onOpenFacebookApp: () -> Unit,
    onPickPhoto: () -> Unit,
    onCapturePhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onGenerateCaptionWithAi: (String, SocialPlatform, (String) -> Unit) -> Unit,
    onPrepareAndConfirmShare: (SocialPlatform, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var platform by remember { mutableStateOf(SocialPlatform.FACEBOOK) }
    var topicPrompt by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var targetLabel by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("FACEBOOK & SOCIAL STUDIO", style = MaterialTheme.typography.headlineMedium, color = ElectricCyan)
                    Text("Draft posts, captions, comments & share with confirmation", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                }
                OutlinedButton(
                    onClick = onOpenFacebookApp,
                    modifier = Modifier.testTag("open_facebook_button")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Facebook")
                }
            }
        }

        // Photo Selection & Camera Capture
        item {
            GlassHudCard {
                Text("PHOTO & MEDIA ATTACHMENT", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onPickPhoto,
                        modifier = Modifier.weight(1f).testTag("pick_photo_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery Picker")
                    }
                    OutlinedButton(
                        onClick = onCapturePhoto,
                        modifier = Modifier.weight(1f).testTag("capture_photo_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Camera")
                    }
                }
                if (selectedPhotoUri != null) {
                    Spacer(Modifier.height(10.dp))
                    Image(
                        painter = rememberAsyncImagePainter(selectedPhotoUri),
                        contentDescription = "Selected photo preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    TextButton(onClick = onClearPhoto) {
                        Text("Remove selected photo", color = MutedSteel)
                    }
                }
            }
        }

        // Post / Comment / Caption Composer
        item {
            GlassHudCard {
                Text("PREPARE POST OR COMMENT", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialPlatform.entries.forEach { p ->
                        FilterChip(
                            selected = platform == p,
                            onClick = { platform = p },
                            label = { Text(p.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = VoidNavy
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = topicPrompt,
                        onValueChange = { topicPrompt = it },
                        label = { Text("Topic for AI Caption / Comment") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onGenerateCaptionWithAi(topicPrompt, platform) { generated ->
                                caption = generated
                            }
                        },
                        enabled = !isThinking && topicPrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Draft")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Post Caption / Comment (Hindi or English)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("social_caption_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetLabel,
                    onValueChange = { targetLabel = it },
                    label = { Text("Audience / Page / Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Safety rule: JARVIS never auto-publishes or bypasses Facebook security. You always confirm before sharing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonTeal
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (caption.isNotBlank()) {
                            onPrepareAndConfirmShare(platform, caption, targetLabel.ifBlank { null })
                        }
                    },
                    enabled = caption.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy),
                    modifier = Modifier.fillMaxWidth().testTag("social_confirm_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Review & Share to ${platform.label}")
                }
            }
        }

        // Saved Drafts History
        item {
            Text("RECENT DRAFTS", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
        }
        val drafts = (draftsState as? UiState.Success)?.data.orEmpty()
        if (drafts.isEmpty()) {
            item {
                Text("No saved social drafts yet.", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
            }
        } else {
            items(drafts.take(6), key = { it.id }) { draft ->
                GlassHudCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(draft.platform, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                        Text(
                            text = draft.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (draft.status == "SHARED") StatusEmerald else MutedSteel
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(draft.caption, style = MaterialTheme.typography.bodyMedium, color = PureIce)
                    if (draft.hasPhoto) {
                        Text("Includes photo attachment", style = MaterialTheme.typography.labelSmall, color = NeonTeal)
                    }
                }
            }
        }
    }
}

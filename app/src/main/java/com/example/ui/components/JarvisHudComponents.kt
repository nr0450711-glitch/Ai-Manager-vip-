package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.PanelNavy
import com.example.ui.theme.PureIce
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.VoidNavy
import kotlin.math.sin

@Composable
fun GlassHudCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        PanelNavy.copy(alpha = 0.82f),
                        DeepNavy.copy(alpha = 0.90f)
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun HolographicCoreVisualizer(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    micEnabled: Boolean,
    rmsDb: Float,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "jarvis_core")
    val outerAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening || isSpeaking) 3200 else 10000, easing = LinearEasing)
        ),
        label = "outer_angle"
    )
    val innerAngle by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 2400 else 7200, easing = LinearEasing)
        ),
        label = "inner_angle"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val activeColor = when {
        !micEnabled -> StatusCrimson
        isListening -> NeonTeal
        isSpeaking -> ElectricCyan
        isThinking -> ArcBlue
        else -> ElectricCyan
    }

    Box(
        modifier = modifier
            .size(196.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onMicClick)
            .testTag("jarvis_core_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f - 8.dp.toPx()
            val dynamicBoost = if (isListening) (rmsDb / 12f) * 8.dp.toPx() else 0f

            // Subtle radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = baseRadius * pulse
                ),
                radius = baseRadius * pulse,
                center = center
            )

            // Outer segmented HUD ring
            drawArc(
                color = activeColor.copy(alpha = 0.85f),
                startAngle = outerAngle,
                sweepAngle = 110f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ArcBlue.copy(alpha = 0.7f),
                startAngle = outerAngle + 180f,
                sweepAngle = 110f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Inner counter-rotating ring
            val innerInset = 18.dp.toPx() - dynamicBoost.coerceAtMost(6.dp.toPx())
            drawArc(
                color = activeColor.copy(alpha = 0.55f),
                startAngle = innerAngle,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - innerInset * 2f,
                    size.height - innerInset * 2f
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Image(
            painter = painterResource(id = R.drawable.img_jarvis_emblem),
            contentDescription = "Tap to activate Jarvis VIP voice assistant",
            modifier = Modifier
                .size(126.dp)
                .clip(CircleShape)
                .border(1.5.dp, activeColor.copy(alpha = 0.7f), CircleShape),
            contentScale = ContentScale.Crop
        )

        Surface(
            color = VoidNavy.copy(alpha = 0.78f),
            shape = CircleShape,
            modifier = Modifier
                .size(50.dp)
                .border(1.dp, activeColor, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        !micEnabled -> Icons.Default.MicOff
                        isListening || isSpeaking -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = if (isListening) "Stop listening" else "Start voice command",
                    tint = activeColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedVoiceWaveform(
    active: Boolean,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "waveform")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        val barCount = 28
        val spacing = size.width / barCount
        val centerY = size.height / 2f
        val energy = if (active) (0.45f + (rmsDb / 14f).coerceIn(0f, 0.55f)) else 0.14f

        for (i in 0 until barCount) {
            val x = spacing * i + spacing / 2f
            val factor = kotlin.math.abs(sin(phase + i * 0.45f))
            val barHalfHeight = (size.height * 0.42f) * (0.22f + factor * energy)
            drawLine(
                color = if (active) ElectricCyan else ArcBlue.copy(alpha = 0.45f),
                start = Offset(x, centerY - barHalfHeight),
                end = Offset(x, centerY + barHalfHeight),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun StatusPillBadge(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (active) StatusEmerald else StatusCrimson
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(DeepNavy.copy(alpha = 0.85f))
            .border(1.dp, dotColor.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PureIce
        )
    }
}

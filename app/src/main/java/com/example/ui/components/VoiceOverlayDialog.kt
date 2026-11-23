package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.VoiceLoopPhase
import com.example.ui.viewmodel.VoiceOverlayState

@Composable
fun VoiceConversationOverlay(
    state: VoiceOverlayState,
    rmsLevel: Float,
    liveTranscript: String,
    autoLoopEnabled: Boolean,
    onToggleAutoLoop: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onInterruptBargeIn: () -> Unit,
    onClose: () -> Unit
) {
    if (!state.isVisible) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val animatedRms by animateFloatAsState(
            targetValue = rmsLevel,
            animationSpec = tween(90),
            label = "rms"
        )
        val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
        val idlePulse by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        val primaryColor = MaterialTheme.colorScheme.primary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Voice Session",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (state.phase) {
                                VoiceLoopPhase.LISTENING -> "Listening — speak naturally"
                                VoiceLoopPhase.THINKING -> "Thinking with Gemini..."
                                VoiceLoopPhase.SPEAKING -> "Speaking — tap orb to interrupt"
                                VoiceLoopPhase.PAUSED -> "Paused — tap play or orb to resume"
                                VoiceLoopPhase.IDLE -> "Ready"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryColor
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Auto-loop",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoLoopEnabled,
                            onCheckedChange = onToggleAutoLoop,
                            modifier = Modifier.testTag("voice_auto_loop_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Pulsing decibel-reactive orb (tapping during speaking performs barge-in!)
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .clickable {
                            when (state.phase) {
                                VoiceLoopPhase.SPEAKING -> onInterruptBargeIn()
                                VoiceLoopPhase.PAUSED -> onTogglePause()
                                else -> onInterruptBargeIn()
                            }
                        }
                        .testTag("voice_orb_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = size.minDimension * 0.28f
                        val rmsBoost = when (state.phase) {
                            VoiceLoopPhase.LISTENING -> animatedRms * 46f
                            VoiceLoopPhase.SPEAKING -> (idlePulse - 0.9f) * 140f
                            VoiceLoopPhase.THINKING -> (idlePulse - 0.9f) * 80f
                            else -> 0f
                        }
                        val outerRadius = (baseRadius * idlePulse + rmsBoost * 1.4f)
                            .coerceAtMost(size.minDimension * 0.48f)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.26f),
                                    tertiaryColor.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = outerRadius
                            ),
                            radius = outerRadius
                        )
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(primaryColor, tertiaryColor)
                            ),
                            radius = baseRadius + rmsBoost * 0.4f
                        )
                    }
                    Icon(
                        imageVector = when (state.phase) {
                            VoiceLoopPhase.SPEAKING -> Icons.Outlined.Stop
                            VoiceLoopPhase.PAUSED -> Icons.Outlined.PlayArrow
                            else -> Icons.Outlined.Mic
                        },
                        contentDescription = "Voice orb",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Live transcript & reply preview cards
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val shownTranscript = if (state.phase == VoiceLoopPhase.LISTENING && liveTranscript.isNotBlank()) {
                        liveTranscript
                    } else state.transcript

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "YOUR TRANSCRIPT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = shownTranscript.ifBlank { "Speak when ready..." },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (state.replyPreview.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "UNKNOWN AI REPLY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = primaryColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.replyPreview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .size(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (state.phase == VoiceLoopPhase.PAUSED) {
                                Icons.Outlined.PlayArrow
                            } else Icons.Outlined.Pause,
                            contentDescription = if (state.phase == VoiceLoopPhase.PAUSED) "Resume" else "Pause"
                        )
                    }

                    if (state.phase == VoiceLoopPhase.SPEAKING) {
                        FilledIconButton(
                            onClick = onInterruptBargeIn,
                            modifier = Modifier
                                .size(56.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("voice_barge_in_button")
                        ) {
                            Icon(Icons.Outlined.Mic, contentDescription = "Interrupt and speak")
                        }
                    }

                    FilledIconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("voice_close_button")
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "End voice session")
                    }
                }
            }
        }
    }
}

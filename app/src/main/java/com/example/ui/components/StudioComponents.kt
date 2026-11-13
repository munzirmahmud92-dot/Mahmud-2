package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.PlaybackState
import com.example.core.StudioStrings
import com.example.data.local.VoiceProfileEntity
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun StudioGradientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.3f
    val gradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF05070E),
                Color(0xFF0A0F24),
                Color(0xFF070A16)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.background,
                Color(0xFFEBEFF9)
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreenScaffold(
    title: String,
    strings: StudioStrings,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    playbackState: PlaybackState = PlaybackState(),
    onMiniPlayPause: (() -> Unit)? = null,
    onMiniStop: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    StudioGradientBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("nav_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.back
                                )
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (playbackState.activeFilePath != null && onMiniPlayPause != null && onMiniStop != null) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        strings = strings,
                        onPlayPause = onMiniPlayPause,
                        onStop = onMiniStop
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                content(
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                )
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    amplitudes: List<Float> = emptyList(),
    progress: Float = 0f,
    barCount: Int = 36
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val height = size.height
        val slotWidth = totalWidth / barCount
        val barWidth = (slotWidth * 0.56f).coerceAtLeast(3f)

        for (i in 0 until barCount) {
            val fraction = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            val sampleAmp = if (amplitudes.isNotEmpty()) {
                val idx = ((fraction * (amplitudes.size - 1)).toInt()).coerceIn(0, amplitudes.lastIndex)
                amplitudes[idx].coerceIn(0.08f, 1f)
            } else if (isActive) {
                val wave = 0.38f + 0.52f * abs(sin(phase + i * 0.42f))
                wave.coerceIn(0.12f, 1f)
            } else {
                val idleWave = 0.14f + 0.16f * abs(sin(i * 0.65f))
                idleWave
            }

            val barHeight = (height * sampleAmp).coerceAtLeast(6f)
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            val y = (height - barHeight) / 2f

            val isPlayedOrActive = isActive || (progress > 0f && fraction <= progress)
            val barBrush = if (isPlayedOrActive) {
                Brush.verticalGradient(
                    colors = listOf(tertiary, secondary, primary)
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(inactiveColor, inactiveColor)
                )
            }

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StudioGradientCard(
    modifier: Modifier = Modifier,
    accentGlow: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val borderBrush = if (accentGlow) {
        Brush.linearGradient(listOf(ElectricViolet.copy(alpha = 0.75f), CobaltBlue, NeonCyan.copy(alpha = 0.65f)))
    } else {
        Brush.linearGradient(listOf(scheme.outline.copy(alpha = 0.55f), scheme.outline.copy(alpha = 0.25f)))
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, borderBrush),
        tonalElevation = 4.dp
    ) {
        content()
    }
}

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    strings: StudioStrings,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playbackState.activeTitle.ifBlank { "Studio Playback" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatDurationMs(playbackState.positionMs)} / ${formatDurationMs(playbackState.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) strings.pause else strings.play
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.testTag("mini_player_stop")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = strings.stop
                    )
                }
            }
        }
    }
}

@Composable
fun PinEntryDialog(
    title: String,
    strings: StudioStrings,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirmPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.enterPinPrompt, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text("4-Digit PIN") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_input_field")
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPin(pin) },
                enabled = pin.length == 4,
                modifier = Modifier.testTag("pin_confirm_button")
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun ConsentRecordDialog(
    profile: VoiceProfileEntity,
    strings: StudioStrings,
    onDismiss: () -> Unit
) {
    val dateStr = remember(profile.consentTimestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(profile.consentTimestamp))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = { Text(strings.viewConsentRecord) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${strings.profileNameLabel}: ${profile.profileName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${strings.speakerNameLabel}: ${profile.speakerName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Timestamp: $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = profile.consentStatement,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(strings.back)
            }
        }
    )
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

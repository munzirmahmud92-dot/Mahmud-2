package com.example.ui.screens.recorder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.RecordingStatus
import com.example.core.StudioStrings
import com.example.data.local.AudioFormat
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@Composable
fun RecordingStudioScreen(
    viewModel: RecordingStudioViewModel,
    strings: StudioStrings,
    onBack: () -> Unit,
    onSendToVoiceClone: (filePath: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val elapsedMs by viewModel.elapsedMs.collectAsStateWithLifecycle()
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val recordingTitle by viewModel.recordingTitle.collectAsStateWithLifecycle()
    val finishedClip by viewModel.finishedClip.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val playbackState by viewModel.player.state.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.startRecording()
    }

    StudioScreenScaffold(
        title = strings.recorderTitle,
        strings = strings,
        onBack = onBack,
        playbackState = playbackState,
        onMiniPlayPause = {
            playbackState.activeFilePath?.let { viewModel.player.togglePlayPause(it, playbackState.activeTitle) }
        },
        onMiniStop = { viewModel.player.stop() }
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!hasMicPermission) {
                item {
                    StudioGradientCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(strings.micPermissionRequired, style = MaterialTheme.typography.bodyLarge)
                            Button(
                                onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier.testTag("recorder_grant_mic_button")
                            ) {
                                Text(strings.grantMicPermission)
                            }
                        }
                    }
                }
            }

            item {
                StudioGradientCard(accentGlow = status == RecordingStatus.RECORDING) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StatusPillBadge(
                            text = when (status) {
                                RecordingStatus.IDLE -> strings.tapToRecord
                                RecordingStatus.RECORDING -> strings.recordingActive
                                RecordingStatus.PAUSED -> strings.recordingPaused
                            },
                            color = when (status) {
                                RecordingStatus.IDLE -> NeonCyan
                                RecordingStatus.RECORDING -> CrimsonError
                                RecordingStatus.PAUSED -> AmberWarn
                            }
                        )

                        Text(
                            text = formatDurationMs(elapsedMs),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        AudioWaveformVisualizer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                            isActive = status == RecordingStatus.RECORDING,
                            amplitudes = waveform,
                            barCount = 44
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedFormat == AudioFormat.WAV,
                                onClick = { if (status == RecordingStatus.IDLE) viewModel.setSelectedFormat(AudioFormat.WAV) },
                                label = { Text("WAV (Lossless)") }
                            )
                            FilterChip(
                                selected = selectedFormat == AudioFormat.MP3,
                                onClick = { if (status == RecordingStatus.IDLE) viewModel.setSelectedFormat(AudioFormat.MP3) },
                                label = { Text("MP3 (Compressed)") }
                            )
                        }

                        OutlinedTextField(
                            value = recordingTitle,
                            onValueChange = viewModel::updateTitle,
                            label = { Text("Recording Title (Optional)") },
                            singleLine = true,
                            enabled = status != RecordingStatus.IDLE || finishedClip == null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (status) {
                                RecordingStatus.IDLE -> {
                                    Button(
                                        onClick = {
                                            if (hasMicPermission) {
                                                viewModel.startRecording()
                                            } else {
                                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("recorder_start_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                                    ) {
                                        if (isSaving) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                        } else {
                                            Icon(Icons.Default.Mic, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.record, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }

                                RecordingStatus.RECORDING -> {
                                    OutlinedButton(
                                        onClick = viewModel::pauseRecording,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("recorder_pause_button")
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.pause)
                                    }
                                    Button(
                                        onClick = viewModel::stopAndSave,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("recorder_stop_save_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.save)
                                    }
                                }

                                RecordingStatus.PAUSED -> {
                                    Button(
                                        onClick = viewModel::resumeRecording,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("recorder_resume_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.resume)
                                    }
                                    Button(
                                        onClick = viewModel::stopAndSave,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("recorder_stop_save_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldOnline)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.save)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = EmeraldOnline.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            color = EmeraldOnline,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            finishedClip?.let { clip ->
                val isPlaying = playbackState.activeFilePath == clip.filePath && playbackState.isPlaying
                item {
                    StudioGradientCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(clip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${clip.format} • ${formatDurationMs(clip.durationMs)} • ${formatFileSize(clip.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = viewModel::togglePreview,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPlaying) strings.pause else strings.preview)
                                }
                                OutlinedButton(
                                    onClick = { onSendToVoiceClone(clip.filePath, clip.title) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.sendToVoiceClone)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

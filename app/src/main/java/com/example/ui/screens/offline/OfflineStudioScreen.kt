package com.example.ui.screens.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.TtsEngineState
import com.example.core.StudioStrings
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@Composable
fun OfflineStudioScreen(
    viewModel: OfflineStudioViewModel,
    strings: StudioStrings,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsStateWithLifecycle()
    val selectedLanguageTag by viewModel.selectedLanguageTag.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val playbackState by viewModel.player.state.collectAsStateWithLifecycle()

    val offlineVoiceCount = voices.count { it.isOfflineReady }

    StudioScreenScaffold(
        title = strings.offlineTitle,
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
            // Offline Status & Honest Voice Cloning Disclaimer
            item {
                StudioGradientCard(accentGlow = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPillBadge(
                                text = when (engineState) {
                                    TtsEngineState.READY -> "${strings.offlineReadyBadge} ($offlineVoiceCount)"
                                    TtsEngineState.INITIALIZING -> "Initializing TTS..."
                                    TtsEngineState.UNAVAILABLE -> "TTS Unavailable"
                                },
                                color = if (engineState == TtsEngineState.READY) EmeraldOnline else AmberWarn
                            )
                            StatusPillBadge(
                                text = if (isOnline) "Net Available (Optional)" else strings.offlineStatus,
                                color = if (isOnline) NeonCyan else EmeraldOnline
                            )
                        }

                        Surface(
                            color = CobaltBlue.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.45f)),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan)
                                Column {
                                    Text(strings.offlineDisclaimerTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(strings.offlineDisclaimerBody, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Text Input & Quick Samples
            item {
                StudioGradientCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = viewModel::updateText,
                            label = { Text(strings.ttsInputLabel) },
                            placeholder = { Text(strings.ttsInputPlaceholder) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_text_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedLanguageTag.startsWith("bn"),
                                onClick = {
                                    viewModel.selectLanguageTag("bn-BD")
                                    if (inputText.isBlank()) {
                                        viewModel.updateText("ইন্টারনেট ছাড়াই বাংলা অফলাইন ভয়েস স্টুডিও সচল রয়েছে।")
                                    }
                                },
                                label = { Text("বাংলা (bn-BD)") }
                            )
                            FilterChip(
                                selected = selectedLanguageTag.startsWith("en"),
                                onClick = {
                                    viewModel.selectLanguageTag("en-US")
                                    if (inputText.isBlank()) {
                                        viewModel.updateText("Offline Android TextToSpeech is ready without an internet connection.")
                                    }
                                },
                                label = { Text("English (en-US)") }
                            )
                        }

                        AudioWaveformVisualizer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            isActive = isSpeaking
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${strings.speedLabel}: ${String.format("%.2fx", speed)}", style = MaterialTheme.typography.bodyMedium)
                            Text("${strings.pitchLabel}: ${String.format("%.2fx", pitch)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Slider(value = speed, onValueChange = viewModel::updateSpeed, valueRange = 0.5f..2.0f)
                        Slider(value = pitch, onValueChange = viewModel::updatePitch, valueRange = 0.5f..1.5f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isSpeaking) viewModel.stopSpeaking() else viewModel.speakNow()
                                },
                                enabled = engineState == TtsEngineState.READY,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("offline_speak_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isSpeaking) strings.stop else strings.speakOffline)
                            }

                            OutlinedButton(
                                onClick = viewModel::synthesizeAndSave,
                                enabled = engineState == TtsEngineState.READY && !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("offline_save_button")
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.saveOfflineAudio)
                                }
                            }
                        }
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            // Installed Voices Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.installedVoices,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(viewModel.offlineTtsEngine.createInstallVoiceDataIntent())
                            }
                        },
                        modifier = Modifier.testTag("offline_install_voices_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.installVoiceData)
                    }
                }
            }

            if (voices.isEmpty()) {
                item {
                    StudioGradientCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = AmberWarn)
                            Text(strings.noOfflineVoicesFound, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(voices.take(25), key = { it.name }) { voice ->
                    val selected = selectedVoiceName == voice.name
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { viewModel.selectVoice(voice) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            ElectricViolet.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) NeonCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(voice.displayLanguage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${voice.localeTag} • ${voice.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPillBadge(
                                text = if (voice.isOfflineReady) strings.offlineReadyBadge else strings.requiresNetworkBadge,
                                color = if (voice.isOfflineReady) EmeraldOnline else AmberWarn
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

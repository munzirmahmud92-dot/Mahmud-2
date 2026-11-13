package com.example.ui.screens.tts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppLanguage
import com.example.core.StudioStrings
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.remote.GeminiTtsRepository
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextToSpeechScreen(
    viewModel: TextToSpeechViewModel,
    strings: StudioStrings,
    onBack: () -> Unit,
    onOpenOfflineStudio: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onExportClip: (AudioClipEntity, AudioFormat) -> Unit,
    onShareClip: (AudioClipEntity, AudioFormat) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val usage by viewModel.todayUsage.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguageCode.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val isAiAssisting by viewModel.isAiAssisting.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.player.state.collectAsStateWithLifecycle()

    val isBengaliUi = settings.language == AppLanguage.BENGALI
    val isBlockedOffline = settings.offlineOnlyMode || !isOnline

    StudioScreenScaffold(
        title = strings.ttsTitle,
        strings = strings,
        onBack = onBack,
        playbackState = playbackState,
        onMiniPlayPause = {
            playbackState.activeFilePath?.let { path ->
                viewModel.player.togglePlayPause(path, playbackState.activeTitle)
            }
        },
        onMiniStop = { viewModel.stopPlayback() }
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Transparency & Offline Guard
            if (isBlockedOffline) {
                item {
                    StudioGradientCard(accentGlow = false) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = AmberWarn)
                                Text(
                                    text = strings.offlineModeBlockedTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AmberWarn,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(strings.offlineModeBlockedMsg, style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(
                                onClick = onOpenOfflineStudio,
                                modifier = Modifier.testTag("tts_go_offline_button")
                            ) {
                                Text(strings.goToOfflineStudio)
                            }
                        }
                    }
                }
            }

            // Standard TTS Disclaimer
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = CobaltBlue.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan)
                        Text(
                            text = strings.standardTtsDisclaimer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Text Input Card
            item {
                StudioGradientCard(accentGlow = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = strings.ttsInputLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = viewModel::updateText,
                            placeholder = { Text(strings.ttsInputPlaceholder) },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_text_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateText(
                                        "আসসালামু আলাইকুম! মনজির ভয়েস ক্রোনার স্টুডিওতে আপনাকে স্বাগতম। আজ আমরা বাংলায় পরিষ্কার এআই কণ্ঠস্বর তৈরি করছি।"
                                    )
                                    viewModel.selectLanguage("bn-IN")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tts_sample_bn_button")
                            ) {
                                Text(strings.sampleBengali)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateText(
                                        "Welcome to Munzir Voice Cloner Studio. This studio speech is synthesized with Gemini Neural TTS."
                                    )
                                    viewModel.selectLanguage("en-US")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tts_sample_en_button")
                            ) {
                                Text(strings.sampleEnglish)
                            }
                        }

                        // Inline Gemini AI Assist Controls
                        Text(
                            text = strings.aiAssistInlineLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.runInlineAiAssist("POLISH_TTS") },
                                enabled = !isAiAssisting && !isBlockedOffline,
                                modifier = Modifier.testTag("tts_ai_polish_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isAiAssisting) "..." else strings.aiPolishBtn)
                            }
                            OutlinedButton(
                                onClick = { viewModel.runInlineAiAssist("TRANSLATE_TO_BN") },
                                enabled = !isAiAssisting && !isBlockedOffline,
                                modifier = Modifier.testTag("tts_ai_bn_button")
                            ) {
                                Text("→ বাংলা")
                            }
                            OutlinedButton(
                                onClick = { viewModel.runInlineAiAssist("TRANSLATE_TO_EN") },
                                enabled = !isAiAssisting && !isBlockedOffline,
                                modifier = Modifier.testTag("tts_ai_en_button")
                            ) {
                                Text("→ EN")
                            }
                            OutlinedButton(
                                onClick = onOpenAiStudio,
                                modifier = Modifier.testTag("tts_open_ai_studio_button")
                            ) {
                                Text(strings.aiStudioTitle)
                            }
                        }
                    }
                }
            }

            // Language & Voice Selector
            item {
                StudioGradientCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = strings.selectLanguage,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GeminiTtsRepository.SUPPORTED_LANGUAGES.forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguage == lang.code,
                                    onClick = { viewModel.selectLanguage(lang.code) },
                                    label = { Text(if (isBengaliUi) lang.labelBn else lang.labelEn) }
                                )
                            }
                        }

                        Text(
                            text = strings.selectVoice,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(GeminiTtsRepository.AVAILABLE_VOICES) { preset ->
                                val active = selectedVoice == preset.id
                                Surface(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { viewModel.selectVoice(preset.id) }
                                        .testTag("tts_voice_${preset.id}"),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (active) {
                                        ElectricViolet.copy(alpha = 0.28f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (active) NeonCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = preset.id,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${preset.style} • ${preset.gender}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Speed & Pitch Controls
            item {
                StudioGradientCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text(strings.speedLabel, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(String.format("%.2fx", speed), style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        }
                        Slider(
                            value = speed,
                            onValueChange = viewModel::updateSpeed,
                            valueRange = 0.6f..1.8f,
                            modifier = Modifier.testTag("tts_speed_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(18.dp))
                                Text(strings.pitchLabel, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(String.format("%.2fx", pitch), style = MaterialTheme.typography.labelLarge, color = ElectricViolet)
                        }
                        Slider(
                            value = pitch,
                            onValueChange = viewModel::updatePitch,
                            valueRange = 0.7f..1.4f,
                            modifier = Modifier.testTag("tts_pitch_slider")
                        )
                    }
                }
            }

            // Generate Speech CTA
            item {
                val isGenerating = uiState is TtsUiState.Generating
                Button(
                    onClick = viewModel::generateSpeech,
                    enabled = !isGenerating && !isBlockedOffline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("tts_generate_button"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = Color.White
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.generatingSpeech, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.generateSpeech, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Error Banner
            val errorState = uiState as? TtsUiState.Error
            if (errorState != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = errorState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = viewModel::generateSpeech) {
                                Text(strings.retry)
                            }
                        }
                    }
                }
            }

            // Generated Audio Player & Export Card
            val readyState = uiState as? TtsUiState.Ready
            if (readyState != null) {
                item {
                    val clip = readyState.clip
                    val isThisPlaying = playbackState.activeFilePath == clip.filePath && playbackState.isPlaying
                    val progress = if (playbackState.activeFilePath == clip.filePath && playbackState.durationMs > 0L) {
                        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    StudioGradientCard(accentGlow = true) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = clip.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${clip.format} • ${formatDurationMs(clip.durationMs)} • ${formatFileSize(clip.fileSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPillBadge(text = clip.voiceName, color = EmeraldOnline)
                            }

                            AudioWaveformVisualizer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                isActive = isThisPlaying,
                                amplitudes = readyState.waveformPeaks,
                                progress = progress,
                                barCount = 40
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.togglePlayClip(clip) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tts_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isThisPlaying) strings.pause else strings.play)
                                }
                                IconButton(
                                    onClick = viewModel::stopPlayback,
                                    modifier = Modifier.testTag("tts_stop_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = strings.stop)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onExportClip(clip, AudioFormat.WAV) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tts_export_wav_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportWav)
                                }
                                OutlinedButton(
                                    onClick = { onExportClip(clip, AudioFormat.MP3) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tts_export_mp3_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportMp3)
                                }
                                IconButton(
                                    onClick = { onShareClip(clip, AudioFormat.MP3) },
                                    modifier = Modifier.testTag("tts_share_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = strings.share)
                                }
                            }
                        }
                    }
                }
            }

            // API Usage & Quota Card
            item {
                val reqCount = usage?.requestCount ?: 0
                val chars = usage?.charactersProcessed ?: 0
                val quotaFraction = (reqCount.toFloat() / GeminiTtsRepository.DAILY_SOFT_QUOTA_REQUESTS.toFloat()).coerceIn(0f, 1f)
                StudioGradientCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text(strings.apiUsageTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "$reqCount / ${GeminiTtsRepository.DAILY_SOFT_QUOTA_REQUESTS} • $chars chars",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonCyan
                            )
                        }
                        LinearProgressIndicator(
                            progress = { quotaFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = NeonCyan
                        )
                        Text(
                            text = strings.apiQuotaNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

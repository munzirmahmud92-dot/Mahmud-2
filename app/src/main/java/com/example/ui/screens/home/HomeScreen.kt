package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackState
import com.example.core.AppLanguage
import com.example.core.StudioStrings
import com.example.data.local.ApiUsageEntity
import com.example.data.preferences.StudioSettings
import com.example.data.remote.GeminiTtsRepository
import com.example.ui.components.AnimatedBrandTitle
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientBackdrop
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@Composable
fun HomeScreen(
    strings: StudioStrings,
    settings: StudioSettings,
    isNetworkOnline: Boolean,
    todayUsage: ApiUsageEntity?,
    profileCount: Int,
    clipCount: Int,
    playbackState: PlaybackState,
    onToggleLanguage: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onOpenVoiceClone: () -> Unit,
    onOpenOfflineStudio: () -> Unit,
    onOpenVoiceProfiles: () -> Unit,
    onOpenTextToSpeech: () -> Unit,
    onOpenAudioLibrary: () -> Unit,
    onOpenRecordingStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onMiniPlayPause: () -> Unit,
    onMiniStop: () -> Unit
) {
    val statusText = when {
        settings.offlineOnlyMode -> strings.forcedOfflineStatus
        isNetworkOnline -> strings.onlineStatus
        else -> strings.offlineStatus
    }
    val statusColor = when {
        settings.offlineOnlyMode -> AmberWarn
        isNetworkOnline -> EmeraldOnline
        else -> AmberWarn
    }

    StudioGradientBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (playbackState.activeFilePath != null) {
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
                    .padding(innerPadding)
                    .statusBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Brand Top Bar
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AnimatedBrandTitle(
                                brandBengali = strings.brandBengali,
                                brandEnglish = strings.brandEnglish,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusPillBadge(text = statusText, color = statusColor)
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(onClick = onToggleLanguage)
                                        .testTag("home_language_toggle"),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = "Switch Language",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = if (settings.language == AppLanguage.BENGALI) "EN" else "বাংলা",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Futuristic Hero Studio Card
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            border = BorderStroke(
                                1.dp,
                                Brush.linearGradient(listOf(ElectricViolet, CobaltBlue, NeonCyan))
                            ),
                            tonalElevation = 6.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_studio_hero),
                                    contentDescription = strings.studioSubtitle,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xBB05070E),
                                                    Color(0x880A0E1E),
                                                    Color(0xEE05070E)
                                                )
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = strings.homeGreeting,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val reqCount = todayUsage?.requestCount ?: 0
                                        StatusPillBadge(
                                            text = "$reqCount/${GeminiTtsRepository.DAILY_SOFT_QUOTA_REQUESTS} ${strings.requestsToday}",
                                            color = NeonCyan
                                        )
                                    }

                                    AudioWaveformVisualizer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        isActive = playbackState.isPlaying || true,
                                        barCount = 40
                                    )

                                    Text(
                                        text = strings.studioSubtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFD5E0FA),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Gemini AI Script & Voice Coach (Featured AI Hub)
                    item {
                        StudioDashboardModuleCard(
                            title = strings.menuAiStudio,
                            subtitle = strings.menuAiStudioDesc,
                            icon = Icons.Default.AutoAwesome,
                            accentColors = listOf(NeonCyan, ElectricViolet),
                            badgeText = "AI • GEMINI 3.8",
                            testTag = "home_card_ai_studio",
                            onClick = onOpenAiStudio
                        )
                    }

                    // 1. Online Voice Clone (Featured)
                    item {
                        StudioDashboardModuleCard(
                            title = strings.menuOnlineVoiceClone,
                            subtitle = strings.menuOnlineVoiceCloneDesc,
                            icon = Icons.Default.RecordVoiceOver,
                            accentColors = listOf(ElectricViolet, CobaltBlue),
                            badgeText = "1",
                            testTag = "home_card_online_voice_clone",
                            onClick = onOpenVoiceClone
                        )
                    }

                    // 2 & 3 Row: Offline Voice Studio + My Voice Profiles
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudioCompactTile(
                                modifier = Modifier.weight(1f),
                                title = strings.menuOfflineStudio,
                                subtitle = strings.menuOfflineStudioDesc,
                                icon = Icons.Default.VolumeOff,
                                accentColor = NeonCyan,
                                badgeText = "2 • OFF",
                                testTag = "home_card_offline_studio",
                                onClick = onOpenOfflineStudio
                            )
                            StudioCompactTile(
                                modifier = Modifier.weight(1f),
                                title = strings.menuVoiceProfiles,
                                subtitle = strings.menuVoiceProfilesDesc,
                                icon = Icons.Default.SpatialAudioOff,
                                accentColor = ElectricViolet,
                                badgeText = "3 • $profileCount",
                                testTag = "home_card_voice_profiles",
                                onClick = onOpenVoiceProfiles
                            )
                        }
                    }

                    // 4. Text to Speech (Gemini Neural TTS)
                    item {
                        StudioDashboardModuleCard(
                            title = strings.menuTextToSpeech,
                            subtitle = strings.menuTextToSpeechDesc,
                            icon = Icons.Default.GraphicEq,
                            accentColors = listOf(CobaltBlue, NeonCyan),
                            badgeText = "4 • GEMINI TTS",
                            testTag = "home_card_text_to_speech",
                            onClick = onOpenTextToSpeech
                        )
                    }

                    // 5 & 6 Row: Audio Library + Recording Studio
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudioCompactTile(
                                modifier = Modifier.weight(1f),
                                title = strings.menuAudioLibrary,
                                subtitle = strings.menuAudioLibraryDesc,
                                icon = Icons.Default.FolderOpen,
                                accentColor = CobaltBlue,
                                badgeText = "5 • $clipCount",
                                testTag = "home_card_audio_library",
                                onClick = onOpenAudioLibrary
                            )
                            StudioCompactTile(
                                modifier = Modifier.weight(1f),
                                title = strings.menuRecordingStudio,
                                subtitle = strings.menuRecordingStudioDesc,
                                icon = Icons.Default.Mic,
                                accentColor = EmeraldOnline,
                                badgeText = "6 • REC",
                                testTag = "home_card_recording_studio",
                                onClick = onOpenRecordingStudio
                            )
                        }
                    }

                    // 7. Settings
                    item {
                        StudioDashboardModuleCard(
                            title = strings.menuSettings,
                            subtitle = strings.menuSettingsDesc,
                            icon = Icons.Default.Settings,
                            accentColors = listOf(Color(0xFF475569), CobaltBlue),
                            badgeText = "7",
                            testTag = "home_card_settings",
                            onClick = onOpenSettings
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioDashboardModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColors: List<Color>,
    badgeText: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColors.first().copy(alpha = 0.45f)),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(accentColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = accentColors.first().copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColors.last(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StudioCompactTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(152.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

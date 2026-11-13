package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppLanguage
import com.example.core.StudioStrings
import com.example.data.local.AudioFormat
import com.example.data.preferences.AudioQuality
import com.example.ui.components.PinEntryDialog
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.components.formatFileSize
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    strings: StudioStrings,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var cloningUrlDraft by remember(settings.cloningApiBaseUrl) {
        mutableStateOf(settings.cloningApiBaseUrl)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
    }

    StudioScreenScaffold(
        title = strings.settingsTitle,
        strings = strings,
        onBack = onBack
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = EmeraldOnline.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldOnline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            // Interface Language & Theme
            item {
                StudioGradientCard(accentGlow = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(strings.interfaceLanguage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.language == AppLanguage.BENGALI,
                                onClick = { viewModel.setLanguage(AppLanguage.BENGALI) },
                                label = { Text("বাংলা (Bengali)") },
                                modifier = Modifier.testTag("settings_lang_bn")
                            )
                            FilterChip(
                                selected = settings.language == AppLanguage.ENGLISH,
                                onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                                label = { Text("English") },
                                modifier = Modifier.testTag("settings_lang_en")
                            )
                        }

                        Text(strings.themeMode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.themeMode == ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                label = { Text(strings.themeDark) },
                                modifier = Modifier.testTag("settings_theme_dark")
                            )
                            FilterChip(
                                selected = settings.themeMode == ThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                label = { Text(strings.themeLight) },
                                modifier = Modifier.testTag("settings_theme_light")
                            )
                            FilterChip(
                                selected = settings.themeMode == ThemeMode.SYSTEM,
                                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                label = { Text(strings.themeSystem) }
                            )
                        }
                    }
                }
            }

            // Audio Format, Quality & Connectivity Mode
            item {
                StudioGradientCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(strings.defaultAudioFormat, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.defaultFormat == AudioFormat.WAV,
                                onClick = { viewModel.setDefaultFormat(AudioFormat.WAV) },
                                label = { Text("WAV (PCM)") },
                                modifier = Modifier.testTag("settings_format_wav")
                            )
                            FilterChip(
                                selected = settings.defaultFormat == AudioFormat.MP3,
                                onClick = { viewModel.setDefaultFormat(AudioFormat.MP3) },
                                label = { Text("MP3") },
                                modifier = Modifier.testTag("settings_format_mp3")
                            )
                        }

                        Text(strings.audioQuality, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.audioQuality == AudioQuality.STANDARD_24K,
                                onClick = { viewModel.setAudioQuality(AudioQuality.STANDARD_24K) },
                                label = { Text(strings.qualityStandard) }
                            )
                            FilterChip(
                                selected = settings.audioQuality == AudioQuality.STUDIO_44K,
                                onClick = { viewModel.setAudioQuality(AudioQuality.STUDIO_44K) },
                                label = { Text(strings.qualityHigh) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.onlineOfflineMode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(strings.offlineOnlyToggle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.offlineOnlyMode,
                                onCheckedChange = viewModel::setOfflineOnlyMode,
                                modifier = Modifier.testTag("settings_offline_switch")
                            )
                        }
                    }
                }
            }

            // Storage Management
            item {
                StudioGradientCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = NeonCyan)
                            Text(strings.storageManagement, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${strings.totalStorageUsed}: ${formatFileSize(storageStats.totalBytes)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Audio Clips: ${formatFileSize(storageStats.audioBytes)} • Voice Samples: ${formatFileSize(storageStats.samplesBytes)} • Cache: ${formatFileSize(storageStats.cacheBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::clearExportCache,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_clear_cache_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.clearCache)
                            }
                            OutlinedButton(
                                onClick = viewModel::clearGeneratedTtsHistory,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear TTS History")
                            }
                        }
                    }
                }
            }

            // Privacy Settings & Voice Replication Backend URL
            item {
                StudioGradientCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan)
                            Text(strings.privacySettings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.requireConsentToggle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.requireConsentBeforeClone,
                                onCheckedChange = viewModel::setRequireConsent
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.localOnlyStorageToggle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.localOnlyPrivacyMode,
                                onCheckedChange = viewModel::setLocalOnlyPrivacy
                            )
                        }

                        if (settings.hasPrivacyPin) {
                            OutlinedButton(
                                onClick = { viewModel.setPrivacyPin(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.removePrivacyPin)
                            }
                        } else {
                            Button(
                                onClick = { showPinSetupDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_set_pin_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.setPrivacyPin)
                            }
                        }

                        Text(strings.replicationServerTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = cloningUrlDraft,
                            onValueChange = { cloningUrlDraft = it },
                            placeholder = { Text(strings.replicationServerHint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.setCloningApiUrl(cloningUrlDraft) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.save)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    if (showPinSetupDialog) {
        PinEntryDialog(
            title = strings.setPrivacyPin,
            strings = strings,
            onDismiss = { showPinSetupDialog = false },
            onConfirmPin = { newPin ->
                viewModel.setPrivacyPin(newPin)
                showPinSetupDialog = false
            }
        )
    }
}

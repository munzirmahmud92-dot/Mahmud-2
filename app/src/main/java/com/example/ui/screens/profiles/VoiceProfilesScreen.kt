package com.example.ui.screens.profiles

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.StudioAudioPlayer
import com.example.core.StudioStrings
import com.example.data.local.VoiceProfileEntity
import com.example.data.preferences.StudioSettings
import com.example.ui.components.ConsentRecordDialog
import com.example.ui.components.PinEntryDialog
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@Composable
fun VoiceProfilesScreen(
    viewModel: VoiceProfilesViewModel,
    player: StudioAudioPlayer,
    settings: StudioSettings,
    strings: StudioStrings,
    onBack: () -> Unit,
    onCreateProfile: () -> Unit,
    onUseProfile: (VoiceProfileEntity) -> Unit
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val unlockedIds by viewModel.unlockedProfileIds.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val playbackState by player.state.collectAsStateWithLifecycle()

    var renamingProfile by remember { mutableStateOf<VoiceProfileEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var viewingConsent by remember { mutableStateOf<VoiceProfileEntity?>(null) }
    var pendingPinAction by remember { mutableStateOf<Pair<VoiceProfileEntity, () -> Unit>?>(null) }

    fun runGuardedAction(profile: VoiceProfileEntity, action: () -> Unit) {
        val isLocked = profile.isProtected && settings.hasPrivacyPin && !unlockedIds.contains(profile.id)
        if (isLocked) {
            viewModel.clearPinError()
            pendingPinAction = profile to action
        } else {
            action()
        }
    }

    StudioScreenScaffold(
        title = strings.profilesTitle,
        strings = strings,
        onBack = onBack,
        actions = {
            IconButton(
                onClick = onCreateProfile,
                modifier = Modifier.testTag("profiles_add_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.recordOwnVoice)
            }
        },
        playbackState = playbackState,
        onMiniPlayPause = {
            playbackState.activeFilePath?.let { player.togglePlayPause(it, playbackState.activeTitle) }
        },
        onMiniStop = { player.stop() }
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (profiles.isEmpty()) {
                item {
                    StudioGradientCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SpatialAudioOff,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(strings.profilesEmpty, style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = onCreateProfile) {
                                Text(strings.menuOnlineVoiceClone)
                            }
                        }
                    }
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    val isThisPlaying = playbackState.activeFilePath == profile.sampleFilePath && playbackState.isPlaying
                    val isLocked = profile.isProtected && settings.hasPrivacyPin && !unlockedIds.contains(profile.id)

                    StudioGradientCard(accentGlow = profile.consentGranted) {
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.profileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${profile.speakerName} • ${profile.languageCode} • ${formatDurationMs(profile.durationMs)} • ${formatFileSize(profile.fileSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (profile.isProtected) {
                                    StatusPillBadge(
                                        text = if (isLocked) "PIN Locked" else strings.protectedBadge,
                                        color = if (isLocked) AmberWarn else ElectricViolet
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (profile.consentGranted) {
                                    StatusPillBadge(text = strings.consentVerified, color = EmeraldOnline)
                                }
                                StatusPillBadge(text = profile.cloningStatus, color = NeonCyan)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        runGuardedAction(profile) { onUseProfile(profile) }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_use_${profile.id}")
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.useVoiceProfile)
                                }

                                OutlinedButton(
                                    onClick = {
                                        runGuardedAction(profile) {
                                            player.togglePlayPause(profile.sampleFilePath, profile.profileName)
                                        }
                                    },
                                    modifier = Modifier.testTag("profile_preview_${profile.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isThisPlaying) strings.pause else strings.preview)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        runGuardedAction(profile) { viewingConsent = profile }
                                    },
                                    modifier = Modifier.testTag("profile_consent_${profile.id}")
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.viewConsentRecord)
                                }

                                Row {
                                IconButton(
                                    onClick = {
                                        runGuardedAction(profile) { viewModel.toggleProtection(profile) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (profile.isProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = strings.lock
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        runGuardedAction(profile) {
                                            renameText = profile.profileName
                                            renamingProfile = profile
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = strings.rename)
                                }
                                IconButton(
                                    onClick = {
                                        runGuardedAction(profile) { viewModel.deleteProfile(profile) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = strings.delete,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    renamingProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { renamingProfile = null },
            title = { Text(strings.rename) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(strings.profileNameLabel) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameProfile(profile.id, renameText)
                    renamingProfile = null
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renamingProfile = null }) { Text(strings.cancel) }
            }
        )
    }

    viewingConsent?.let { profile ->
        ConsentRecordDialog(
            profile = profile,
            strings = strings,
            onDismiss = { viewingConsent = null }
        )
    }

    pendingPinAction?.let { (profile, action) ->
        PinEntryDialog(
            title = profile.profileName,
            strings = strings,
            errorMessage = pinError,
            onDismiss = {
                viewModel.clearPinError()
                pendingPinAction = null
            },
            onConfirmPin = { entered ->
                if (viewModel.verifyAndUnlock(profile.id, entered, settings.privacyPinHash, strings.wrongPinError)) {
                    pendingPinAction = null
                    action()
                }
            }
        )
    }
}

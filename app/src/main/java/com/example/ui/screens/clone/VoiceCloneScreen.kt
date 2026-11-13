package com.example.ui.screens.clone

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.core.AppLanguage
import com.example.core.StudioStrings
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.remote.CloningAvailability
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@Composable
fun VoiceCloneScreen(
    viewModel: VoiceCloneViewModel,
    strings: StudioStrings,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onExportClip: (AudioClipEntity, AudioFormat) -> Unit,
    onShareClip: (AudioClipEntity, AudioFormat) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val recordingStatus by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val recordingElapsed by viewModel.recordingElapsedMs.collectAsStateWithLifecycle()
    val recordingWaveform by viewModel.recordingWaveform.collectAsStateWithLifecycle()
    val stagedSample by viewModel.stagedSample.collectAsStateWithLifecycle()
    val profileName by viewModel.profileName.collectAsStateWithLifecycle()
    val speakerName by viewModel.speakerName.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val consentGranted by viewModel.consentGranted.collectAsStateWithLifecycle()
    val protectWithPin by viewModel.protectWithPin.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val cloneScript by viewModel.cloneScript.collectAsStateWithLifecycle()
    val useOfflineClone by viewModel.useOfflineClone.collectAsStateWithLifecycle()
    val isSynthesizingClone by viewModel.isSynthesizingClone.collectAsStateWithLifecycle()
    val generatedCloneResult by viewModel.generatedCloneResult.collectAsStateWithLifecycle()
    val generatedClonePeaks by viewModel.generatedClonePeaks.collectAsStateWithLifecycle()
    val playbackState by viewModel.player.state.collectAsStateWithLifecycle()
    val isBengali = settings.language == AppLanguage.BENGALI

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.startRecordingSample()
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAudioSample(it) }
    }

    val cloningAvailability = viewModel.getCloningAvailability()

    StudioScreenScaffold(
        title = strings.cloneTitle,
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
            // Free Built-in Cloning Availability Banner (No Charge)
            item {
                when (cloningAvailability) {
                    CloningAvailability.NotConfigured -> {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = EmeraldOnline.copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, EmeraldOnline.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldOnline)
                                    Text(
                                        text = strings.freeCloneReadyTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldOnline,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = strings.freeCloneReadyBody,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("clone_open_settings_button")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.menuSettings)
                                }
                            }
                        }
                    }

                    CloningAvailability.Offline -> {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = AmberWarn.copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = AmberWarn)
                                Text(
                                    text = strings.offlineModeBlockedMsg,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    is CloningAvailability.Ready -> {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = EmeraldOnline.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, EmeraldOnline.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldOnline)
                                Column {
                                    Text(strings.cloningBackendStatus, style = MaterialTheme.typography.titleMedium, color = EmeraldOnline)
                                    Text(cloningAvailability.endpointUrl, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Step 1: Record or Import Sample
            item {
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
                            Text(
                                text = strings.cloneStepRecordOrImport,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (recordingStatus == RecordingStatus.RECORDING) {
                                StatusPillBadge(
                                    text = formatDurationMs(recordingElapsed),
                                    color = CrimsonError
                                )
                            }
                        }

                        AudioWaveformVisualizer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            isActive = recordingStatus == RecordingStatus.RECORDING ||
                                (stagedSample != null && playbackState.activeFilePath == stagedSample?.filePath && playbackState.isPlaying),
                            amplitudes = if (recordingStatus == RecordingStatus.RECORDING) {
                                recordingWaveform
                            } else {
                                stagedSample?.waveformPeaks.orEmpty()
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isRecording = recordingStatus == RecordingStatus.RECORDING
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        viewModel.stopRecordingSample()
                                    } else if (hasMicPermission) {
                                        viewModel.startRecordingSample()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clone_record_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) CrimsonError else ElectricViolet
                                )
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isRecording) strings.stop else strings.recordOwnVoice)
                            }

                            OutlinedButton(
                                onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                enabled = !isRecording,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clone_import_button")
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.importAudioSample)
                            }
                        }

                        stagedSample?.let { sample ->
                            val isPreviewPlaying = playbackState.activeFilePath == sample.filePath && playbackState.isPlaying
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sample.displayName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${formatDurationMs(sample.durationMs)} • ${formatFileSize(sample.fileSizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "F0: ${sample.fingerprint.fundamentalFreqHz.toInt()} Hz • ${if (isBengali) sample.fingerprint.registerLabelBn else sample.fingerprint.registerLabelEn}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = viewModel::togglePreviewSample,
                                        modifier = Modifier.testTag("clone_preview_sample_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isPreviewPlaying) strings.pause else strings.preview)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 2: Explicit Voice-Owner Consent & Profile Details
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
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonCyan)
                            Text(
                                text = strings.cloneConsentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = profileName,
                            onValueChange = viewModel::updateProfileName,
                            label = { Text(strings.profileNameLabel) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clone_profile_name_input")
                        )

                        OutlinedTextField(
                            value = speakerName,
                            onValueChange = viewModel::updateSpeakerName,
                            label = { Text(strings.speakerNameLabel) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clone_speaker_name_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedLanguage == "bn-IN",
                                onClick = { viewModel.selectLanguage("bn-IN") },
                                label = { Text("বাংলা (Bengali)") }
                            )
                            FilterChip(
                                selected = selectedLanguage == "en-US",
                                onClick = { viewModel.selectLanguage("en-US") },
                                label = { Text("English") }
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = strings.cloneConsentBody,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = consentGranted,
                                onCheckedChange = viewModel::setConsentGranted,
                                modifier = Modifier.testTag("clone_consent_checkbox")
                            )
                            Text(
                                text = strings.consentCheckboxLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(18.dp))
                                Text(strings.protectedBadge, style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = protectWithPin,
                                onCheckedChange = viewModel::setProtectWithPin
                            )
                        }
                    }
                }
            }

            // Messages
            if (!errorMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CrimsonError.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CrimsonError)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CrimsonError)
                            Text(errorMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CrimsonError)
                        }
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = EmeraldOnline.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EmeraldOnline)
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

            // Action Buttons (Save Profile + Optional External API)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.saveAuthorizedProfile(sendToReplicationApi = false) },
                        enabled = !isBusy && !isSynthesizingClone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("clone_save_profile_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text(strings.saveAuthorizedProfile)
                        }
                    }

                    if (cloningAvailability is CloningAvailability.Ready) {
                        OutlinedButton(
                            onClick = { viewModel.saveAuthorizedProfile(sendToReplicationApi = true) },
                            enabled = !isBusy && !isSynthesizingClone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("clone_send_api_button")
                        ) {
                            Text(strings.connectCloningApi)
                        }
                    }
                }
            }

            // Step 3: Free Built-in Voice-Match Cloning Synthesis (Zero Charge)
            item {
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
                            Text(
                                text = strings.freeCloneStepSpeakTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            StatusPillBadge(text = "FREE • ৳0", color = EmeraldOnline)
                        }

                        OutlinedTextField(
                            value = cloneScript,
                            onValueChange = viewModel::updateCloneScript,
                            placeholder = { Text(strings.ttsInputPlaceholder) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clone_speak_script_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateCloneScript(
                                        "আসসালামু আলাইকুম! এটি আমার নিজের কণ্ঠের ফ্রি ক্লোন করা অডিও।"
                                    )
                                    viewModel.selectLanguage("bn-IN")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(strings.sampleBengali) }

                            OutlinedButton(
                                onClick = {
                                    viewModel.updateCloneScript(
                                        "Hello! This voice was matched and synthesized completely free inside Munzir Voice Cloner."
                                    )
                                    viewModel.selectLanguage("en-US")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(strings.sampleEnglish) }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !useOfflineClone && isOnline && !settings.offlineOnlyMode,
                                onClick = { viewModel.setUseOfflineClone(false) },
                                enabled = isOnline && !settings.offlineOnlyMode,
                                label = { Text(strings.freeCloneOnlineMode) }
                            )
                            FilterChip(
                                selected = useOfflineClone || !isOnline || settings.offlineOnlyMode,
                                onClick = { viewModel.setUseOfflineClone(true) },
                                label = { Text(strings.freeCloneOfflineMode) }
                            )
                        }

                        Button(
                            onClick = viewModel::generateFreeClonedSpeech,
                            enabled = !isBusy && !isSynthesizingClone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("clone_free_generate_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricViolet,
                                contentColor = Color.White
                            )
                        ) {
                            if (isSynthesizingClone) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.freeCloneGenerating)
                            } else {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.freeCloneGenerateBtn)
                            }
                        }
                    }
                }
            }

            // Generated Cloned Output Player & Export
            generatedCloneResult?.let { result ->
                item {
                    val clip = result.clip
                    val isPlayingThis = playbackState.activeFilePath == clip.filePath && playbackState.isPlaying
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
                                    Text(clip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${result.baseVoiceUsed} • ${result.fingerprint.fundamentalFreqHz.toInt()} Hz • ${formatDurationMs(clip.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan
                                    )
                                }
                                StatusPillBadge(
                                    text = if (result.usedOfflineEngine) "Offline Free" else "AI Match Free",
                                    color = EmeraldOnline
                                )
                            }

                            AudioWaveformVisualizer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                isActive = isPlayingThis,
                                amplitudes = generatedClonePeaks,
                                progress = progress,
                                barCount = 40
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = viewModel::togglePlayGeneratedClone,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPlayingThis) strings.pause else strings.play)
                                }
                                IconButton(onClick = { viewModel.player.stop() }) {
                                    Icon(Icons.Default.Stop, contentDescription = strings.stop)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onExportClip(clip, AudioFormat.WAV) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportWav)
                                }
                                OutlinedButton(
                                    onClick = { onExportClip(clip, AudioFormat.MP3) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportMp3)
                                }
                                IconButton(onClick = { onShareClip(clip, AudioFormat.MP3) }) {
                                    Icon(Icons.Default.Share, contentDescription = strings.share)
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

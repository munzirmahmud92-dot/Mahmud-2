package com.example.ui.screens.library

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.core.StudioStrings
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@Composable
fun AudioLibraryScreen(
    viewModel: AudioLibraryViewModel,
    strings: StudioStrings,
    onBack: () -> Unit
) {
    val clips by viewModel.filteredClips.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val playbackState by viewModel.player.state.collectAsStateWithLifecycle()

    var renamingClip by remember { mutableStateOf<AudioClipEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var exportDialogClip by remember { mutableStateOf<AudioClipEntity?>(null) }
    var pendingSafExport by remember { mutableStateOf<Pair<AudioClipEntity, AudioFormat>?>(null) }

    val safCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val pending = pendingSafExport
        if (uri != null && pending != null) {
            viewModel.exportToUri(pending.first, pending.second, uri)
        }
        pendingSafExport = null
    }

    StudioScreenScaffold(
        title = strings.libraryTitle,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(strings.searchPlaceholder) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter == LibraryFilter.ALL,
                            onClick = { viewModel.selectFilter(LibraryFilter.ALL) },
                            label = { Text(strings.filterAll) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == LibraryFilter.ONLINE_TTS,
                            onClick = { viewModel.selectFilter(LibraryFilter.ONLINE_TTS) },
                            label = { Text(strings.filterOnlineTts) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == LibraryFilter.OFFLINE_TTS,
                            onClick = { viewModel.selectFilter(LibraryFilter.OFFLINE_TTS) },
                            label = { Text(strings.filterOfflineTts) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == LibraryFilter.RECORDINGS,
                            onClick = { viewModel.selectFilter(LibraryFilter.RECORDINGS) },
                            label = { Text(strings.filterRecordings) }
                        )
                    }
                }
            }

            if (isExporting) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

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

            if (clips.isEmpty()) {
                item {
                    StudioGradientCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(44.dp))
                            Text(strings.libraryEmpty, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                items(clips, key = { it.id }) { clip ->
                    val isPlaying = playbackState.activeFilePath == clip.filePath && playbackState.isPlaying
                    StudioGradientCard(accentGlow = isPlaying) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        text = "${formatDurationMs(clip.durationMs)} • ${formatFileSize(clip.fileSizeBytes)} • ${clip.sampleRateHz} Hz",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPillBadge(
                                    text = clip.format,
                                    color = if (clip.format == AudioFormat.MP3.name) ElectricViolet else NeonCyan
                                )
                            }

                            if (clip.scriptPreview.isNotBlank()) {
                                Text(
                                    text = clip.scriptPreview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.togglePlay(clip) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("library_play_${clip.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPlaying) strings.pause else strings.play)
                                }

                                OutlinedButton(
                                    onClick = { exportDialogClip = clip },
                                    modifier = Modifier.testTag("library_export_${clip.id}")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.export)
                                }

                                IconButton(
                                    onClick = { viewModel.shareClip(clip, AudioFormat.MP3) },
                                    modifier = Modifier.testTag("library_share_${clip.id}")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = strings.share)
                                }

                                IconButton(
                                    onClick = {
                                        renameText = clip.title
                                        renamingClip = clip
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = strings.rename)
                                }

                                IconButton(onClick = { viewModel.deleteClip(clip) }) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    renamingClip?.let { clip ->
        AlertDialog(
            onDismissRequest = { renamingClip = null },
            title = { Text(strings.rename) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameClip(clip.id, renameText)
                    renamingClip = null
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renamingClip = null }) { Text(strings.cancel) }
            }
        )
    }

    exportDialogClip?.let { clip ->
        AlertDialog(
            onDismissRequest = { exportDialogClip = null },
            title = { Text("${strings.export}: ${clip.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.exportToPhoneStorage(clip, AudioFormat.WAV)
                            exportDialogClip = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${strings.exportWav} (Music/MunzirVoiceCloner)")
                    }
                    Button(
                        onClick = {
                            viewModel.exportToPhoneStorage(clip, AudioFormat.MP3)
                            exportDialogClip = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${strings.exportMp3} (Music/MunzirVoiceCloner)")
                    }
                    OutlinedButton(
                        onClick = {
                            pendingSafExport = clip to AudioFormat.MP3
                            exportDialogClip = null
                            safCreateLauncher.launch("munzir_${clip.id}.mp3")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${strings.saveToPhone} (MP3 Picker)")
                    }
                    OutlinedButton(
                        onClick = {
                            pendingSafExport = clip to AudioFormat.WAV
                            exportDialogClip = null
                            safCreateLauncher.launch("munzir_${clip.id}.wav")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${strings.saveToPhone} (WAV Picker)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { exportDialogClip = null }) { Text(strings.cancel) }
            }
        )
    }
}

package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.audio.AudioExporter
import com.example.core.AppLanguage
import com.example.core.StudioAppContainer
import com.example.core.StudioViewModelFactory
import com.example.core.configureAppCheck
import com.example.core.stringsFor
import com.example.data.preferences.StudioSettings
import com.example.ui.components.StudioLaunchIntroOverlay
import com.example.ui.navigation.AiStudioRoute
import com.example.ui.navigation.AudioLibraryRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.OfflineStudioRoute
import com.example.ui.navigation.OnlineVoiceCloneRoute
import com.example.ui.navigation.RecordingStudioRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.TextToSpeechRoute
import com.example.ui.navigation.VoiceProfilesRoute
import com.example.ui.screens.aistudio.AiStudioScreen
import com.example.ui.screens.aistudio.AiStudioViewModel
import com.example.ui.screens.clone.VoiceCloneScreen
import com.example.ui.screens.clone.VoiceCloneViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.AudioLibraryScreen
import com.example.ui.screens.library.AudioLibraryViewModel
import com.example.ui.screens.offline.OfflineStudioScreen
import com.example.ui.screens.offline.OfflineStudioViewModel
import com.example.ui.screens.profiles.VoiceProfilesScreen
import com.example.ui.screens.profiles.VoiceProfilesViewModel
import com.example.ui.screens.recorder.RecordingStudioScreen
import com.example.ui.screens.recorder.RecordingStudioViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.tts.TextToSpeechScreen
import com.example.ui.screens.tts.TextToSpeechViewModel
import com.example.ui.theme.MunzirVoiceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: StudioAppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        appContainer = StudioAppContainer(application)
        enableEdgeToEdge()

        setContent {
            MunzirVoiceStudioApp(
                container = appContainer,
                onLaunchShare = { shareIntent ->
                    startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                },
                onShowToast = { msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroy() {
        if (::appContainer.isInitialized) {
            appContainer.release()
        }
        super.onDestroy()
    }
}

@Composable
private fun MunzirVoiceStudioApp(
    container: StudioAppContainer,
    onLaunchShare: (Intent) -> Unit,
    onShowToast: (String) -> Unit
) {
    val factory = remember(container) { StudioViewModelFactory(container) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = StudioSettings()
    )
    val isOnline by container.networkMonitor.isOnline.collectAsStateWithLifecycle()
    val todayUsage by container.geminiTtsRepository.observeTodayUsage().collectAsStateWithLifecycle(
        initialValue = null
    )
    val profiles by container.database.voiceProfileDao().observeAllProfiles().collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val clips by container.database.audioClipDao().observeAllClips().collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val playbackState by container.audioPlayer.state.collectAsStateWithLifecycle()

    val strings = remember(settings.language) { stringsFor(settings.language) }
    var showLaunchIntro by rememberSaveable { mutableStateOf(true) }

    MunzirVoiceTheme(themeMode = settings.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    strings = strings,
                    settings = settings,
                    isNetworkOnline = isOnline,
                    todayUsage = todayUsage,
                    profileCount = profiles.size,
                    clipCount = clips.size,
                    playbackState = playbackState,
                    onToggleLanguage = {
                        scope.launch {
                            val next = if (settings.language == AppLanguage.BENGALI) {
                                AppLanguage.ENGLISH
                            } else {
                                AppLanguage.BENGALI
                            }
                            container.settingsRepository.setLanguage(next)
                        }
                    },
                    onOpenAiStudio = { navController.navigate(AiStudioRoute) },
                    onOpenVoiceClone = { navController.navigate(OnlineVoiceCloneRoute()) },
                    onOpenOfflineStudio = { navController.navigate(OfflineStudioRoute) },
                    onOpenVoiceProfiles = { navController.navigate(VoiceProfilesRoute) },
                    onOpenTextToSpeech = { navController.navigate(TextToSpeechRoute()) },
                    onOpenAudioLibrary = { navController.navigate(AudioLibraryRoute) },
                    onOpenRecordingStudio = { navController.navigate(RecordingStudioRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onMiniPlayPause = {
                        playbackState.activeFilePath?.let {
                            container.audioPlayer.togglePlayPause(it, playbackState.activeTitle)
                        }
                    },
                    onMiniStop = { container.audioPlayer.stop() }
                )
            }

            composable<AiStudioRoute> {
                val vm: AiStudioViewModel = viewModel(factory = factory)
                AiStudioScreen(
                    viewModel = vm,
                    strings = strings,
                    onBack = { navController.popBackStack() },
                    onSendToTts = { text, voiceId, langCode, speed, pitch ->
                        navController.navigate(
                            TextToSpeechRoute(
                                initialText = text,
                                voiceId = voiceId,
                                languageCode = langCode,
                                speed = speed,
                                pitch = pitch
                            )
                        )
                    },
                    onOpenOfflineStudio = { navController.navigate(OfflineStudioRoute) }
                )
            }

            composable<OnlineVoiceCloneRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<OnlineVoiceCloneRoute>()
                val vm: VoiceCloneViewModel = viewModel(factory = factory)
                LaunchedEffect(route.samplePath) {
                    if (!route.samplePath.isNullOrBlank()) {
                        vm.loadExistingSamplePath(route.samplePath, route.sampleTitle ?: "Studio Recording")
                    }
                }
                VoiceCloneScreen(
                    viewModel = vm,
                    strings = strings,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onExportClip = { clip, format ->
                        scope.launch {
                            val res = AudioExporter.saveClipToMediaStore(
                                context = container.application,
                                clip = clip,
                                targetFormat = format,
                                bitrateKbps = settings.audioQuality.mp3BitrateKbps
                            )
                            res.fold(
                                onSuccess = { name -> onShowToast("Saved to Music/MunzirVoiceCloner/$name") },
                                onFailure = { err -> onShowToast(err.localizedMessage ?: "Export failed") }
                            )
                        }
                    },
                    onShareClip = { clip, format ->
                        scope.launch {
                            val res = AudioExporter.createShareIntent(
                                context = container.application,
                                clip = clip,
                                targetFormat = format,
                                bitrateKbps = settings.audioQuality.mp3BitrateKbps
                            )
                            res.fold(
                                onSuccess = onLaunchShare,
                                onFailure = { err -> onShowToast(err.localizedMessage ?: "Share failed") }
                            )
                        }
                    }
                )
            }

            composable<OfflineStudioRoute> {
                val vm: OfflineStudioViewModel = viewModel(factory = factory)
                OfflineStudioScreen(
                    viewModel = vm,
                    strings = strings,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<VoiceProfilesRoute> {
                val vm: VoiceProfilesViewModel = viewModel(factory = factory)
                VoiceProfilesScreen(
                    viewModel = vm,
                    player = container.audioPlayer,
                    settings = settings,
                    strings = strings,
                    onBack = { navController.popBackStack() },
                    onCreateProfile = { navController.navigate(OnlineVoiceCloneRoute()) },
                    onUseProfile = { profile ->
                        navController.navigate(
                            OnlineVoiceCloneRoute(
                                samplePath = profile.sampleFilePath,
                                sampleTitle = profile.profileName
                            )
                        )
                    }
                )
            }

            composable<TextToSpeechRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<TextToSpeechRoute>()
                val vm: TextToSpeechViewModel = viewModel(factory = factory)
                LaunchedEffect(route) {
                    vm.applyPresetIfNeeded(
                        initialText = route.initialText,
                        voiceId = route.voiceId,
                        languageCode = route.languageCode,
                        speedValue = route.speed,
                        pitchValue = route.pitch
                    )
                }
                TextToSpeechScreen(
                    viewModel = vm,
                    strings = strings,
                    onBack = { navController.popBackStack() },
                    onOpenOfflineStudio = { navController.navigate(OfflineStudioRoute) },
                    onOpenAiStudio = { navController.navigate(AiStudioRoute) },
                    onExportClip = { clip, format ->
                        scope.launch {
                            val res = AudioExporter.saveClipToMediaStore(
                                context = container.application,
                                clip = clip,
                                targetFormat = format,
                                bitrateKbps = settings.audioQuality.mp3BitrateKbps
                            )
                            res.fold(
                                onSuccess = { name -> onShowToast("Saved to Music/MunzirVoiceCloner/$name") },
                                onFailure = { err -> onShowToast(err.localizedMessage ?: "Export failed") }
                            )
                        }
                    },
                    onShareClip = { clip, format ->
                        scope.launch {
                            val res = AudioExporter.createShareIntent(
                                context = container.application,
                                clip = clip,
                                targetFormat = format,
                                bitrateKbps = settings.audioQuality.mp3BitrateKbps
                            )
                            res.fold(
                                onSuccess = onLaunchShare,
                                onFailure = { err -> onShowToast(err.localizedMessage ?: "Share failed") }
                            )
                        }
                    }
                )
            }

            composable<AudioLibraryRoute> {
                val vm: AudioLibraryViewModel = viewModel(factory = factory)
                AudioLibraryScreen(
                    viewModel = vm,
                    strings = strings,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<RecordingStudioRoute> {
                val vm: RecordingStudioViewModel = viewModel(factory = factory)
                RecordingStudioScreen(
                    viewModel = vm,
                    strings = strings,
                    onBack = { navController.popBackStack() },
                    onSendToVoiceClone = { path, title ->
                        navController.navigate(OnlineVoiceCloneRoute(samplePath = path, sampleTitle = title))
                    }
                )
            }

            composable<SettingsRoute> {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(
                    viewModel = vm,
                    strings = strings,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        StudioLaunchIntroOverlay(
            visible = showLaunchIntro,
            brandBengali = strings.brandBengali,
            brandEnglish = strings.brandEnglish,
            subtitle = strings.studioSubtitle,
            onFinished = { showLaunchIntro = false }
        )
        }
    }
}

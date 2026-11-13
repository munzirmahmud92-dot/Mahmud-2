package com.example.ui.screens.aistudio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppLanguage
import com.example.core.StudioStrings
import com.example.data.remote.AiScriptPreset
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioGradientCard
import com.example.ui.components.StudioScreenScaffold
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.NeonCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioScreen(
    viewModel: AiStudioViewModel,
    strings: StudioStrings,
    onBack: () -> Unit,
    onSendToTts: (text: String, voiceId: String?, languageCode: String?, speed: Float?, pitch: Float?) -> Unit,
    onOpenOfflineStudio: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()

    val isBengali = settings.language == AppLanguage.BENGALI
    val isBlockedOffline = settings.offlineOnlyMode || !isOnline

    StudioScreenScaffold(
        title = strings.aiStudioTitle,
        strings = strings,
        onBack = onBack
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = NeonCyan
            ) {
                Tab(
                    selected = activeTab == AiStudioTab.SCRIPT_WRITER,
                    onClick = { viewModel.selectTab(AiStudioTab.SCRIPT_WRITER) },
                    modifier = Modifier.testTag("ai_tab_script"),
                    text = { Text(strings.aiTabScript) },
                    icon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == AiStudioTab.VOICE_COACH,
                    onClick = { viewModel.selectTab(AiStudioTab.VOICE_COACH) },
                    modifier = Modifier.testTag("ai_tab_coach"),
                    text = { Text(strings.aiTabCoach) },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == AiStudioTab.STUDIO_CHAT,
                    onClick = { viewModel.selectTab(AiStudioTab.STUDIO_CHAT) },
                    modifier = Modifier.testTag("ai_tab_chat"),
                    text = { Text(strings.aiTabChat) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (isBlockedOffline) {
                StudioGradientCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
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
                        OutlinedButton(onClick = onOpenOfflineStudio) {
                            Text(strings.goToOfflineStudio)
                        }
                    }
                }
            }

            when (activeTab) {
                AiStudioTab.SCRIPT_WRITER -> ScriptWriterTab(
                    viewModel = viewModel,
                    strings = strings,
                    isBengali = isBengali,
                    isBlockedOffline = isBlockedOffline,
                    onSendToTts = onSendToTts
                )
                AiStudioTab.VOICE_COACH -> VoiceCoachTab(
                    viewModel = viewModel,
                    strings = strings,
                    isBengali = isBengali,
                    isBlockedOffline = isBlockedOffline,
                    onSendToTts = onSendToTts
                )
                AiStudioTab.STUDIO_CHAT -> StudioChatTab(
                    viewModel = viewModel,
                    strings = strings,
                    isBengali = isBengali,
                    isBlockedOffline = isBlockedOffline,
                    onSendToTts = onSendToTts
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptWriterTab(
    viewModel: AiStudioViewModel,
    strings: StudioStrings,
    isBengali: Boolean,
    isBlockedOffline: Boolean,
    onSendToTts: (String, String?, String?, Float?, Float?) -> Unit
) {
    val topic by viewModel.topic.collectAsStateWithLifecycle()
    val preset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLanguageCode.collectAsStateWithLifecycle()
    val tone by viewModel.selectedTone.collectAsStateWithLifecycle()
    val scriptState by viewModel.scriptState.collectAsStateWithLifecycle()

    val tones = listOf(
        "Warm & Expressive",
        "Energetic & Crisp",
        "Calm & Documentary",
        "Dramatic Cinema"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            StudioGradientCard(accentGlow = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.aiScriptPromptLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = topic,
                        onValueChange = viewModel::updateTopic,
                        placeholder = { Text(strings.aiScriptPromptHint) },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_script_topic_input")
                    )

                    Text(strings.aiPresetLabel, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiScriptPreset.entries.forEach { item ->
                            FilterChip(
                                selected = preset == item,
                                onClick = { viewModel.selectPreset(item) },
                                label = { Text(if (isBengali) item.labelBn else item.labelEn) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = targetLang == "bn-IN",
                            onClick = { viewModel.selectLanguageCode("bn-IN") },
                            label = { Text("বাংলা (Bengali)") }
                        )
                        FilterChip(
                            selected = targetLang == "en-US",
                            onClick = { viewModel.selectLanguageCode("en-US") },
                            label = { Text("English (US)") }
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tones.forEach { t ->
                            FilterChip(
                                selected = tone == t,
                                onClick = { viewModel.selectTone(t) },
                                label = { Text(t) }
                            )
                        }
                    }

                    val isLoading = scriptState is AiGenUiState.Loading || scriptState is AiGenUiState.Streaming
                    Button(
                        onClick = viewModel::generateScript,
                        enabled = !isLoading && !isBlockedOffline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("ai_generate_script_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.aiGenerating)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.aiGenerateScriptBtn)
                        }
                    }
                }
            }
        }

        val err = scriptState as? AiGenUiState.Error
        if (err != null) {
            item {
                AiErrorBanner(message = err.message, retryLabel = strings.retry, onRetry = viewModel::generateScript)
            }
        }

        val generatedText = when (val s = scriptState) {
            is AiGenUiState.Streaming -> s.text
            is AiGenUiState.Success -> s.text
            else -> null
        }
        if (generatedText != null) {
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
                                text = strings.aiScriptOutputTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            StatusPillBadge(
                                text = if (scriptState is AiGenUiState.Streaming) "Streaming" else "Gemini 3.8 Flash",
                                color = if (scriptState is AiGenUiState.Streaming) NeonCyan else EmeraldOnline
                            )
                        }
                        Text(
                            text = generatedText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (scriptState is AiGenUiState.Success) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onSendToTts(generatedText, null, targetLang, null, null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ai_send_script_to_tts")
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.aiUseInTts)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateCoachInput(generatedText)
                                        viewModel.selectTab(AiStudioTab.VOICE_COACH)
                                        viewModel.analyzeVoiceDirection(isBengali)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(strings.aiAnalyzeVoiceBtn)
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

@Composable
private fun VoiceCoachTab(
    viewModel: AiStudioViewModel,
    strings: StudioStrings,
    isBengali: Boolean,
    isBlockedOffline: Boolean,
    onSendToTts: (String, String?, String?, Float?, Float?) -> Unit
) {
    val input by viewModel.coachInput.collectAsStateWithLifecycle()
    val coachState by viewModel.coachState.collectAsStateWithLifecycle()
    val transformState by viewModel.transformState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            StudioGradientCard(accentGlow = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.aiCoachTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = viewModel::updateCoachInput,
                        placeholder = { Text(strings.ttsInputPlaceholder) },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_coach_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.transformCoachText("POLISH_TTS") },
                            enabled = !isBlockedOffline,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.aiPolishBtn)
                        }
                        OutlinedButton(
                            onClick = { viewModel.transformCoachText("TRANSLATE_TO_BN") },
                            enabled = !isBlockedOffline,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("→ বাংলা")
                        }
                        OutlinedButton(
                            onClick = { viewModel.transformCoachText("TRANSLATE_TO_EN") },
                            enabled = !isBlockedOffline,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("→ EN")
                        }
                    }

                    val analyzing = coachState is VoiceCoachUiState.Analyzing
                    Button(
                        onClick = { viewModel.analyzeVoiceDirection(isBengali) },
                        enabled = !analyzing && !isBlockedOffline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("ai_analyze_voice_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue, contentColor = Color.White)
                    ) {
                        if (analyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.aiGenerating)
                        } else {
                            Icon(Icons.Default.Psychology, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.aiAnalyzeVoiceBtn)
                        }
                    }
                }
            }
        }

        if (transformState is AiGenUiState.Error) {
            item {
                AiErrorBanner(
                    message = (transformState as AiGenUiState.Error).message,
                    retryLabel = strings.retry,
                    onRetry = { viewModel.transformCoachText("POLISH_TTS") }
                )
            }
        }

        when (val c = coachState) {
            is VoiceCoachUiState.Error -> {
                item {
                    AiErrorBanner(
                        message = c.message,
                        retryLabel = strings.retry,
                        onRetry = { viewModel.analyzeVoiceDirection(isBengali) }
                    )
                }
            }
            is VoiceCoachUiState.Ready -> {
                val plan = c.plan
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
                                Text(
                                    text = plan.deliveryStyle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                StatusPillBadge(text = plan.recommendedVoiceId, color = ElectricViolet)
                            }
                            Text(
                                text = "Voice: ${plan.recommendedVoiceId} • Lang: ${plan.recommendedLanguageCode} • Speed: ${String.format("%.2fx", plan.recommendedSpeed)} • Pitch: ${String.format("%.2fx", plan.recommendedPitch)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = plan.coachingTip,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = {
                                    onSendToTts(
                                        input,
                                        plan.recommendedVoiceId,
                                        plan.recommendedLanguageCode,
                                        plan.recommendedSpeed,
                                        plan.recommendedPitch
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_apply_plan_button")
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.aiApplyPlanToTts)
                            }
                        }
                    }
                }
            }
            else -> Unit
        }

        item { Spacer(modifier = Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun StudioChatTab(
    viewModel: AiStudioViewModel,
    strings: StudioStrings,
    isBengali: Boolean,
    isBlockedOffline: Boolean,
    onSendToTts: (String, String?, String?, Float?, Float?) -> Unit
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatInput by viewModel.chatInput.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val isSending by viewModel.isChatSending.collectAsStateWithLifecycle()

    val quickPrompts = if (isBengali) {
        listOf(
            "বাংলায় একটি দারুণ পডকাস্ট ইন্ট্রো লিখে দাও",
            "রেকর্ডিংয়ের আগে কণ্ঠ ও মাইক্রোফোন পরিষ্কার রাখার ৩টি টিপস দাও",
            "Create a bilingual Bengali-English welcome intro"
        )
    } else {
        listOf(
            "Write a 40-word studio intro in Bengali and English",
            "Give 3 mic tips for cleaner voice clone samples",
            "Draft a calm Bengali bedtime narration"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickPrompts.forEach { prompt ->
                        Surface(
                            onClick = { viewModel.sendChatMessage(prompt) },
                            enabled = !isSending && !isBlockedOffline,
                            shape = RoundedCornerShape(12.dp),
                            color = ElectricViolet.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.isUser) {
                            ElectricViolet.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (msg.isUser) ElectricViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = msg.text.ifBlank { "..." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!msg.isUser && !msg.isStreaming && msg.text.isNotBlank() && msg.id != 1L) {
                                OutlinedButton(
                                    onClick = { onSendToTts(msg.text, null, null, null, null) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.aiUseInTts, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            if (chatError != null) {
                item {
                    AiErrorBanner(
                        message = chatError!!,
                        retryLabel = strings.retry,
                        onRetry = { viewModel.sendChatMessage() }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = viewModel::updateChatInput,
                placeholder = { Text(strings.aiChatPlaceholder) },
                maxLines = 3,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input")
            )
            IconButton(
                onClick = { viewModel.sendChatMessage() },
                enabled = chatInput.isNotBlank() && !isSending && !isBlockedOffline,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("ai_chat_send_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun AiErrorBanner(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit
) {
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
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onRetry) {
                Text(retryLabel)
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimatedBrandTitle(
    brandBengali: String,
    brandEnglish: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "brand_shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    val badgePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_pulse"
    )

    val entryScale = remember { Animatable(0.88f) }
    val entryAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            entryScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            entryAlpha.animateTo(1f, animationSpec = tween(550))
        }
    }

    val titleBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF3F6FF),
            NeonCyan,
            ElectricVioletLight,
            Color(0xFFF3F6FF)
        ),
        start = Offset(shimmerOffset - 240f, 0f),
        end = Offset(shimmerOffset + 240f, 80f)
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = entryScale.value
                scaleY = entryScale.value
                alpha = entryAlpha.value
            }
            .testTag("animated_brand_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .scale(badgePulse)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(ElectricViolet, CobaltBlue, NeonCyan))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = brandEnglish,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = brandBengali,
                style = MaterialTheme.typography.headlineSmall.merge(
                    TextStyle(brush = titleBrush)
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = brandEnglish,
                style = MaterialTheme.typography.labelLarge,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StudioLaunchIntroOverlay(
    visible: Boolean,
    brandBengali: String,
    brandEnglish: String,
    subtitle: String,
    onFinished: () -> Unit
) {
    val emblemScale = remember { Animatable(0.45f) }
    val titleReveal = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val englishTranslateY = remember { Animatable(28f) }
    val letterSpacingAnim = remember { Animatable(0.5f) }
    val dividerWidthFraction = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "intro_pulse")
    val ringExpand by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_expand"
    )
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "intro_shimmer"
    )

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        launch {
            emblemScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            delay(140L)
            titleReveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(460L)
            launch { subtitleAlpha.animateTo(1f, tween(520)) }
            launch { englishTranslateY.animateTo(0f, tween(520, easing = FastOutSlowInEasing)) }
            launch { letterSpacingAnim.animateTo(3.2f, tween(650, easing = FastOutSlowInEasing)) }
            launch { dividerWidthFraction.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        }
        delay(2350L)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(420)) + scaleOut(targetScale = 1.06f, animationSpec = tween(420))
    ) {
        val visibleChars = (brandBengali.length * titleReveal.value)
            .toInt()
            .coerceIn(0, brandBengali.length)
        val revealedBengali = brandBengali.substring(0, visibleChars)

        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                Color.White,
                NeonCyan,
                ElectricVioletLight,
                Color.White
            ),
            start = Offset(shimmerX - 240f, 0f),
            end = Offset(shimmerX + 240f, 120f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1044),
                            Color(0xFF0A0E22),
                            Color(0xFF04060C)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFinished
                )
                .testTag("studio_launch_intro_overlay"),
            contentAlignment = Alignment.Center
        ) {
            // Concentric acoustic sonar rings behind the emblem
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.43f)
                val baseR = size.minDimension * 0.14f
                for (i in 0..2) {
                    val phase = (ringExpand + i / 3f) % 1f
                    val radius = baseR + phase * size.minDimension * 0.34f
                    val alpha = (1f - phase) * 0.38f
                    drawCircle(
                        color = if (i % 2 == 0) NeonCyan.copy(alpha = alpha) else ElectricViolet.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Glowing AI emblem
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(emblemScale.value)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricViolet, CobaltBlue, NeonCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = brandEnglish,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Animated Waveform strip
                AudioWaveformVisualizer(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(44.dp),
                    isActive = true,
                    barCount = 28
                )

                // Animated Bengali App Name (Character Reveal + Shimmer)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = revealedBengali.ifEmpty { " " },
                        style = MaterialTheme.typography.headlineLarge.merge(
                            TextStyle(
                                brush = shimmerBrush,
                                fontSize = 31.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .graphicsLayer {
                                val pulse = 0.94f + 0.06f * titleReveal.value
                                scaleX = pulse
                                scaleY = pulse
                            }
                            .testTag("intro_animated_app_name")
                    )

                    // Glowing animated divider bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f * dividerWidthFraction.value.coerceAtLeast(0.02f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ElectricViolet, NeonCyan, CobaltBlue)
                                )
                            )
                    )

                    // Animated English Brand Name
                    Text(
                        text = brandEnglish,
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = letterSpacingAnim.value.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .alpha(subtitleAlpha.value)
                            .graphicsLayer { translationY = englishTranslateY.value }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = ElectricViolet.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.alpha(subtitleAlpha.value)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }

            // Tap to skip pill at top-right
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .clickable(onClick = onFinished)
                    .testTag("intro_skip_button")
            ) {
                Text(
                    text = "Skip • এড়িয়ে যান",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

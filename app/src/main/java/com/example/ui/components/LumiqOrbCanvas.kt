package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeuralViolet
import com.example.voice.VoiceEngineState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LumiqOrbCanvas(
    voiceState: VoiceEngineState,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    orbSize: Dp = 216.dp,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "lumiq_orb")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceEngineState.LISTENING -> 4200
                    VoiceEngineState.SPEAKING -> 3200
                    VoiceEngineState.PROCESSING -> 2200
                    VoiceEngineState.IDLE -> 11000
                },
                easing = LinearEasing
            )
        ),
        label = "orb_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val activeRms = rmsLevel.coerceIn(0f, 1f)
    val primaryGlow = when (voiceState) {
        VoiceEngineState.LISTENING -> CyanGlow
        VoiceEngineState.SPEAKING -> ElectricBlue
        VoiceEngineState.PROCESSING -> NeuralViolet
        VoiceEngineState.IDLE -> ElectricBlue
    }
    val secondaryGlow = when (voiceState) {
        VoiceEngineState.LISTENING -> ElectricBlue
        VoiceEngineState.SPEAKING -> CyanGlow
        VoiceEngineState.PROCESSING -> CyanGlow
        VoiceEngineState.IDLE -> CyanGlow
    }

    Box(
        modifier = modifier
            .size(orbSize)
            .testTag("lumiq_ai_orb")
            .semantics {
                contentDescription = "LUMIQ AI 10D Voice Orb. State: ${voiceState.name}. Tap to talk or stop."
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val baseRadius = size.minDimension * 0.30f * (pulse + activeRms * 0.08f)

            // Outer ambient halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.30f + activeRms * 0.15f),
                        secondaryGlow.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.50f
                ),
                radius = size.minDimension * 0.50f,
                center = center
            )

            // Precision radar tick marks ring (inspired by futuristic HUD)
            val tickRingRadius = size.minDimension * 0.40f
            val tickCount = 60
            for (i in 0 until tickCount) {
                val angleRad = ((i * (360f / tickCount)) + rotationAngle * 0.25f) * (PI / 180.0)
                val isMajor = i % 5 == 0
                val innerR = tickRingRadius - (if (isMajor) 10f else 5f)
                val outerR = tickRingRadius + (if (isMajor) 6f else 2f)
                drawLine(
                    color = primaryGlow.copy(alpha = if (isMajor) 0.55f else 0.25f),
                    start = Offset(
                        cx + (cos(angleRad) * innerR).toFloat(),
                        cy + (sin(angleRad) * innerR).toFloat()
                    ),
                    end = Offset(
                        cx + (cos(angleRad) * outerR).toFloat(),
                        cy + (sin(angleRad) * outerR).toFloat()
                    ),
                    strokeWidth = if (isMajor) 2.2f else 1.2f,
                    cap = StrokeCap.Round
                )
            }

            // Frosted chrome-silver metallic outer bezel
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color(0xFFC8D8EE),
                        primaryGlow.copy(alpha = 0.55f)
                    )
                ),
                radius = baseRadius + 10f,
                center = center,
                style = Stroke(width = 6f)
            )

            // 3D deep sapphire core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF102A4C),
                        Color(0xFF09162B),
                        Color(0xFF040A14)
                    ),
                    center = Offset(cx - baseRadius * 0.2f, cy - baseRadius * 0.2f),
                    radius = baseRadius * 1.2f
                ),
                radius = baseRadius,
                center = center
            )

            // Inner glowing neural energy rings & constellation nodes
            val nodePoints = (0 until 12).map { idx ->
                val a = ((idx * 30f) + rotationAngle) * (PI / 180.0)
                val r = baseRadius * (0.42f + 0.24f * cos(idx * 1.3f + rotationAngle * 0.04f).toFloat())
                Offset(
                    cx + (cos(a) * r).toFloat(),
                    cy + (sin(a) * r).toFloat()
                )
            }

            for (i in nodePoints.indices) {
                val p1 = nodePoints[i]
                val p2 = nodePoints[(i + 3) % nodePoints.size]
                val p3 = nodePoints[(i + 5) % nodePoints.size]
                drawLine(
                    color = secondaryGlow.copy(alpha = 0.55f),
                    start = p1,
                    end = p2,
                    strokeWidth = 1.6f
                )
                drawLine(
                    color = primaryGlow.copy(alpha = 0.35f),
                    start = p1,
                    end = p3,
                    strokeWidth = 1.1f
                )
            }

            nodePoints.forEach { pt ->
                drawCircle(
                    color = Color.White,
                    radius = 3.2f,
                    center = pt
                )
                drawCircle(
                    color = CyanGlow.copy(alpha = 0.65f),
                    radius = 6.5f,
                    center = pt
                )
            }

            // Luminous center core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        CyanGlow.copy(alpha = 0.85f),
                        ElectricBlue.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.42f
                ),
                radius = baseRadius * 0.42f,
                center = center
            )
        }
    }
}

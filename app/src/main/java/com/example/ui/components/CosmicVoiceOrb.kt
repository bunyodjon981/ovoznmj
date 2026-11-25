package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.example.service.OrbPhase
import com.example.ui.theme.AmberThinking
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.SapphireCore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CosmicVoiceOrb(
    phase: OrbPhase,
    audioLevel: Float,
    size: Dp = 210.dp,
    onClick: (() -> Unit)? = null,
    testTagName: String = "voice_orb_button"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    OrbPhase.THINKING, OrbPhase.EXECUTING -> 2400
                    OrbPhase.ACTIVE_LISTENING, OrbPhase.SPEAKING -> 4200
                    else -> 9000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val smoothLevel by animateFloatAsState(
        targetValue = when (phase) {
            OrbPhase.ACTIVE_LISTENING, OrbPhase.SPEAKING -> audioLevel.coerceIn(0.08f, 1f)
            OrbPhase.THINKING, OrbPhase.EXECUTING -> 0.45f
            OrbPhase.PASSIVE_WAKE_LISTENING -> 0.14f + idlePulse * 0.08f
            OrbPhase.IDLE -> 0.05f + idlePulse * 0.04f
            OrbPhase.ERROR -> 0.2f
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
        label = "smooth_audio_level"
    )

    val primaryColor by animateColorAsState(
        targetValue = when (phase) {
            OrbPhase.IDLE -> SapphireCore
            OrbPhase.PASSIVE_WAKE_LISTENING -> ElectricCyan
            OrbPhase.ACTIVE_LISTENING -> EmeraldActive
            OrbPhase.THINKING, OrbPhase.EXECUTING -> AmberThinking
            OrbPhase.SPEAKING -> ElectricCyan
            OrbPhase.ERROR -> CoralError
        },
        animationSpec = tween(350),
        label = "orb_primary_color"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (phase) {
            OrbPhase.IDLE -> Color(0xFF1D2D50)
            OrbPhase.PASSIVE_WAKE_LISTENING -> SapphireCore
            OrbPhase.ACTIVE_LISTENING -> ElectricCyan
            OrbPhase.THINKING, OrbPhase.EXECUTING -> NeuralViolet
            OrbPhase.SPEAKING -> NeuralViolet
            OrbPhase.ERROR -> AmberThinking
        },
        animationSpec = tween(350),
        label = "orb_secondary_color"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = primaryColor),
                        onClick = onClick
                    )
                } else Modifier
            )
            .testTag(testTagName)
            .semantics {
                contentDescription = "Telefonim AI ovozli animatsiyali aylana"
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension * 0.30f

            // 1. Outer ambient radial aura expanding with voice amplitude
            val auraRadius = baseRadius * (1.55f + smoothLevel * 0.42f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.36f + smoothLevel * 0.25f),
                        secondaryColor.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = center
            )

            // 2. Rotating dual orbital rings
            rotate(degrees = rotation, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - baseRadius * (1.28f + smoothLevel * 0.14f),
                        center.y - baseRadius * (1.28f + smoothLevel * 0.14f)
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        baseRadius * 2f * (1.28f + smoothLevel * 0.14f),
                        baseRadius * 2f * (1.28f + smoothLevel * 0.14f)
                    ),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            rotate(degrees = -rotation * 1.35f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            secondaryColor.copy(alpha = 0.8f),
                            primaryColor.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 90f,
                    sweepAngle = 200f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - baseRadius * (1.14f + smoothLevel * 0.10f),
                        center.y - baseRadius * (1.14f + smoothLevel * 0.10f)
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        baseRadius * 2f * (1.14f + smoothLevel * 0.10f),
                        baseRadius * 2f * (1.14f + smoothLevel * 0.10f)
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Audio-reactive morphing circular wave contour
            val points = 64
            val wavePath = Path()
            val phaseRad = (rotation * PI / 180.0)
            val amplitude = baseRadius * (0.04f + smoothLevel * 0.24f)

            for (i in 0 until points) {
                val angle = (2.0 * PI * i) / points
                val harmonic1 = sin(angle * 4.0 + phaseRad * 2.0)
                val harmonic2 = cos(angle * 7.0 - phaseRad * 3.0)
                val r = baseRadius * (0.96f + smoothLevel * 0.16f) +
                    (amplitude * (0.65 * harmonic1 + 0.35 * harmonic2)).toFloat()
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y + (r * sin(angle)).toFloat()
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            wavePath.close()

            drawPath(
                path = wavePath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.92f),
                        secondaryColor.copy(alpha = 0.85f),
                        Color(0xFF0A1224)
                    ),
                    center = center,
                    radius = baseRadius * 1.25f
                )
            )
            drawPath(
                path = wavePath,
                color = Color.White.copy(alpha = 0.35f + smoothLevel * 0.35f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center icon indicator
        val icon = when (phase) {
            OrbPhase.IDLE -> Icons.Rounded.MicOff
            OrbPhase.PASSIVE_WAKE_LISTENING -> Icons.Rounded.Mic
            OrbPhase.ACTIVE_LISTENING -> Icons.Rounded.GraphicEq
            OrbPhase.THINKING, OrbPhase.EXECUTING -> Icons.Rounded.Psychology
            OrbPhase.SPEAKING -> Icons.Rounded.AutoAwesome
            OrbPhase.ERROR -> Icons.Rounded.Mic
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.19f)
        )
    }
}

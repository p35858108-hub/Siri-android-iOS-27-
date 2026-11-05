package com.assistant.siri.ui.capsule

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.assistant.siri.ui.theme.CapsuleColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Fase que avanza en bucle. Con [animate] en false se queda quieta. */
@Composable
private fun rememberLoopPhase(animate: Boolean, durationMillis: Int, endValue: Float): State<Float> =
    if (animate) {
        rememberInfiniteTransition(label = "loop").animateFloat(
            initialValue = 0f,
            targetValue = endValue,
            animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

/**
 * Barras que se mueven suavemente mientras se escucha. Con voz, su altura sigue [level]
 * (de 0.0 a 1.0). Todo se calcula al dibujar, sin recomponer.
 */
@Composable
fun ListeningIndicator(
    level: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
    color: Color = CapsuleColors.TextPrimary
) {
    val smoothLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "listeningLevel"
    )
    val phase = rememberLoopPhase(animate, durationMillis = 2400, endValue = (2.0 * PI).toFloat())

    Canvas(modifier.size(width = 56.dp, height = 28.dp)) {
        val barCount = 7
        val barWidth = 4.dp.toPx()
        val gap = 4.dp.toPx()
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (size.width - totalWidth) / 2f
        val minBar = 5.dp.toPx()
        val maxBar = size.height
        val middle = (barCount - 1) / 2f
        val p = phase.value
        val voice = smoothLevel

        for (i in 0 until barCount) {
            // Más altas en el centro, como una onda de voz
            val envelope = 1f - 0.4f * abs(i - middle) / middle
            val breathing = 0.5f + 0.5f * sin(p + i * 0.8f)
            val reaction = voice * (0.5f + 0.5f * sin(p * 2.2f + i * 1.7f))
            val amplitude = ((0.16f + 0.14f * breathing + 0.70f * reaction) * envelope).coerceIn(0.12f, 1f)

            val barHeight = minBar + (maxBar - minBar) * amplitude
            drawRoundRect(
                color = color.copy(alpha = 0.55f + 0.45f * amplitude),
                topLeft = Offset(startX + i * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

/** Tres puntos con una onda de luz: la cápsula está pensando. */
@Composable
fun ThinkingIndicator(
    animate: Boolean,
    modifier: Modifier = Modifier,
    color: Color = CapsuleColors.TextPrimary
) {
    val phase = rememberLoopPhase(animate, durationMillis = 1300, endValue = 1f)

    Canvas(modifier.size(width = 44.dp, height = 16.dp)) {
        val radius = 3.5.dp.toPx()
        val spacing = 12.dp.toPx()
        val centerY = size.height / 2f

        for (i in 0 until 3) {
            val wave = if (animate) {
                val shifted = phase.value - i * 0.16f
                val t = shifted - floor(shifted)
                0.5f - 0.5f * cos(t * 2f * PI.toFloat())
            } else {
                0.6f
            }
            drawCircle(
                color = color.copy(alpha = 0.35f + 0.65f * wave),
                radius = radius * (0.7f + 0.5f * wave),
                center = Offset(size.width / 2f + (i - 1) * spacing, centerY)
            )
        }
    }
}

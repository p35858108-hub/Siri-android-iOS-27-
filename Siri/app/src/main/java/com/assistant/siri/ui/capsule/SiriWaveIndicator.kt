package com.assistant.siri.ui.capsule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Onda Siri nativa: varias curvas tipo spindle con halo de color.
 * El nivel del micrófono amplía la onda cuando hay voz; no captura gestos ni toques.
 */
@Composable
fun SiriWaveIndicator(
    level: Float,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val phaseAnimation = remember { Animatable(0f) }

    // Al desactivar movimiento se cancela el bucle y la onda queda en una posición fija.
    LaunchedEffect(animate) {
        if (animate) {
            phaseAnimation.animateTo(
                targetValue = (2.0 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2300, easing = LinearEasing)
                )
            )
        } else {
            phaseAnimation.snapTo(0f)
        }
    }
    // La animación invalida el dibujo del Canvas, no toda la composición.
    val phase = phaseAnimation.asState()

    val palette = listOf(
        Color(0xFF42D8FF),
        Color(0xFF718CFF),
        Color(0xFFB36CFF),
        Color(0xFFFF78C8),
        Color(0xFFFFA66B)
    )
    val gradient = Brush.horizontalGradient(palette)

    Canvas(modifier.size(width = 144.dp, height = 32.dp)) {
        val centerY = size.height / 2f
        val intensity = level.coerceIn(0f, 1f)
        val currentPhase = phase.value
        val glowWidth = 8.dp.toPx()
        val midWidth = 3.8.dp.toPx()
        val coreWidth = 1.35.dp.toPx()

        // Las capas avanzan a distinta velocidad para dar profundidad y movimiento orgánico.
        for (layer in 0 until 5) {
            val path = Path()
            val layerPhase = currentPhase * (0.72f + layer * 0.105f) + layer * 1.17f
            val baseAmplitude = size.height * (0.15f + layer * 0.022f) *
                (0.48f + intensity * 0.95f)

            for (step in 0..96) {
                val x = size.width * step / 96f
                val normalized = (x / size.width) * 2f - 1f
                val envelope = (1f - abs(normalized)).coerceAtLeast(0f).pow(0.62f)
                val modulation = 0.82f + 0.18f * sin(currentPhase * 0.55f + layer * 1.4f)
                val wave = sin(normalized * 5.4f + layerPhase) *
                    baseAmplitude * envelope * modulation
                val y = centerY + wave

                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val tint = palette[layer]
            drawPath(
                path = path,
                color = tint.copy(alpha = 0.12f),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                color = tint.copy(alpha = 0.32f),
                style = Stroke(width = midWidth, cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                brush = gradient,
                alpha = 0.78f,
                style = Stroke(width = coreWidth, cap = StrokeCap.Round)
            )
        }

        // Núcleo luminoso más fino para unir las capas en el centro.
        val core = Path()
        for (step in 0..96) {
            val x = size.width * step / 96f
            val normalized = (x / size.width) * 2f - 1f
            val envelope = (1f - abs(normalized)).coerceAtLeast(0f).pow(0.72f)
            val y = centerY + sin(normalized * 5.9f + currentPhase * 1.08f) *
                size.height * (0.12f + intensity * 0.17f) * envelope
            if (step == 0) core.moveTo(x, y) else core.lineTo(x, y)
        }
        drawPath(
            path = core,
            color = Color.White.copy(alpha = 0.72f),
            style = Stroke(width = 0.85.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

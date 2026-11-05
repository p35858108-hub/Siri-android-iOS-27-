package com.assistant.siri.ui.capsule

import android.content.Context
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.assistant.siri.ui.theme.CapsuleColors

object BlurSupport {
    /** El desenfoque (RenderEffect) necesita Android 12 (API 31) o superior. */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Desenfoca el contenido con un RenderEffect. El radio (en píxeles) se lee al dibujar,
 * así que animarlo no recompone nada. Antes de Android 12 no hace nada.
 */
fun Modifier.backdropBlur(radiusPx: () -> Float): Modifier =
    if (!BlurSupport.isSupported) {
        this
    } else {
        graphicsLayer {
            val radius = radiusPx()
            if (radius > 0.5f) {
                renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                clip = true
            } else {
                renderEffect = null
                clip = false
            }
        }
    }

/**
 * Envuelve el contenido de la app: cuando [active] es true lo desenfoca y lo oscurece.
 * En versiones sin desenfoque usa solo una capa oscura más opaca.
 *
 * @param dim multiplicador de 0 a 1 que se lee al dibujar; el gesto de abrir la app lo
 * baja a 0 para quitar el desenfoque a medida que la cápsula se convierte en la pantalla.
 */
@Composable
fun AssistantBackdrop(
    active: Boolean,
    blurIntensity: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    dim: () -> Float = { 1f },
    onScrimClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val progress = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (reduceMotion) {
            snap<Float>()
        } else {
            tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
        },
        label = "backdropProgress"
    )
    val maxRadiusPx = with(LocalDensity.current) {
        (6f + 22f * blurIntensity.coerceIn(0f, 1f)).dp.toPx()
    }
    val scrimColor = if (BlurSupport.isSupported) CapsuleColors.Scrim else CapsuleColors.ScrimFallback
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropBlur { maxRadiusPx * progress.value * dim() }) {
            content()
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (progress.value * dim()).coerceIn(0f, 1f) }
                .background(scrimColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = active && onScrimClick != null
                ) { onScrimClick?.invoke() }
        )
    }
}

// ---------- Desenfoque a nivel de ventana (para el asistente superpuesto, Parte 12) ----------

/** True si el sistema permite desenfocar lo que hay detrás de una ventana. */
fun Context.isCrossWindowBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return windowManager.isCrossWindowBlurEnabled
}

/** Desenfoca lo que hay detrás de la ventana. Con radio 0 lo quita. Solo Android 12+. */
fun Window.setBehindBlur(radiusPx: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val params = attributes
    if (radiusPx > 0) {
        params.blurBehindRadius = radiusPx
        addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    } else {
        params.blurBehindRadius = 0
        clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
    attributes = params
}

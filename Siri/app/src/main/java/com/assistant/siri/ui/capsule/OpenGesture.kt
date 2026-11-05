package com.assistant.siri.ui.capsule

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Gesto de deslizar la respuesta hacia abajo para abrir la pantalla principal.
 *
 * [morph] va de 0 a 1 mientras se arrastra (se lee al dibujar). Al soltar, si se ha llegado
 * lo bastante lejos (o fue un gesto rápido hacia abajo), termina de abrirse y llama a
 * `onOpened`; si no, vuelve a 0 con un muelle. Subir el dedo sin soltar también lo cancela.
 */
@Stable
class OpenGesture internal constructor(
    private val scope: CoroutineScope,
    private val distancePx: () -> Float,
    private val enabled: () -> Boolean,
    private val reduceMotion: () -> Boolean,
    private val onOpened: () -> Unit
) {
    /** Progreso de la transformación, de 0 (cápsula) a 1 (pantalla completa). */
    var morph by mutableFloatStateOf(0f)
        private set

    private var dragPx = 0f
    private var animation: Job? = null
    private var inFling = false

    /** Abre la pantalla principal con la misma transición (por ejemplo, desde un botón). */
    fun open() {
        if (enabled()) finish(commit = true)
    }

    private fun dragBy(delta: Float): Float {
        val distance = distancePx()
        if (distance <= 0f) return 0f
        animation?.cancel()
        animation = null
        val newDrag = (dragPx + delta).coerceIn(0f, distance)
        val consumed = newDrag - dragPx
        dragPx = newDrag
        morph = dragPx / distance
        return consumed
    }

    private fun release(velocityY: Float) {
        if (dragPx <= 0f) return
        val commit = (morph > 0.4f && velocityY > -600f) || (velocityY > 1800f && morph > 0.06f)
        finish(commit)
    }

    private fun finish(commit: Boolean) {
        animation?.cancel()
        val distance = distancePx()
        animation = scope.launch {
            val target = if (commit) 1f else 0f
            if (reduceMotion()) {
                morph = target
                dragPx = target * distance
            } else {
                animate(
                    initialValue = morph,
                    targetValue = target,
                    animationSpec = if (commit) {
                        tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    } else {
                        spring(dampingRatio = 0.82f, stiffness = 320f)
                    }
                ) { value, _ ->
                    morph = value
                    dragPx = value * distance
                }
            }
            if (commit) onOpened()
            morph = 0f
            dragPx = 0f
        }
    }

    internal val draggableState = DraggableState { delta -> dragBy(delta) }

    internal fun onDragStopped(velocityY: Float) = release(velocityY)

    /**
     * Conecta el gesto con el texto desplazable de la respuesta: si el texto no está arriba
     * del todo, primero se desplaza; cuando ya está arriba, el resto arrastra la tarjeta.
     */
    internal val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Con la tarjeta ya arrastrada, subir el dedo la devuelve antes de desplazar el texto
            if (!inFling && enabled() && dragPx > 0f && available.y < 0f) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // El texto ya estaba arriba y sobra desplazamiento hacia abajo: arrastra la tarjeta
            if (!inFling && enabled() && available.y > 0f) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // A partir de aquí el desplazamiento es inercia del texto y no debe arrastrar la tarjeta
            inFling = true
            if (dragPx > 0f) {
                release(available.y)
                return available
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            inFling = false
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberOpenGesture(
    enabled: Boolean,
    reduceMotion: Boolean,
    onOpened: () -> Unit
): OpenGesture {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Hay que arrastrar un 38 % de la altura de la pantalla para abrirla del todo
    val distancePx = with(density) { configuration.screenHeightDp.dp.toPx() } * 0.38f

    val currentEnabled by rememberUpdatedState(enabled)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)
    val currentOnOpened by rememberUpdatedState(onOpened)
    val currentDistance by rememberUpdatedState(distancePx)

    return remember(scope) {
        OpenGesture(
            scope = scope,
            distancePx = { currentDistance },
            enabled = { currentEnabled },
            reduceMotion = { currentReduceMotion },
            onOpened = { currentOnOpened() }
        )
    }
}

/** Aplica el gesto a la cápsula. Solo actúa cuando [enabled] es true. */
fun Modifier.openGesture(gesture: OpenGesture, enabled: Boolean): Modifier = this
    .nestedScroll(gesture.nestedScrollConnection)
    .draggable(
        state = gesture.draggableState,
        orientation = Orientation.Vertical,
        enabled = enabled,
        onDragStopped = { velocity -> gesture.onDragStopped(velocity) }
    )

package com.assistant.siri.ui.capsule

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import kotlin.math.max

/**
 * Posición de la cámara frontal, en píxeles y en coordenadas de la ventana.
 * [cutout] es null si el dispositivo no tiene recorte. [safeTop] es la zona alta ocupada
 * por la barra de estado o el recorte.
 */
@Immutable
data class CutoutAnchor(
    val cutout: Rect?,
    val safeTop: Float
)

/**
 * Lee el recorte real con DisplayCutout y WindowInsets. Se actualiza al rotar
 * o cuando cambian los insets.
 */
@Composable
fun rememberCutoutAnchor(): CutoutAnchor {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current

    // Leer estos insets en la composición provoca que se recalcule el ancla cuando cambian
    val cutoutInsets = WindowInsets.displayCutout
    val top = cutoutInsets.getTop(density)
    val bottom = cutoutInsets.getBottom(density)
    val left = cutoutInsets.getLeft(density, layoutDirection)
    val right = cutoutInsets.getRight(density, layoutDirection)
    val statusTop = WindowInsets.statusBars.getTop(density)

    return remember(
        top, bottom, left, right, statusTop,
        configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp
    ) {
        val rects = ViewCompat.getRootWindowInsets(view)?.displayCutout?.boundingRects.orEmpty()
        // Si hay varias zonas, la cámara es la más pequeña
        val camera = rects.filter { !it.isEmpty }.minByOrNull { it.width() * it.height() }
        CutoutAnchor(
            cutout = camera?.let {
                Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
            },
            safeTop = max(statusTop, top).toFloat()
        )
    }
}

internal enum class AnchorEdge { TOP, LEFT, RIGHT }

/** Forma de la cápsula en reposo: centro, tamaño y borde de la pantalla donde está la cámara. */
internal data class RestShape(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val edge: AnchorEdge
) {
    val top: Float get() = centerY - height / 2f
}

/**
 * Calcula la cápsula en reposo a partir del recorte, sin coordenadas fijas.
 * Rodea la cámara con un margen; en vertical queda en el borde superior y en horizontal
 * (cámara en un lateral) se alarga a lo largo de ese borde.
 */
internal fun CutoutAnchor.restShape(
    containerWidth: Float,
    containerHeight: Float,
    density: Float
): RestShape {
    val c = cutout
    if (c == null || c.isEmpty) {
        // Sin recorte: isla virtual arriba al centro, bajo la barra de estado
        val width = 96f * density
        val height = 30f * density
        return RestShape(
            centerX = containerWidth / 2f,
            centerY = safeTop + 6f * density + height / 2f,
            width = width,
            height = height,
            edge = AnchorEdge.TOP
        )
    }

    val distanceTop = c.top
    val distanceLeft = c.left
    val distanceRight = containerWidth - c.right
    val edge = when {
        distanceLeft < distanceTop && distanceLeft <= distanceRight -> AnchorEdge.LEFT
        distanceRight < distanceTop && distanceRight < distanceLeft -> AnchorEdge.RIGHT
        else -> AnchorEdge.TOP
    }

    val along = 26f * density
    val across = 6f * density
    return if (edge == AnchorEdge.TOP) {
        val top = max(0f, c.top - across)
        val height = c.bottom + across - top
        RestShape(
            centerX = c.center.x,
            centerY = top + height / 2f,
            width = c.width + 2f * along,
            height = height,
            edge = edge
        )
    } else {
        RestShape(
            centerX = c.center.x,
            centerY = c.center.y,
            width = c.width + 2f * across,
            height = c.height + 2f * along,
            edge = edge
        )
    }
}

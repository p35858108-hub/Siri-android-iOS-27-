package com.assistant.siri.ui.capsule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.assistant.siri.assistant.AssistantState
import com.assistant.siri.data.settings.AppSettings
import com.assistant.siri.ui.theme.CapsuleColors

/**
 * Pantalla completa con la cápsula encima. Cuando la cápsula no está en reposo, el
 * [content] de la app queda desenfocado y oscurecido detrás.
 *
 * @param onCapsuleClick toque sobre la cápsula (reposo, escucha o procesando).
 * @param onScrimClick toque sobre el fondo desenfocado (normalmente, cerrar el asistente).
 * @param expandedContent contenido de escritura, respuesta y error (Parte 9).
 * @param morph de 0 a 1, se lee al dibujar: convierte la cápsula en la pantalla principal
 * (ver [AssistantCapsule]) y va quitando el desenfoque del fondo.
 * @param capsuleKey al cambiarlo, la cápsula se recrea en reposo sin animación.
 * @param gestureModifier gestos sobre la cápsula.
 */
@Composable
fun AssistantOverlayHost(
    state: AssistantState,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onCapsuleClick: (AssistantState) -> Unit = {},
    onScrimClick: (() -> Unit)? = null,
    morph: () -> Float = { 0f },
    morphColor: Color = CapsuleColors.Surface,
    capsuleKey: Int = 0,
    gestureModifier: Modifier = Modifier,
    expandedContent: @Composable (state: AssistantState, maxHeight: Dp) -> Unit = { _, _ -> },
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize()) {
        AssistantBackdrop(
            active = state !is AssistantState.Idle,
            blurIntensity = settings.blurIntensity,
            reduceMotion = settings.reduceMotion,
            dim = { 1f - morph().coerceIn(0f, 1f) },
            onScrimClick = onScrimClick,
            content = content
        )
        key(capsuleKey) {
            AssistantCapsule(
                state = state,
                settings = settings,
                onClick = onCapsuleClick,
                gestureModifier = gestureModifier,
                morph = morph,
                morphColor = morphColor,
                expandedContent = expandedContent
            )
        }
    }
}

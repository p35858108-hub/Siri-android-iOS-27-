package com.assistant.siri.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import com.assistant.siri.assistant.AssistantState

/**
 * Todo lo que la interfaz puede pedir. La Parte 10 lo conecta con `AssistantViewModel`
 * (por ejemplo, `onSubmit = vm::submit`, `onCancel = vm::cancel`).
 */
@Immutable
data class AssistantActions(
    /** Cada cambio del texto escrito. */
    val onDraftChange: (String) -> Unit = {},
    /** Enviar el texto escrito. */
    val onSubmit: (String) -> Unit = {},
    /** Cancelar lo que esté en curso (escritura o respuesta que está llegando). */
    val onCancel: () -> Unit = {},
    /** Cambiar a entrada por voz. */
    val onMicrophone: () -> Unit = {},
    /** Cerrar la cápsula y volver al reposo. */
    val onDismiss: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSpeak: () -> Unit = {},
    val onStopSpeaking: () -> Unit = {},
    /** Abrir la conversación en la pantalla principal (alternativa al gesto de deslizar). */
    val onOpenApp: () -> Unit = {},
    /** Pedir el permiso de micrófono. */
    val onGrantMicrophone: () -> Unit = {},
    /** Abrir los ajustes de Android de esta app. */
    val onOpenSystemSettings: () -> Unit = {},
    /** Abrir la configuración de proveedores de la app. */
    val onOpenProviderSettings: () -> Unit = {}
)

/**
 * Contenido de la cápsula para escritura, respuesta y error. Se pasa como `expandedContent`
 * a `AssistantOverlayHost`.
 *
 * Mientras la cápsula pasa a otro estado, este contenido se desvanece con el último estado
 * que mostró, así que no desaparece de golpe.
 */
@Composable
fun AssistantExpandedContent(
    state: AssistantState,
    maxHeight: Dp,
    isSpeaking: Boolean,
    actions: AssistantActions
) {
    val holder = remember { LastExpandedState() }
    val shown: AssistantState? = when (state) {
        is AssistantState.Typing,
        is AssistantState.Responding,
        is AssistantState.Error -> {
            holder.last = state
            state
        }

        else -> holder.last
    }

    when (shown) {
        is AssistantState.Typing -> TypingContent(initialText = shown.draft, actions = actions)
        is AssistantState.Responding -> ResponseContent(
            state = shown,
            maxHeight = maxHeight,
            isSpeaking = isSpeaking,
            actions = actions
        )

        is AssistantState.Error -> ErrorContent(state = shown, actions = actions)
        else -> Unit
    }
}

private class LastExpandedState {
    var last: AssistantState? = null
}

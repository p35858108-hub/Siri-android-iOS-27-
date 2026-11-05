package com.assistant.siri.service

import android.view.Window
import android.widget.Toast
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assistant.siri.assistant.AssistantEvent
import com.assistant.siri.assistant.AssistantState
import com.assistant.siri.assistant.AssistantViewModel
import com.assistant.siri.assistant.userMessage
import com.assistant.siri.ui.capsule.AssistantOverlayHost
import com.assistant.siri.ui.capsule.BlurSupport
import com.assistant.siri.ui.capsule.isCrossWindowBlurEnabled
import com.assistant.siri.ui.capsule.openGesture
import com.assistant.siri.ui.capsule.rememberOpenGesture
import com.assistant.siri.ui.capsule.setBehindBlur
import com.assistant.siri.ui.chat.AssistantActions
import com.assistant.siri.ui.chat.AssistantExpandedContent
import com.assistant.siri.voice.VoicePermissions
import kotlin.math.roundToInt

/**
 * Contenido de la sesión del asistente: la cápsula sobre la app que hay debajo.
 *
 * @param window ventana de la sesión, para desenfocar lo que hay detrás.
 * @param onOpenApp abre la app (con la conversación indicada) y cierra la sesión.
 * @param onGrantMicrophone abre la pantalla que pide el permiso de micrófono.
 * @param onClose cierra la sesión.
 */
@Composable
fun AssistantSessionContent(
    vm: AssistantViewModel,
    window: Window?,
    onOpenApp: (Long?) -> Unit,
    onGrantMicrophone: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isSpeaking by vm.isSpeaking.collectAsStateWithLifecycle()
    val conversationId by vm.conversationId.collectAsStateWithLifecycle()

    // Al volver al reposo después de haber estado activo (el usuario cerró el asistente), se cierra la sesión
    var wasActive by remember { mutableStateOf(false) }
    val isIdle = state is AssistantState.Idle
    LaunchedEffect(isIdle) {
        if (isIdle) {
            if (wasActive) onClose()
        } else {
            wasActive = true
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            if (event is AssistantEvent.Problem) {
                Toast.makeText(context, event.error.userMessage(context), Toast.LENGTH_SHORT).show()
            }
        }
    }

    BehindBlurEffect(
        window = window,
        active = !isIdle,
        intensity = settings.blurIntensity,
        reduceMotion = settings.reduceMotion
    )

    // Deslizar la respuesta hacia abajo abre la app con esta conversación
    val gesture = rememberOpenGesture(
        enabled = state is AssistantState.Responding,
        reduceMotion = settings.reduceMotion,
        onOpened = { onOpenApp(conversationId) }
    )

    val actions = AssistantActions(
        onDraftChange = vm::updateDraft,
        onSubmit = vm::submit,
        onCancel = vm::cancel,
        onMicrophone = vm::startListening,
        onDismiss = vm::dismiss,
        onRetry = vm::retry,
        onSpeak = vm::speakResponse,
        onStopSpeaking = vm::stopSpeaking,
        onOpenApp = { gesture.open() },
        onGrantMicrophone = onGrantMicrophone,
        onOpenSystemSettings = {
            VoicePermissions.openAppSettings(context)
            onClose()
        },
        onOpenProviderSettings = { onOpenApp(null) }
    )

    AssistantOverlayHost(
        state = state,
        settings = settings,
        modifier = Modifier.fillMaxSize(),
        onCapsuleClick = { current ->
            when (current) {
                is AssistantState.Idle, is AssistantState.Listening -> vm.startTyping()
                else -> vm.cancel()
            }
        },
        onScrimClick = { vm.dismiss() },
        morph = { gesture.morph },
        morphColor = MaterialTheme.colorScheme.background,
        gestureModifier = Modifier.openGesture(gesture, enabled = state is AssistantState.Responding),
        expandedContent = { current, maxHeight ->
            AssistantExpandedContent(
                state = current,
                maxHeight = maxHeight,
                isSpeaking = isSpeaking,
                actions = actions
            )
        }
    ) {
        // Lo que hay debajo es otra app: aquí solo va el fondo transparente
        Box(Modifier.fillMaxSize())
    }
}

/**
 * Desenfoca lo que hay detrás de la ventana de la sesión (Android 12+, si el sistema permite
 * el desenfoque entre ventanas). Si no, solo se ve la capa oscura del fondo.
 */
@Composable
private fun BehindBlurEffect(
    window: Window?,
    active: Boolean,
    intensity: Float,
    reduceMotion: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var current by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(window, active, intensity, reduceMotion) {
        val target = window ?: return@LaunchedEffect
        if (!BlurSupport.isSupported || !context.isCrossWindowBlurEnabled()) return@LaunchedEffect

        val radius = if (active) {
            with(density) { (6f + 22f * intensity.coerceIn(0f, 1f)).dp.toPx() }
        } else {
            0f
        }
        if (reduceMotion) {
            target.setBehindBlur(radius.roundToInt())
            current = radius
        } else {
            animate(
                initialValue = current,
                targetValue = radius,
                animationSpec = tween(durationMillis = 350)
            ) { value, _ ->
                target.setBehindBlur(value.roundToInt())
                current = value
            }
        }
    }

    DisposableEffect(window) {
        onDispose { window?.setBehindBlur(0) }
    }
}

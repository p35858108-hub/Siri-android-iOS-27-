package com.assistant.siri.ui.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.assistant.siri.R
import com.assistant.siri.assistant.AssistantEvent
import com.assistant.siri.assistant.AssistantState
import com.assistant.siri.assistant.AssistantViewModel
import com.assistant.siri.assistant.userMessage
import com.assistant.siri.ui.capsule.AssistantOverlayHost
import com.assistant.siri.ui.capsule.openGesture
import com.assistant.siri.ui.capsule.rememberOpenGesture
import com.assistant.siri.ui.chat.AssistantActions
import com.assistant.siri.ui.chat.AssistantExpandedContent
import com.assistant.siri.ui.main.ChatScreen
import com.assistant.siri.ui.main.HistoryScreen
import com.assistant.siri.ui.settings.settingsGraph
import com.assistant.siri.voice.VoicePermissions
import com.assistant.siri.voice.rememberMicrophonePermission

private object Routes {
    const val CHAT = "chat"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/**
 * Raíz de la app: navegación entre pantallas, cápsula del asistente encima y conexión
 * con [AssistantViewModel].
 *
 * Hay dos formas de usar el asistente:
 * - Desde la cápsula (tocándola): la pregunta y la respuesta aparecen en la cápsula, con la
 *   app desenfocada detrás. Deslizar la respuesta hacia abajo la convierte en esta pantalla.
 * - Desde la pantalla principal: la respuesta aparece en la propia conversación.
 */
@Composable
fun AssistantApp(
    vm: AssistantViewModel = viewModel(),
    requestedConversationId: Long? = null,
    onRequestHandled: () -> Unit = {}
) {
    val context = LocalContext.current

    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isSpeaking by vm.isSpeaking.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val conversationId by vm.conversationId.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    // Si es true, lo que hace el asistente se muestra en la pantalla principal y no en la cápsula
    var suppressOverlay by remember { mutableStateOf(false) }
    // Al cambiarlo, la cápsula se recrea en reposo sin animación (tras abrir la pantalla principal)
    var capsuleKey by remember { mutableIntStateOf(0) }

    val displayState: AssistantState = if (suppressOverlay) AssistantState.Idle else state
    val overlayVisible = displayState !is AssistantState.Idle

    fun navigateToChat() {
        navController.navigate(Routes.CHAT) {
            popUpTo(Routes.CHAT) { inclusive = false }
            launchSingleTop = true
        }
    }

    // ----- Permiso de micrófono -----
    var pendingMicrophoneAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val microphonePermission = rememberMicrophonePermission { granted ->
        val action = pendingMicrophoneAction
        pendingMicrophoneAction = null
        if (granted) {
            action?.invoke()
        } else {
            // Se muestra el error de permiso en la cápsula, con las opciones para resolverlo
            suppressOverlay = false
            vm.startListening()
        }
    }

    fun withMicrophone(action: () -> Unit) {
        if (VoicePermissions.hasRecordAudio(context)) {
            action()
        } else {
            pendingMicrophoneAction = action
            microphonePermission.request()
        }
    }

    // ----- Gesto de deslizar la respuesta para abrir la pantalla principal -----
    val gesture = rememberOpenGesture(
        enabled = displayState is AssistantState.Responding,
        reduceMotion = settings.reduceMotion,
        onOpened = {
            // Lo que haya en curso (por ejemplo, una respuesta que aún llega) continúa en la pantalla
            suppressOverlay = true
            capsuleKey++
        }
    )

    // A mitad del gesto, la pantalla de debajo pasa a ser la conversación (queda tapada por la cápsula)
    val pastHalfway by remember { derivedStateOf { gesture.morph > 0.5f } }
    LaunchedEffect(pastHalfway) {
        if (pastHalfway) navigateToChat()
    }

    // Conversación pedida desde fuera (por ejemplo, desde el asistente superpuesto a otra app)
    LaunchedEffect(requestedConversationId) {
        if (requestedConversationId != null) {
            suppressOverlay = true
            vm.openConversation(requestedConversationId)
            navigateToChat()
            onRequestHandled()
        }
    }

    // Avisos que no cambian el estado (por ejemplo, fallo al leer en voz alta)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            if (event is AssistantEvent.Problem) {
                Toast.makeText(context, event.error.userMessage(context), Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = overlayVisible) { vm.dismiss() }

    val actions = AssistantActions(
        onDraftChange = vm::updateDraft,
        onSubmit = vm::submit,
        onCancel = vm::cancel,
        onMicrophone = { withMicrophone { vm.startListening() } },
        onDismiss = vm::dismiss,
        onRetry = vm::retry,
        onSpeak = vm::speakResponse,
        onStopSpeaking = vm::stopSpeaking,
        onOpenApp = { gesture.open() },
        onGrantMicrophone = {
            pendingMicrophoneAction = { vm.retry() }
            microphonePermission.request()
        },
        onOpenSystemSettings = { VoicePermissions.openAppSettings(context) },
        onOpenProviderSettings = {
            vm.dismiss()
            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
        }
    )

    AssistantOverlayHost(
        state = displayState,
        settings = settings,
        modifier = Modifier.fillMaxSize(),
        onCapsuleClick = { current ->
            when (current) {
                is AssistantState.Idle -> {
                    suppressOverlay = false
                    vm.startTyping()
                }

                is AssistantState.Listening -> vm.startTyping()
                else -> vm.cancel()
            }
        },
        onScrimClick = { vm.dismiss() },
        morph = { gesture.morph },
        morphColor = MaterialTheme.colorScheme.background,
        capsuleKey = capsuleKey,
        gestureModifier = Modifier.openGesture(gesture, enabled = displayState is AssistantState.Responding),
        expandedContent = { current, maxHeight ->
            AssistantExpandedContent(
                state = current,
                maxHeight = maxHeight,
                isSpeaking = isSpeaking,
                actions = actions
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.CHAT) {
                val currentTitle = history.firstOrNull { it.id == conversationId }
                    ?.title
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.main_title)

                ChatScreen(
                    title = currentTitle,
                    messages = messages,
                    // Mientras el asistente usa la cápsula, la pantalla no muestra su estado
                    state = if (suppressOverlay) state else AssistantState.Idle,
                    actions = actions,
                    onSend = { text ->
                        suppressOverlay = true
                        vm.submit(text)
                    },
                    onMicrophone = {
                        suppressOverlay = true
                        withMicrophone { vm.startListening() }
                    },
                    onStopListening = vm::stopListening,
                    onNewConversation = vm::newConversation,
                    onOpenHistory = {
                        navController.navigate(Routes.HISTORY) { launchSingleTop = true }
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    conversations = history,
                    currentId = conversationId,
                    onOpen = { id ->
                        vm.openConversation(id)
                        navigateToChat()
                    },
                    onDelete = vm::deleteConversation,
                    onNew = {
                        vm.newConversation()
                        navigateToChat()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            settingsGraph(navController = navController, onClearHistory = vm::clearHistory)
        }
    }
}

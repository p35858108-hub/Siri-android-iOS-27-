package com.assistant.siri.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assistant.siri.R
import com.assistant.siri.assistant.AssistantState
import com.assistant.siri.data.chat.ChatMessage
import com.assistant.siri.data.chat.ChatRole
import com.assistant.siri.ui.capsule.ListeningIndicator
import com.assistant.siri.ui.capsule.ThinkingIndicator
import com.assistant.siri.ui.chat.AssistantActions
import com.assistant.siri.ui.chat.ErrorContent
import com.assistant.siri.ui.chat.MarkdownText
import com.assistant.siri.ui.theme.CapsuleColors

/**
 * Pantalla principal: la conversación completa. Las respuestas usan el mismo negro y el mismo
 * texto que la cápsula, para que parezcan el mismo elemento en otro tamaño.
 *
 * @param state estado del asistente cuando la respuesta se muestra aquí (no en la cápsula).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String,
    messages: List<ChatMessage>,
    state: AssistantState,
    actions: AssistantActions,
    onSend: (String) -> Unit,
    onMicrophone: () -> Unit,
    onStopListening: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = onNewConversation) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.new_conversation))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Rounded.History, stringResource(R.string.history_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val isEmpty = messages.isEmpty() &&
                    state !is AssistantState.Processing &&
                    state !is AssistantState.Error
                if (isEmpty) {
                    EmptyChat()
                } else {
                    MessageList(messages = messages, state = state, actions = actions)
                }
            }
            ChatInputBar(
                state = state,
                onSend = onSend,
                onMicrophone = onMicrophone,
                onStop = actions.onCancel,
                onStopListening = onStopListening
            )
        }
    }
}

@Composable
private fun EmptyChat() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(width = 72.dp, height = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.chat_empty),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    state: AssistantState,
    actions: AssistantActions
) {
    val listState = rememberLazyListState()
    // En una lista invertida el primer elemento es el de abajo (el más reciente)
    val newestFirst = remember(messages) { messages.asReversed() }
    val streamingId = if (state is AssistantState.Responding && state.isStreaming) {
        messages.lastOrNull()?.takeIf { it.role == ChatRole.ASSISTANT }?.id
    } else {
        null
    }

    // Al llegar un mensaje nuevo (o el aviso de pensando o de error) baja al final
    LaunchedEffect(
        newestFirst.firstOrNull()?.id,
        state is AssistantState.Processing,
        state is AssistantState.Error
    ) {
        listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (state is AssistantState.Error) {
            item(key = "error") {
                AssistantSurface {
                    ErrorContent(state = state, actions = actions)
                }
            }
        }
        if (state is AssistantState.Processing) {
            item(key = "thinking") {
                AssistantSurface {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        ThinkingIndicator(animate = true)
                    }
                }
            }
        }
        items(newestFirst, key = { it.id }) { message ->
            if (message.role == ChatRole.USER) {
                UserMessage(message)
            } else {
                AssistantMessage(message, streaming = message.id == streamingId)
            }
        }
    }
}

/** Contenedor negro de las respuestas: el mismo material que la cápsula. */
@Composable
private fun AssistantSurface(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Surface(
            color = CapsuleColors.Surface,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
            modifier = Modifier.widthIn(max = 640.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, streaming: Boolean) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        AssistantSurface {
            SelectionContainer {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    MarkdownText(text = message.content)
                    if (streaming) {
                        Spacer(Modifier.height(10.dp))
                        ThinkingIndicator(animate = true, color = CapsuleColors.TextSecondary)
                    }
                }
            }
        }
        if (!streaming) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(message.content)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    state: AssistantState,
    onSend: (String) -> Unit,
    onMicrophone: () -> Unit,
    onStop: () -> Unit,
    onStopListening: () -> Unit
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val listening = state as? AssistantState.Listening
    val busy = state is AssistantState.Processing ||
        (state is AssistantState.Responding && state.isStreaming)

    fun send() {
        val text = field.text.trim()
        if (text.isNotEmpty() && !busy) {
            field = TextFieldValue()
            onSend(text)
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (listening != null) {
                Box(Modifier.padding(start = 12.dp)) {
                    ListeningIndicator(
                        level = listening.level,
                        animate = true,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = listening.partialText.ifBlank { stringResource(R.string.state_listening) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel))
                }
                FilledIconButton(onClick = onStopListening) {
                    Icon(Icons.Rounded.Stop, stringResource(R.string.action_stop_listening))
                }
            } else {
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.input_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                when {
                    busy -> FilledIconButton(onClick = onStop) {
                        Icon(Icons.Rounded.Stop, stringResource(R.string.action_cancel))
                    }

                    field.text.isNotBlank() -> FilledIconButton(onClick = { send() }) {
                        Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.action_send))
                    }

                    else -> IconButton(onClick = onMicrophone) {
                        Icon(Icons.Rounded.Mic, stringResource(R.string.action_microphone))
                    }
                }
            }
        }
    }
}

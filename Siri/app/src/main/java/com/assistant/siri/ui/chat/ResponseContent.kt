package com.assistant.siri.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.siri.R
import com.assistant.siri.assistant.AssistantState
import com.assistant.siri.ui.capsule.ThinkingIndicator
import com.assistant.siri.ui.theme.CapsuleColors
import kotlinx.coroutines.delay

/**
 * Contenedor de respuesta: pregunta arriba, texto con formato debajo y acciones al pie.
 * Se adapta a la longitud del texto; si supera [maxHeight], el texto se desplaza dentro.
 */
@Composable
fun ResponseContent(
    state: AssistantState.Responding,
    maxHeight: Dp,
    isSpeaking: Boolean,
    actions: AssistantActions,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    // Espacio para la cabecera, las acciones y los márgenes
    val textMaxHeight = (maxHeight - 150.dp).coerceAtLeast(96.dp)
    var follow by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // Si el usuario se desplaza hacia arriba deja de seguirse el final; si vuelve abajo, se retoma
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) follow = scrollState.value >= scrollState.maxValue - 24
        }
    }
    // Mientras llega la respuesta, la vista sigue el final del texto
    LaunchedEffect(scrollState, state.isStreaming) {
        if (state.isStreaming) {
            snapshotFlow { scrollState.maxValue }.collect { max ->
                if (follow) scrollState.scrollTo(max)
            }
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp)
    ) {
        // Cabecera: la pregunta y cerrar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.userText,
                style = MaterialTheme.typography.labelLarge,
                color = CapsuleColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            CapsuleIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
                onClick = actions.onDismiss
            )
        }

        // Respuesta
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = textMaxHeight)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            ) {
                MarkdownText(text = state.text)
                if (state.isStreaming) {
                    Spacer(Modifier.size(10.dp))
                    ThinkingIndicator(animate = true, color = CapsuleColors.TextSecondary)
                }
            }
        }

        // Acciones
        if (state.isStreaming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                CapsuleTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = actions.onCancel
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CapsuleIconButton(
                    icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    onClick = {
                        clipboard.setText(AnnotatedString(state.text))
                        copied = true
                    }
                )
                CapsuleIconButton(
                    icon = if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(
                        if (isSpeaking) R.string.action_stop_speaking else R.string.action_speak
                    ),
                    onClick = if (isSpeaking) actions.onStopSpeaking else actions.onSpeak
                )
                Spacer(Modifier.weight(1f))
                CapsuleTextButton(
                    text = stringResource(R.string.action_open_app),
                    onClick = actions.onOpenApp
                )
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = null,
                    tint = CapsuleColors.TextSecondary,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(16.dp)
                )
            }

            // Barra de agarre: indica que se puede deslizar hacia abajo
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .width(36.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )
        }
    }
}

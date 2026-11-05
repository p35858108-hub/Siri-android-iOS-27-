package com.assistant.siri.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.assistant.siri.R
import com.assistant.siri.ui.theme.CapsuleColors
import kotlinx.coroutines.delay

/**
 * Modo de escritura dentro de la cápsula: cancelar, campo de texto, micrófono y enviar.
 * El texto se guarda en un estado local (así el cursor nunca salta) y se avisa de cada
 * cambio con [AssistantActions.onDraftChange].
 */
@Composable
fun TypingContent(
    initialText: String,
    actions: AssistantActions,
    modifier: Modifier = Modifier
) {
    var field by remember {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSend = field.text.isNotBlank()

    fun send() {
        val text = field.text.trim()
        if (text.isNotEmpty()) {
            keyboard?.hide()
            actions.onSubmit(text)
        }
    }

    // Foco y teclado al abrir; el teclado se cierra al salir de este modo
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CapsuleIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.action_cancel),
            onClick = {
                keyboard?.hide()
                actions.onCancel()
            }
        )

        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                actions.onDraftChange(it.text)
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = CapsuleColors.TextPrimary),
            cursorBrush = SolidColor(CapsuleColors.Accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { send() }),
            maxLines = 5,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.heightIn(min = 44.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (field.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.input_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = CapsuleColors.TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )

        CapsuleIconButton(
            icon = Icons.Rounded.Mic,
            contentDescription = stringResource(R.string.action_microphone),
            onClick = {
                keyboard?.hide()
                actions.onMicrophone()
            }
        )

        FilledIconButton(
            onClick = { send() },
            enabled = canSend,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color(0x1FFFFFFF),
                disabledContentColor = CapsuleColors.TextSecondary
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.action_send),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

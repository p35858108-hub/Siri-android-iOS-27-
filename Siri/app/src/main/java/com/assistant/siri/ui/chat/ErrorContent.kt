package com.assistant.siri.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.siri.R
import com.assistant.siri.ai.AiError
import com.assistant.siri.assistant.AssistantError
import com.assistant.siri.assistant.AssistantState
import com.assistant.siri.assistant.userMessage
import com.assistant.siri.ui.theme.CapsuleColors
import com.assistant.siri.voice.SpeechError

/** Aviso de error con las acciones que tienen sentido para cada causa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorContent(
    state: AssistantState.Error,
    actions: AssistantActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val message = remember(state.error) { state.error.userMessage(context) }

    val error = state.error
    val permissionDenied = error is AssistantError.Speech &&
        error.exception.error == SpeechError.PERMISSION_DENIED
    val needsProviderSetup = error is AssistantError.NoProvider ||
        (error is AssistantError.Ai && error.exception.error in PROVIDER_SETUP_ERRORS)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .size(8.dp)
                    .background(CapsuleColors.Error, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CapsuleColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
        ) {
            if (permissionDenied) {
                CapsuleTextButton(
                    text = stringResource(R.string.permission_grant),
                    onClick = actions.onGrantMicrophone,
                    emphasized = true
                )
                CapsuleTextButton(
                    text = stringResource(R.string.permission_open_settings),
                    onClick = actions.onOpenSystemSettings
                )
            } else if (needsProviderSetup) {
                CapsuleTextButton(
                    text = stringResource(R.string.settings_title),
                    onClick = actions.onOpenProviderSettings,
                    emphasized = true
                )
            }
            if (state.canRetry) {
                CapsuleTextButton(
                    text = stringResource(R.string.action_retry),
                    onClick = actions.onRetry,
                    emphasized = !permissionDenied && !needsProviderSetup
                )
            }
            CapsuleTextButton(
                text = stringResource(R.string.action_close),
                onClick = actions.onDismiss
            )
        }
    }
}

private val PROVIDER_SETUP_ERRORS = setOf(
    AiError.NO_API_KEY,
    AiError.INVALID_URL,
    AiError.INVALID_CONFIG,
    AiError.AUTH
)

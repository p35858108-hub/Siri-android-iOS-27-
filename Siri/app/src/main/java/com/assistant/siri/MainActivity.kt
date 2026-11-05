package com.assistant.siri

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.assistant.siri.ui.app.AssistantApp
import com.assistant.siri.ui.theme.AssistantTheme

class MainActivity : ComponentActivity() {

    /** Conversación que hay que abrir (pedida desde el asistente superpuesto), o null. */
    private var requestedConversationId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pantalla completa: la cápsula se dibuja sobre la zona de la cámara
        enableEdgeToEdge()
        requestedConversationId = conversationIdFrom(intent)
        setContent {
            AssistantTheme {
                AssistantApp(
                    requestedConversationId = requestedConversationId,
                    onRequestHandled = { requestedConversationId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedConversationId = conversationIdFrom(intent)
    }

    private fun conversationIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_CONVERSATION_ID, -1L)?.takeIf { it >= 0L }

    companion object {
        /** Id de la conversación que debe mostrar la pantalla principal al abrirse. */
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}

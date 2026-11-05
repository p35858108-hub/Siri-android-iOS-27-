package com.assistant.siri.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Base común de los adaptadores HTTP con streaming SSE.
 * Cada adaptador solo define cómo construir la petición y cómo leer un fragmento.
 */
abstract class HttpAiProvider(protected val client: OkHttpClient) : AiProvider {

    /** Construye la petición HTTP. Recibe solo mensajes con contenido. */
    protected abstract fun buildRequest(config: ProviderConfig, messages: List<AiMessage>): Request

    /**
     * Interpreta un fragmento "data:" y devuelve el texto nuevo, o null si no hay.
     * Lanza [AiException] si el fragmento contiene un error del servidor.
     */
    protected abstract fun parseChunk(data: String): String?

    final override fun stream(config: ProviderConfig, messages: List<AiMessage>): Flow<String> = flow {
        config.validationError()?.let { throw AiException(it) }

        val clean = messages.filter { it.content.isNotBlank() }
        if (clean.none { it.role != AiRole.SYSTEM }) throw AiException(AiError.INVALID_CONFIG)

        val request = buildRequest(config, clean)
        var received = false
        client.sseData(request).collect { data ->
            val text = parseChunk(data)
            if (!text.isNullOrEmpty()) {
                received = true
                emit(text)
            }
        }
        if (!received) throw AiException(AiError.EMPTY_RESPONSE)
    }

    /** Une la URL base con el sufijo, sin duplicarlo si el usuario ya lo incluyó. */
    protected fun endpoint(baseUrl: String, suffix: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith(suffix)) base else base + suffix
    }

    protected fun systemText(messages: List<AiMessage>): String =
        messages.filter { it.role == AiRole.SYSTEM }.joinToString("\n\n") { it.content }
}

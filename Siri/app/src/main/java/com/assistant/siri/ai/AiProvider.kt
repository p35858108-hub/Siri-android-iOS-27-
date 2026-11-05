package com.assistant.siri.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import okhttp3.OkHttpClient

/**
 * Contrato común de todos los proveedores.
 * Los errores se lanzan como [AiException].
 */
interface AiProvider {

    val protocol: AiProtocol

    /**
     * Emite el texto de la respuesta por fragmentos a medida que llega.
     * Cancelar la recogida del Flow cancela la petición HTTP.
     */
    fun stream(config: ProviderConfig, messages: List<AiMessage>): Flow<String>

    /** Respuesta completa, concatenando todos los fragmentos. */
    suspend fun complete(config: ProviderConfig, messages: List<AiMessage>): String {
        val builder = StringBuilder()
        stream(config, messages).collect { builder.append(it) }
        return builder.toString()
    }

    /** Comprueba que la URL, la clave y el modelo funcionan. Lanza [AiException] si no. */
    suspend fun testConnection(config: ProviderConfig) {
        val reply = complete(
            config,
            listOf(AiMessage(AiRole.USER, "Responde únicamente con la palabra: ok"))
        )
        if (reply.isBlank()) throw AiException(AiError.EMPTY_RESPONSE)
    }
}

/** Crea el adaptador adecuado según el protocolo del perfil. */
object AiProviderFactory {

    fun create(
        protocol: AiProtocol,
        client: OkHttpClient = AiHttpClient.shared
    ): AiProvider = when (protocol) {
        AiProtocol.OPENAI -> OpenAiCompatibleProvider(client)
        AiProtocol.GEMINI -> GeminiProvider(client)
        AiProtocol.ANTHROPIC -> AnthropicProvider(client)
    }

    fun forConfig(config: ProviderConfig): AiProvider = create(config.protocol)
}

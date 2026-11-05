package com.assistant.siri.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** Protocolo de red que habla el proveedor. */
@Serializable
enum class AiProtocol {
    OPENAI,
    GEMINI,
    ANTHROPIC
}

/**
 * Tipo de proveedor. Los valores de [suggestedBaseUrl] son solo sugerencias
 * para rellenar el formulario: los adaptadores siempre usan la URL guardada
 * en [ProviderConfig.baseUrl], que el usuario puede editar libremente.
 */
@Serializable
enum class ProviderType(
    val displayName: String,
    val suggestedBaseUrl: String,
    val defaultProtocol: AiProtocol
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", AiProtocol.OPENAI),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", AiProtocol.GEMINI),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", AiProtocol.ANTHROPIC),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", AiProtocol.OPENAI),
    CUSTOM("Personalizado", "", AiProtocol.OPENAI)
}

/**
 * Perfil de un proveedor de IA.
 *
 * [apiKey] está marcada como @Transient: nunca se serializa a JSON, de modo que
 * no puede acabar guardada en claro por accidente. La Parte 4 la guarda aparte
 * cifrada con Android Keystore y la vuelve a asignar al leer el perfil.
 */
@Serializable
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    @Transient val apiKey: String = "",
    val modelId: String,
    val protocol: AiProtocol
) {
    /** Oculta la clave para que no aparezca en logs. */
    override fun toString(): String =
        "ProviderConfig(id=$id, name=$name, type=$type, baseUrl=$baseUrl, " +
            "apiKey=${if (apiKey.isBlank()) "<vacía>" else "<oculta>"}, " +
            "modelId=$modelId, protocol=$protocol)"
}

/**
 * Devuelve el primer problema de configuración, o null si el perfil es válido.
 * En el protocolo compatible con OpenAI la clave es opcional (servidores locales).
 */
fun ProviderConfig.validationError(): AiError? = when {
    baseUrl.trim().toHttpUrlOrNull() == null -> AiError.INVALID_URL
    modelId.isBlank() -> AiError.INVALID_CONFIG
    protocol != AiProtocol.OPENAI && apiKey.isBlank() -> AiError.NO_API_KEY
    else -> null
}

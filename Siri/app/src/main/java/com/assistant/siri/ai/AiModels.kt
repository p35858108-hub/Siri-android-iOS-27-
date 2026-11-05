package com.assistant.siri.ai

import android.content.Context
import com.assistant.siri.R

enum class AiRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/** Mensaje que se envía al modelo. */
data class AiMessage(
    val role: AiRole,
    val content: String
)

enum class AiError {
    NO_API_KEY,
    INVALID_URL,
    INVALID_CONFIG,
    NETWORK,
    TIMEOUT,
    AUTH,
    RATE_LIMIT,
    SERVER,
    EMPTY_RESPONSE,
    UNKNOWN
}

/** Error tipado de cualquier proveedor. [detail] es el mensaje del servidor, si lo hay. */
class AiException(
    val error: AiError,
    val httpCode: Int? = null,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(detail ?: error.name, cause)

/** Texto legible para mostrar al usuario, usando los recursos de la Parte 2. */
fun AiException.userMessage(context: Context): String {
    val base = when (error) {
        AiError.NO_API_KEY -> context.getString(R.string.error_no_api_key)
        AiError.INVALID_URL -> context.getString(R.string.error_invalid_url)
        AiError.INVALID_CONFIG -> context.getString(R.string.error_no_provider)
        AiError.NETWORK -> context.getString(R.string.error_network)
        AiError.TIMEOUT -> context.getString(R.string.error_timeout)
        AiError.AUTH -> context.getString(R.string.error_auth)
        AiError.RATE_LIMIT -> context.getString(R.string.error_rate_limit)
        AiError.SERVER -> context.getString(R.string.error_server, httpCode ?: 0)
        AiError.EMPTY_RESPONSE -> context.getString(R.string.error_empty_response)
        AiError.UNKNOWN -> context.getString(R.string.error_unknown)
    }
    val extra = detail?.takeIf { it.isNotBlank() }
    return if (extra != null) "$base\n$extra" else base
}

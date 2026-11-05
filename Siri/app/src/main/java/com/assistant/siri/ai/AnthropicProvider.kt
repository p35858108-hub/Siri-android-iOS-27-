package com.assistant.siri.ai

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * API de mensajes de Anthropic con streaming.
 * URL base de ejemplo: https://api.anthropic.com/v1
 */
class AnthropicProvider(client: OkHttpClient) : HttpAiProvider(client) {

    override val protocol: AiProtocol = AiProtocol.ANTHROPIC

    override fun buildRequest(config: ProviderConfig, messages: List<AiMessage>): Request {
        val system = systemText(messages)
        val chat = messages.filter { it.role != AiRole.SYSTEM }

        val body = buildJsonObject {
            put("model", config.modelId.trim())
            put("max_tokens", MAX_TOKENS)
            put("stream", true)
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                chat.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == AiRole.USER) "user" else "assistant")
                        put("content", m.content)
                    }
                }
            }
        }

        return Request.Builder()
            .url(endpoint(config.baseUrl, "/messages"))
            .header("x-api-key", config.apiKey.trim())
            .header("anthropic-version", API_VERSION)
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody())
            .build()
    }

    override fun parseChunk(data: String): String? {
        val obj = parseJsonObject(data) ?: return null

        return when (obj["type"].asString()) {
            "content_block_delta" -> {
                val delta = obj["delta"].asObject()
                if (delta?.get("type").asString() == "text_delta") {
                    delta?.get("text").asString()
                } else {
                    null
                }
            }

            "error" -> {
                val kind = when (obj["error"].asObject()?.get("type").asString()) {
                    "authentication_error", "permission_error" -> AiError.AUTH
                    "rate_limit_error" -> AiError.RATE_LIMIT
                    else -> AiError.SERVER
                }
                throw AiException(kind, detail = errorDetail(obj["error"]))
            }

            else -> null
        }
    }

    private companion object {
        const val API_VERSION = "2023-06-01"
        const val MAX_TOKENS = 4096
    }
}

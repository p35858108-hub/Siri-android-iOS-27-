package com.assistant.siri.ai

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cualquier servicio compatible con la API de Chat Completions de OpenAI
 * (OpenAI, OpenRouter, servidores locales, etc.).
 * URL base de ejemplo: https://api.openai.com/v1
 */
class OpenAiCompatibleProvider(client: OkHttpClient) : HttpAiProvider(client) {

    override val protocol: AiProtocol = AiProtocol.OPENAI

    override fun buildRequest(config: ProviderConfig, messages: List<AiMessage>): Request {
        val body = buildJsonObject {
            put("model", config.modelId.trim())
            put("stream", true)
            putJsonArray("messages") {
                messages.forEach { m ->
                    addJsonObject {
                        put("role", roleName(m.role))
                        put("content", m.content)
                    }
                }
            }
        }

        val builder = Request.Builder()
            .url(endpoint(config.baseUrl, "/chat/completions"))
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody())

        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        return builder.build()
    }

    override fun parseChunk(data: String): String? {
        if (data == "[DONE]") return null
        val obj = parseJsonObject(data) ?: return null

        obj["error"]?.let { throw AiException(AiError.SERVER, detail = errorDetail(it)) }

        val choice = obj["choices"].asArray()?.firstOrNull().asObject() ?: return null
        return choice["delta"].asObject()?.get("content").asString()
    }

    private fun roleName(role: AiRole): String = when (role) {
        AiRole.SYSTEM -> "system"
        AiRole.USER -> "user"
        AiRole.ASSISTANT -> "assistant"
    }
}

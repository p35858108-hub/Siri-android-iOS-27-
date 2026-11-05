package com.assistant.siri.ai

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * API nativa de Gemini (generateContent con streaming).
 * URL base de ejemplo: https://generativelanguage.googleapis.com/v1beta
 */
class GeminiProvider(client: OkHttpClient) : HttpAiProvider(client) {

    override val protocol: AiProtocol = AiProtocol.GEMINI

    override fun buildRequest(config: ProviderConfig, messages: List<AiMessage>): Request {
        val system = systemText(messages)
        val chat = messages.filter { it.role != AiRole.SYSTEM }

        val body = buildJsonObject {
            if (system.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", system) }
                    }
                }
            }
            putJsonArray("contents") {
                chat.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == AiRole.USER) "user" else "model")
                        putJsonArray("parts") {
                            addJsonObject { put("text", m.content) }
                        }
                    }
                }
            }
        }

        val model = config.modelId.trim().removePrefix("models/")
        val base = config.baseUrl.trim().trimEnd('/')

        return Request.Builder()
            .url("$base/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", config.apiKey.trim())
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody())
            .build()
    }

    override fun parseChunk(data: String): String? {
        val obj = parseJsonObject(data) ?: return null

        obj["error"]?.let { throw AiException(AiError.SERVER, detail = errorDetail(it)) }

        val blockReason = obj["promptFeedback"].asObject()?.get("blockReason").asString()
        if (blockReason != null) {
            throw AiException(AiError.EMPTY_RESPONSE, detail = blockReason)
        }

        val parts = obj["candidates"].asArray()
            ?.firstOrNull().asObject()
            ?.get("content").asObject()
            ?.get("parts").asArray()
            ?: return null

        val text = StringBuilder()
        for (part in parts) {
            val p = part.asObject() ?: continue
            // Se omiten los fragmentos de "pensamiento" interno del modelo
            if ((p["thought"] as? JsonPrimitive)?.booleanOrNull == true) continue
            p["text"].asString()?.let { text.append(it) }
        }
        return if (text.isEmpty()) null else text.toString()
    }
}

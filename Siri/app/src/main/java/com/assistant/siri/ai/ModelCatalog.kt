package com.assistant.siri.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pide al proveedor la lista de modelos disponibles.
 * Funciona con los tres protocolos; los fallos se lanzan como [AiException].
 */
object ModelCatalog {

    suspend fun fetch(
        config: ProviderConfig,
        client: OkHttpClient = AiHttpClient.shared
    ): List<String> {
        if (config.baseUrl.trim().toHttpUrlOrNull() == null) throw AiException(AiError.INVALID_URL)
        if (config.protocol != AiProtocol.OPENAI && config.apiKey.isBlank()) {
            throw AiException(AiError.NO_API_KEY)
        }

        val body = client.awaitBody(buildRequest(config))
        val root = try {
            aiJson.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw AiException(AiError.EMPTY_RESPONSE)

        val ids = when (config.protocol) {
            AiProtocol.GEMINI -> root["models"].asArray().orEmpty().mapNotNull { element ->
                val model = element.asObject() ?: return@mapNotNull null
                // Solo los modelos que pueden generar texto
                val methods = model["supportedGenerationMethods"].asArray()
                if (methods != null && methods.none { it.asString() == "generateContent" }) {
                    return@mapNotNull null
                }
                model["name"].asString()?.removePrefix("models/")
            }

            AiProtocol.OPENAI, AiProtocol.ANTHROPIC -> root["data"].asArray().orEmpty().mapNotNull {
                it.asObject()?.get("id").asString()
            }
        }
        return ids.distinct().sortedBy { it.lowercase() }
    }

    private fun buildRequest(config: ProviderConfig): Request {
        val base = config.baseUrl.trim().trimEnd('/')
        val key = config.apiKey.trim()
        val builder = Request.Builder()
        when (config.protocol) {
            AiProtocol.OPENAI -> {
                builder.url("$base/models")
                if (key.isNotEmpty()) builder.header("Authorization", "Bearer $key")
            }

            AiProtocol.GEMINI -> {
                builder.url("$base/models?pageSize=1000")
                builder.header("x-goog-api-key", key)
            }

            AiProtocol.ANTHROPIC -> {
                builder.url("$base/models?limit=1000")
                builder.header("x-api-key", key)
                builder.header("anthropic-version", "2023-06-01")
            }
        }
        return builder.get().build()
    }

    private suspend fun OkHttpClient.awaitBody(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ioToAiException(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        try {
                            val raw = resp.body?.string().orEmpty()
                            if (!continuation.isActive) return
                            if (resp.isSuccessful) {
                                continuation.resume(raw)
                            } else {
                                continuation.resumeWithException(httpError(resp.code, raw))
                            }
                        } catch (e: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(ioToAiException(e))
                        }
                    }
                }
            })
        }

    private fun httpError(code: Int, raw: String): AiException {
        val parsed = parseJsonObject(raw)
        val detail = if (parsed != null) {
            errorDetail(parsed["error"])
        } else {
            raw.trim().take(240).ifBlank { null }
        }
        val error = when {
            code == 401 || code == 403 -> AiError.AUTH
            code == 400 && detail?.contains("api key", ignoreCase = true) == true -> AiError.AUTH
            code == 408 -> AiError.TIMEOUT
            code == 429 -> AiError.RATE_LIMIT
            else -> AiError.SERVER
        }
        return AiException(error, httpCode = code, detail = detail)
    }
}

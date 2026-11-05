package com.assistant.siri.ai

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

internal val aiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Cliente HTTP compartido por todos los adaptadores. */
object AiHttpClient {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Tiempo máximo entre dos fragmentos del streaming
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

internal fun JsonObject.toRequestBody(): RequestBody = toString().toRequestBody(JSON_MEDIA_TYPE)

// ---------- Ayudas para leer JSON sin lanzar excepciones ----------

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun parseJsonObject(data: String): JsonObject? =
    try {
        aiJson.parseToJsonElement(data) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

/** Extrae el texto de un campo "error" con formato {message: ...} o un texto simple. */
internal fun errorDetail(error: JsonElement?): String? {
    val text = when (error) {
        is JsonObject -> error["message"].asString()
        is JsonPrimitive -> error.contentOrNull
        else -> null
    }
    return text?.trim()?.take(240)?.ifBlank { null }
}

// ---------- Streaming SSE ----------

/**
 * Lanza la petición y emite el contenido de cada línea "data:" (Server-Sent Events).
 * Al cancelar la recogida se cancela la llamada HTTP.
 * Los fallos se transforman en [AiException].
 */
internal fun OkHttpClient.sseData(request: Request): Flow<String> = callbackFlow {
    val call = newCall(request)
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            close(ioToAiException(e))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
                try {
                    if (!resp.isSuccessful) {
                        close(httpToAiException(resp))
                    } else {
                        val source = resp.body?.source()
                        if (source == null) {
                            close(AiException(AiError.EMPTY_RESPONSE))
                        } else {
                            while (!call.isCanceled()) {
                                val line = source.readUtf8Line() ?: break
                                if (line.startsWith("data:")) {
                                    val payload = line.removePrefix("data:").trim()
                                    if (payload.isNotEmpty()) trySend(payload)
                                }
                            }
                            close()
                        }
                    }
                } catch (e: IOException) {
                    if (call.isCanceled()) close() else close(ioToAiException(e))
                }
            }
        }
    })
    awaitClose { call.cancel() }
}.buffer(Channel.UNLIMITED)

internal fun ioToAiException(e: IOException): AiException =
    when (e) {
        is InterruptedIOException -> AiException(AiError.TIMEOUT, cause = e)
        else -> AiException(AiError.NETWORK, cause = e)
    }

private fun httpToAiException(response: Response): AiException {
    val code = response.code
    val raw = try {
        response.body?.string().orEmpty()
    } catch (e: IOException) {
        ""
    }
    val parsed = try {
        aiJson.parseToJsonElement(raw) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
    val detail = if (parsed != null) {
        errorDetail(parsed["error"])
    } else {
        raw.trim().take(240).ifBlank { null }
    }

    val error = when {
        code == 401 || code == 403 -> AiError.AUTH
        // Gemini responde 400 cuando la clave de API no es válida
        code == 400 && detail?.contains("api key", ignoreCase = true) == true -> AiError.AUTH
        code == 408 -> AiError.TIMEOUT
        code == 429 -> AiError.RATE_LIMIT
        else -> AiError.SERVER
    }
    return AiException(error, httpCode = code, detail = detail)
}

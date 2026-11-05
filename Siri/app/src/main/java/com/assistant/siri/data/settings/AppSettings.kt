package com.assistant.siri.data.settings

const val DEFAULT_SYSTEM_PROMPT =
    "Eres un asistente de IA integrado en el móvil. Responde en el idioma del usuario, " +
        "de forma clara y concisa, con párrafos cortos y sin relleno."

/** Preferencias de la app. */
data class AppSettings(
    /** Leer las respuestas en voz alta. */
    val speakResponses: Boolean = false,
    /** Etiqueta de idioma BCP-47 para voz (por ejemplo "es-ES"). Vacía = idioma del sistema. */
    val voiceLanguageTag: String = "",
    /** Velocidad de lectura, de 0.5 a 2.0. */
    val speechRate: Float = 1.0f,
    /** Tono de la voz, de 0.5 a 2.0. */
    val speechPitch: Float = 1.0f,
    /** Intensidad del desenfoque, de 0.0 a 1.0. */
    val blurIntensity: Float = 0.7f,
    /** Luz sutil en el contorno de la cápsula. */
    val capsuleGlow: Boolean = true,
    /** Reduce las animaciones. */
    val reduceMotion: Boolean = false,
    /** Instrucciones de sistema enviadas al modelo en cada conversación. */
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
)

/** Ajusta los valores a sus rangos válidos. */
fun AppSettings.normalized(): AppSettings = copy(
    voiceLanguageTag = voiceLanguageTag.trim(),
    speechRate = speechRate.coerceIn(0.5f, 2.0f),
    speechPitch = speechPitch.coerceIn(0.5f, 2.0f),
    blurIntensity = blurIntensity.coerceIn(0f, 1f),
    systemPrompt = systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }
)

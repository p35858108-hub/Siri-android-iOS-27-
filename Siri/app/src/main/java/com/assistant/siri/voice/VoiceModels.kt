package com.assistant.siri.voice

import android.content.Context
import com.assistant.siri.R

/** Eventos del reconocimiento de voz. */
sealed interface SpeechEvent {
    /** El micrófono está listo: ya se puede hablar. */
    data object Ready : SpeechEvent

    /** Se ha detectado el comienzo de la voz. */
    data object VoiceDetected : SpeechEvent

    /** Intensidad del sonido, de 0.0 (silencio) a 1.0 (fuerte). */
    data class Level(val value: Float) : SpeechEvent

    /** Texto parcial mientras el usuario sigue hablando. */
    data class Partial(val text: String) : SpeechEvent

    /** Texto final reconocido. Es el último evento del Flow. */
    data class Final(val text: String) : SpeechEvent
}

enum class SpeechError {
    PERMISSION_DENIED,
    RECOGNIZER_UNAVAILABLE,
    NO_MATCH,
    TIMEOUT,
    BUSY,
    NETWORK,
    AUDIO,
    LANGUAGE_UNAVAILABLE,
    TTS_UNAVAILABLE,
    TTS_FAILED,
    UNKNOWN
}

/** Error tipado de reconocimiento o síntesis de voz. */
class SpeechException(
    val error: SpeechError,
    cause: Throwable? = null
) : Exception(error.name, cause)

/** Texto legible para mostrar al usuario, con los recursos de la Parte 2. */
fun SpeechException.userMessage(context: Context): String = when (error) {
    SpeechError.PERMISSION_DENIED -> context.getString(R.string.permission_mic_rationale)
    SpeechError.RECOGNIZER_UNAVAILABLE -> context.getString(R.string.error_speech_not_available)
    SpeechError.NO_MATCH -> context.getString(R.string.error_speech_no_match)
    SpeechError.TIMEOUT -> context.getString(R.string.error_speech_timeout)
    SpeechError.BUSY -> context.getString(R.string.error_speech_busy)
    SpeechError.NETWORK -> context.getString(R.string.error_speech_network)
    SpeechError.LANGUAGE_UNAVAILABLE -> context.getString(R.string.error_tts_language)
    SpeechError.TTS_UNAVAILABLE, SpeechError.TTS_FAILED ->
        context.getString(R.string.error_tts_not_available)
    SpeechError.AUDIO, SpeechError.UNKNOWN -> context.getString(R.string.error_unknown)
}

package com.assistant.siri.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Captura de voz con [SpeechRecognizer].
 * Todo ocurre en el hilo principal, como exige Android.
 */
class SpeechRecognizerManager(context: Context) {

    private val appContext = context.applicationContext

    /** Reconocedor en uso. Solo se toca desde el hilo principal. */
    private var active: SpeechRecognizer? = null

    /** True si el dispositivo tiene un servicio de reconocimiento de voz instalado. */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun hasPermission(): Boolean = VoicePermissions.hasRecordAudio(appContext)

    /**
     * Empieza a escuchar y emite eventos. El Flow termina tras [SpeechEvent.Final]
     * o falla con [SpeechException]. Cancelar la recogida detiene el micrófono.
     *
     * @param languageTag etiqueta BCP-47 (por ejemplo "es-ES"). Vacía = idioma del sistema.
     */
    fun listen(languageTag: String = ""): Flow<SpeechEvent> = callbackFlow {
        val failure = when {
            !hasPermission() -> SpeechError.PERMISSION_DENIED
            !isAvailable() -> SpeechError.RECOGNIZER_UNAVAILABLE
            active != null -> SpeechError.BUSY
            else -> null
        }
        if (failure != null) {
            close(SpeechException(failure))
            awaitClose()
            return@callbackFlow
        }

        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (e: RuntimeException) {
            close(SpeechException(SpeechError.RECOGNIZER_UNAVAILABLE, e))
            awaitClose()
            return@callbackFlow
        }

        val listener = object : RecognitionListener {
            private var lastPartial = ""

            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(SpeechEvent.VoiceDetected)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // El rango habitual va de unos -2 dB (silencio) a 10 dB (voz fuerte)
                trySend(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = firstResult(partialResults)
                if (text.isNotBlank()) {
                    lastPartial = text
                    trySend(SpeechEvent.Partial(text))
                }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results).ifBlank { lastPartial }
                if (text.isBlank()) {
                    close(SpeechException(SpeechError.NO_MATCH))
                } else {
                    trySend(SpeechEvent.Final(text))
                    close()
                }
            }

            override fun onError(error: Int) {
                val mapped = mapError(error)
                // Algunos dispositivos devuelven "sin coincidencia" aunque hubo texto parcial
                val recoverable = mapped == SpeechError.NO_MATCH || mapped == SpeechError.TIMEOUT
                if (recoverable && lastPartial.isNotBlank()) {
                    trySend(SpeechEvent.Final(lastPartial))
                    close()
                } else {
                    close(SpeechException(mapped))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        active = recognizer
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(buildIntent(languageTag))

        awaitClose {
            if (active === recognizer) active = null
            recognizer.setRecognitionListener(null)
            recognizer.cancel()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main.immediate)

    /**
     * Deja de escuchar y procesa lo que se haya oído hasta ahora (llegará un
     * [SpeechEvent.Final] o un error). Llamar desde el hilo principal.
     */
    fun stopListening() {
        active?.stopListening()
    }

    private fun buildIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            // Sugerencias de silencio para cerrar la frase (el servicio puede ignorarlas)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            if (languageTag.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
        }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

    @SuppressLint("InlinedApi")
    private fun mapError(code: Int): SpeechError = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechError.NETWORK

        SpeechRecognizer.ERROR_AUDIO -> SpeechError.AUDIO
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.TIMEOUT
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechError.NO_MATCH

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> SpeechError.BUSY

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.PERMISSION_DENIED

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechError.LANGUAGE_UNAVAILABLE

        else -> SpeechError.UNKNOWN
    }
}

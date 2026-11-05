package com.assistant.siri.service

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Reconocimiento de voz que Android exige declarar junto a un asistente.
 *
 * Esta app no tiene motor de reconocimiento propio: cada petición se reenvía al reconocedor
 * real del dispositivo (normalmente el de Google). Así, aunque el sistema deje este servicio
 * como reconocedor por defecto al elegir la app como asistente, la entrada por voz sigue
 * funcionando.
 */
class AssistantRecognitionService : RecognitionService() {

    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        releaseDelegate()

        val target = findRealRecognizer()
        if (target == null) {
            listener.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this, target)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.readyForSpeech(params ?: Bundle())

            override fun onBeginningOfSpeech() = listener.beginningOfSpeech()

            override fun onRmsChanged(rmsdB: Float) = listener.rmsChanged(rmsdB)

            override fun onBufferReceived(buffer: ByteArray?) {
                if (buffer != null) listener.bufferReceived(buffer)
            }

            override fun onEndOfSpeech() = listener.endOfSpeech()

            override fun onError(error: Int) = listener.error(error)

            override fun onResults(results: Bundle?) = listener.results(results ?: Bundle())

            override fun onPartialResults(partialResults: Bundle?) =
                listener.partialResults(partialResults ?: Bundle())

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        delegate = recognizer
        recognizer.startListening(recognizerIntent)
    }

    override fun onStopListening(listener: Callback) {
        delegate?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        releaseDelegate()
    }

    override fun onDestroy() {
        releaseDelegate()
        super.onDestroy()
    }

    private fun releaseDelegate() {
        delegate?.cancel()
        delegate?.destroy()
        delegate = null
    }

    /** Primer servicio de reconocimiento instalado que no sea este, con preferencia por el de Google. */
    private fun findRealRecognizer(): ComponentName? {
        val services = packageManager
            .queryIntentServices(Intent(SERVICE_INTERFACE), 0)
            .mapNotNull { it.serviceInfo }
            .filter { it.packageName != packageName }

        val chosen = services.firstOrNull { it.packageName == GOOGLE_PACKAGE } ?: services.firstOrNull()
        return chosen?.let { ComponentName(it.packageName, it.name) }
    }

    private companion object {
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    }
}

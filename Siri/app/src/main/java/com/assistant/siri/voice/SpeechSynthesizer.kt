package com.assistant.siri.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Síntesis de voz con [TextToSpeech].
 * [speak] suspende hasta que termina la lectura; [stop] la interrumpe al instante.
 */
class SpeechSynthesizer(context: Context) {

    private class PendingSpeech(
        val lastUtteranceId: String,
        val finished: CompletableDeferred<Unit>
    )

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val engineLock = Mutex()

    @Volatile
    private var tts: TextToSpeech? = null

    private val pending = ConcurrentHashMap<String, PendingSpeech>()
    private val callCounter = AtomicLong(0)
    private val activeCalls = AtomicInteger(0)

    private val _isSpeaking = MutableStateFlow(false)

    /** True mientras hay una lectura en curso. */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            val id = utteranceId ?: return
            val speech = pending[callTag(id)] ?: return
            if (id == speech.lastUtteranceId) speech.finished.complete(Unit)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            fail(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            fail(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            val id = utteranceId ?: return
            pending[callTag(id)]?.finished?.complete(Unit)
        }

        private fun fail(utteranceId: String?) {
            val id = utteranceId ?: return
            pending[callTag(id)]?.finished?.completeExceptionally(
                SpeechException(SpeechError.TTS_FAILED)
            )
        }
    }

    /**
     * Lee el texto en voz alta. Quita antes el formato Markdown.
     * Interrumpe cualquier lectura anterior. Si la corutina se cancela, la lectura se detiene.
     * Puede lanzar [SpeechException].
     *
     * @param languageTag etiqueta BCP-47 (por ejemplo "es-ES"). Vacía = idioma del sistema.
     * @param rate velocidad, de 0.5 a 2.0.
     * @param pitch tono, de 0.5 a 2.0.
     */
    suspend fun speak(
        text: String,
        languageTag: String = "",
        rate: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        val spoken = SpokenText.fromMarkdown(text)
        if (spoken.isBlank()) return

        val engine = engine()
        val locale = if (languageTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(languageTag)
        val availability = engine.setLanguage(locale)
        if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            throw SpeechException(SpeechError.LANGUAGE_UNAVAILABLE)
        }
        engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

        val chunks = SpokenText.split(spoken, MAX_CHUNK_LENGTH)
        val callNumber = callCounter.incrementAndGet()
        val tag = "u$callNumber"
        val finished = CompletableDeferred<Unit>()
        pending[tag] = PendingSpeech(lastUtteranceId = "$tag:${chunks.lastIndex}", finished = finished)

        onCallStarted()
        try {
            chunks.forEachIndexed { index, chunk ->
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val result = engine.speak(chunk, mode, null, "$tag:$index")
                if (result != TextToSpeech.SUCCESS) throw SpeechException(SpeechError.TTS_FAILED)
            }
            finished.await()
        } catch (e: CancellationException) {
            // Solo se detiene si no ha empezado ya otra lectura más reciente
            if (callCounter.get() == callNumber) engine.stop()
            throw e
        } finally {
            pending.remove(tag)
            onCallFinished()
        }
    }

    /** Interrumpe la lectura en curso. La llamada a [speak] correspondiente termina con normalidad. */
    fun stop() {
        tts?.stop()
        // Por si el motor no avisa con onStop
        pending.values.forEach { it.finished.complete(Unit) }
    }

    /** Libera el motor de voz. Se vuelve a crear solo en el siguiente [speak]. */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    // ---------- Motor ----------

    private suspend fun engine(): TextToSpeech = engineLock.withLock {
        val existing = tts
        if (existing != null) {
            existing
        } else {
            val created = createEngine()
            tts = created
            created
        }
    }

    private suspend fun createEngine(): TextToSpeech = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Boolean>()
        val created = TextToSpeech(appContext) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }
        var success = false
        try {
            if (!ready.await()) throw SpeechException(SpeechError.TTS_UNAVAILABLE)
            created.setAudioAttributes(audioAttributes)
            created.setOnUtteranceProgressListener(progressListener)
            success = true
            created
        } finally {
            if (!success) created.shutdown()
        }
    }

    // ---------- Estado y foco de audio ----------

    private fun onCallStarted() {
        if (activeCalls.getAndIncrement() == 0) {
            audioManager.requestAudioFocus(focusRequest)
        }
        _isSpeaking.value = true
    }

    private fun onCallFinished() {
        if (activeCalls.decrementAndGet() == 0) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            _isSpeaking.value = false
        }
    }

    private fun callTag(utteranceId: String): String = utteranceId.substringBefore(':')

    private companion object {
        /** Trozos cortos: la lectura empieza antes y se puede interrumpir con más precisión. */
        const val MAX_CHUNK_LENGTH = 1500
    }
}

package com.assistant.siri

import android.app.Application
import android.content.Context
import com.assistant.siri.data.chat.ChatDatabase
import com.assistant.siri.data.chat.ConversationRepository
import com.assistant.siri.data.settings.AppPreferencesRepository
import com.assistant.siri.data.settings.ProviderRepository
import com.assistant.siri.data.settings.SecureKeyStore
import com.assistant.siri.voice.SpeechRecognizerManager
import com.assistant.siri.voice.SpeechSynthesizer

/** Application: crea el contenedor de dependencias una sola vez por proceso. */
class AiAssistantApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Contenedor sencillo de dependencias (sin librerías de inyección). */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore(appContext) }

    val providerRepository: ProviderRepository by lazy {
        ProviderRepository(appContext, secureKeyStore)
    }

    val preferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository(appContext)
    }

    val chatDatabase: ChatDatabase by lazy { ChatDatabase.create(appContext) }

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(chatDatabase)
    }

    val speechRecognizerManager: SpeechRecognizerManager by lazy {
        SpeechRecognizerManager(appContext)
    }

    val speechSynthesizer: SpeechSynthesizer by lazy { SpeechSynthesizer(appContext) }
}

/** Acceso al contenedor desde cualquier Context: `context.appContainer`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as AiAssistantApp).container

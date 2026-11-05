package com.assistant.siri.service

import android.service.voice.VoiceInteractionService

/**
 * Servicio que el sistema enlaza cuando esta app es el asistente digital predeterminado.
 * No necesita lógica propia: cuando el usuario invoca el asistente, el sistema crea una
 * sesión mediante [AssistantSessionService].
 */
class AssistantInteractionService : VoiceInteractionService()

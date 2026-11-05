package com.assistant.siri.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Crea una [AssistantSession] cada vez que el sistema necesita mostrar el asistente. */
class AssistantSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistantSession(this)
}

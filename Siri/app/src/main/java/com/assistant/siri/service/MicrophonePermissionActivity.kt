package com.assistant.siri.service

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.assistant.siri.voice.VoicePermissions

/**
 * Pantalla transparente que solo pide el permiso de micrófono y se cierra. Si el usuario lo
 * ha denegado de forma permanente (el sistema ya no muestra el diálogo), abre los ajustes
 * de la app para que pueda concederlo allí.
 */
class MicrophonePermissionActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && !VoicePermissions.shouldShowRationale(this)) {
            VoicePermissions.openAppSettings(this)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (VoicePermissions.hasRecordAudio(this)) {
            finish()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

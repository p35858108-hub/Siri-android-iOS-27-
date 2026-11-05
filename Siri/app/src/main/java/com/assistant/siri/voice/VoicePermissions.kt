package com.assistant.siri.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Permiso de micrófono. */
object VoicePermissions {

    fun hasRecordAudio(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** True si conviene explicar para qué se necesita el micrófono antes de pedirlo de nuevo. */
    fun shouldShowRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

    /**
     * Abre los ajustes de la app. Es la única vía cuando el usuario ha denegado el permiso
     * de forma permanente (el sistema ya no muestra el diálogo).
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Estado del permiso de micrófono para usar desde Compose. */
@Stable
class MicrophonePermissionState internal constructor(
    private val checkGranted: () -> Boolean,
    private val launch: () -> Unit
) {
    /** Consulta el estado actual del permiso. */
    val isGranted: Boolean
        get() = checkGranted()

    /** Muestra el diálogo del sistema. El resultado llega a `onResult`. */
    fun request() = launch()
}

/**
 * Recuerda un solicitante del permiso de micrófono.
 * Debe usarse dentro de una Activity (no sirve dentro de una sesión del asistente).
 */
@Composable
fun rememberMicrophonePermission(onResult: (granted: Boolean) -> Unit = {}): MicrophonePermissionState {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> currentOnResult(granted) }

    return remember(context, launcher) {
        MicrophonePermissionState(
            checkGranted = { VoicePermissions.hasRecordAudio(context) },
            launch = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
        )
    }
}

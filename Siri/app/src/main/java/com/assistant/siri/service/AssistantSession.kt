package com.assistant.siri.service

import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.assistant.siri.MainActivity
import com.assistant.siri.assistant.AssistantViewModel
import com.assistant.siri.ui.theme.AssistantTheme

/**
 * Sesión del asistente: una ventana transparente a pantalla completa sobre la app actual,
 * con la cápsula alrededor de la cámara.
 *
 * Al mostrarse empieza a escuchar. Al ocultarse se detiene todo (escucha, petición y voz).
 * Una sesión no es una Activity, así que aquí se crean a mano el ciclo de vida y el almacén
 * de ViewModels que necesita Compose.
 */
class AssistantSession(context: Context) : VoiceInteractionSession(context) {

    private val owner = SessionOwner()
    private lateinit var viewModel: AssistantViewModel
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        val application = context.applicationContext as Application
        viewModel = ViewModelProvider(
            owner.viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[AssistantViewModel::class.java]
        configureWindow()
    }

    override fun onCreateContentView(): View {
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                AssistantTheme {
                    AssistantSessionContent(
                        vm = viewModel,
                        window = sessionWindow(),
                        onOpenApp = ::openApp,
                        onGrantMicrophone = ::requestMicrophonePermission,
                        onClose = ::hide
                    )
                }
            }
        }
        composeView = view
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        owner.moveTo(Lifecycle.State.RESUMED)
        // Si falta el permiso de micrófono, la cápsula muestra el aviso con la forma de resolverlo
        viewModel.startListening()
    }

    override fun onHide() {
        viewModel.dismiss()
        owner.moveTo(Lifecycle.State.STARTED)
        super.onHide()
    }

    override fun onDestroy() {
        viewModel.dismiss()
        composeView?.disposeComposition()
        composeView = null
        owner.moveTo(Lifecycle.State.DESTROYED)
        owner.viewModelStore.clear()
        super.onDestroy()
    }

    // ---------- Acciones ----------

    /** Abre la app (en la conversación indicada, si hay) y cierra el asistente. */
    private fun openApp(conversationId: Long?) {
        val intent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        if (conversationId != null) {
            intent.putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
        }
        // Se lanza mientras la ventana del asistente aún está visible y luego se cierra
        context.startActivity(intent)
        hide()
    }

    /** Una sesión no puede pedir permisos: se abre una pantalla transparente que lo hace. */
    private fun requestMicrophonePermission() {
        val intent = Intent(context, MicrophonePermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        hide()
    }

    // ---------- Ventana ----------

    /** Ventana de Android de la sesión (según la versión, el sistema la expone como Window o como Dialog). */
    private fun sessionWindow(): Window? {
        val raw: Any? = window
        return when (raw) {
            is Window -> raw
            is Dialog -> raw.window
            else -> null
        }
    }

    /** Pantalla completa, fondo transparente y dibujo sobre la zona de la cámara. */
    private fun configureWindow() {
        val w = sessionWindow() ?: return
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.setGravity(Gravity.TOP)
        w.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(w, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = w.attributes
            params.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            w.attributes = params
        }
    }

    /** Ciclo de vida, estado guardado y almacén de ViewModels de la sesión, para Compose. */
    private class SessionOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        init {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun moveTo(state: Lifecycle.State) {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.currentState = state
            }
        }
    }
}

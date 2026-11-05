package com.assistant.siri.ui.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument

object SettingsRoutes {
    /** Grafo de ajustes. Navegar a esta ruta abre la pantalla de inicio de la configuración. */
    const val GRAPH = "settings"
    const val HOME = "settings/home"
    const val PROVIDERS = "settings/providers"
    const val PROVIDER_EDIT = "settings/provider/{id}"
    const val MODELS = "settings/models"
    const val VOICE = "settings/voice"
    const val VISUAL = "settings/visual"
    const val APP = "settings/app"

    /** Valor de `{id}` para crear un proveedor nuevo. */
    const val NEW_PROVIDER = "new"

    fun providerEdit(id: String) = "settings/provider/$id"
}

/** Todas las pantallas de configuración. */
fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    onClearHistory: () -> Unit
) {
    val back: () -> Unit = { navController.popBackStack() }
    val open: (String) -> Unit = { route ->
        navController.navigate(route) { launchSingleTop = true }
    }

    navigation(startDestination = SettingsRoutes.HOME, route = SettingsRoutes.GRAPH) {
        composable(SettingsRoutes.HOME) {
            SettingsHomeScreen(onBack = back, onNavigate = open)
        }

        composable(SettingsRoutes.PROVIDERS) {
            ProvidersScreen(
                onBack = back,
                onEdit = { id -> open(SettingsRoutes.providerEdit(id ?: SettingsRoutes.NEW_PROVIDER)) }
            )
        }

        composable(
            route = SettingsRoutes.PROVIDER_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it != SettingsRoutes.NEW_PROVIDER }
            ProviderEditScreen(providerId = id, onBack = back)
        }

        composable(SettingsRoutes.MODELS) {
            ModelsScreen(
                onBack = back,
                onOpenProviders = { open(SettingsRoutes.PROVIDERS) }
            )
        }

        composable(SettingsRoutes.VOICE) {
            VoiceSettingsScreen(onBack = back)
        }

        composable(SettingsRoutes.VISUAL) {
            VisualSettingsScreen(onBack = back)
        }

        composable(SettingsRoutes.APP) {
            AppSettingsScreen(onBack = back, onClearHistory = onClearHistory)
        }
    }
}

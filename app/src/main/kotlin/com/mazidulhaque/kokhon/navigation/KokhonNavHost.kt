package com.mazidulhaque.kokhon.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mazidulhaque.kokhon.ui.detail.DetailScreen
import com.mazidulhaque.kokhon.ui.editor.EditorScreen
import com.mazidulhaque.kokhon.ui.home.HomeScreen
import com.mazidulhaque.kokhon.ui.reminders.RemindersScreen
import com.mazidulhaque.kokhon.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable object HomeRoute

@Serializable data class DetailRoute(
    val id: Long,
)

/** id = 0 creates a new plan. */
@Serializable data class EditorRoute(
    val id: Long = 0,
)

@Serializable data class RemindersRoute(
    val id: Long,
)

@Serializable object SettingsRoute

@Composable
fun KokhonNavHost(entryToOpen: MutableStateFlow<Long?>) {
    val nav = rememberNavController()
    val pending by entryToOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            nav.navigate(DetailRoute(it)) { launchSingleTop = true }
            entryToOpen.value = null
        }
    }
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenEntry = { nav.navigate(DetailRoute(it)) },
                onAddPlan = { nav.navigate(EditorRoute()) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<DetailRoute> { entry ->
            val id = entry.toRoute<DetailRoute>().id
            DetailScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(EditorRoute(id)) },
                onEditReminders = { nav.navigate(RemindersRoute(id)) },
                onOpenEntry = { newId ->
                    nav.navigate(DetailRoute(newId)) { popUpTo<DetailRoute> { inclusive = true } }
                },
            )
        }
        composable<EditorRoute> { entry ->
            val id = entry.toRoute<EditorRoute>().id
            EditorScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onSaved = { savedId ->
                    if (id == 0L) {
                        nav.navigate(DetailRoute(savedId)) { popUpTo<EditorRoute> { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
        composable<RemindersRoute> { entry ->
            RemindersScreen(
                id = entry.toRoute<RemindersRoute>().id,
                onBack = { nav.popBackStack() },
                onOpenAlarmSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

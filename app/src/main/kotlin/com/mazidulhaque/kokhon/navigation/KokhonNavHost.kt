package com.mazidulhaque.kokhon.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mazidulhaque.kokhon.core.model.EntrySource
import com.mazidulhaque.kokhon.ui.capture.CaptureScreen
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

/** id = 0 creates a new plan; [fromCapture] starts it from a plan found in a message. */
@Serializable data class EditorRoute(
    val id: Long = 0,
    val fromCapture: Boolean = false,
)

/** Turn a message into a plan, or update plan [targetId] from it. */
@Serializable data class CaptureRoute(
    val text: String = "",
    val source: String = "PASTED_TEXT",
    val targetId: Long = 0,
    /** A screenshot or photo to read. */
    val imageUri: String = "",
)

@Serializable data class RemindersRoute(
    val id: Long,
)

@Serializable object SettingsRoute

@Composable
fun KokhonNavHost(
    entryToOpen: MutableStateFlow<Long?>,
    sharedText: MutableStateFlow<String?>,
    sharedImage: MutableStateFlow<String?>,
) {
    val nav = rememberNavController()
    val pending by entryToOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            nav.navigate(DetailRoute(it)) { launchSingleTop = true }
            entryToOpen.value = null
        }
    }
    val shared by sharedText.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            nav.navigate(CaptureRoute(text = it, source = "SHARED_TEXT")) { launchSingleTop = true }
            sharedText.value = null
        }
    }
    val image by sharedImage.collectAsStateWithLifecycle()
    LaunchedEffect(image) {
        image?.let {
            nav.navigate(CaptureRoute(imageUri = it, source = "SCREENSHOT")) { launchSingleTop = true }
            sharedImage.value = null
        }
    }
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenEntry = { nav.navigate(DetailRoute(it)) },
                onAddPlan = { nav.navigate(EditorRoute()) },
                onCapture = { text -> nav.navigate(CaptureRoute(text = text)) },
                onCaptureImage = { uri -> nav.navigate(CaptureRoute(imageUri = uri.toString(), source = "SCREENSHOT")) },
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
                onUpdateFromMessage = { nav.navigate(CaptureRoute(targetId = id)) },
                onOpenEntry = { newId ->
                    nav.navigate(DetailRoute(newId)) { popUpTo<DetailRoute> { inclusive = true } }
                },
            )
        }
        composable<EditorRoute> { entry ->
            val route = entry.toRoute<EditorRoute>()
            val id = route.id
            EditorScreen(
                id = id,
                fromCapture = route.fromCapture,
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
        composable<CaptureRoute> { entry ->
            val route = entry.toRoute<CaptureRoute>()
            CaptureScreen(
                initialText = route.text,
                initialImage = route.imageUri,
                source = EntrySource.Kind.entries.firstOrNull { it.name == route.source } ?: EntrySource.Kind.PASTED_TEXT,
                targetId = route.targetId,
                onClose = { nav.popBackStack() },
                onOpenEntry = { id -> nav.navigate(DetailRoute(id)) { popUpTo<CaptureRoute> { inclusive = true } } },
                onGoHome = { nav.popBackStack(HomeRoute, inclusive = false) },
                onEditDraft = { nav.navigate(EditorRoute(fromCapture = true)) { popUpTo<CaptureRoute> { inclusive = true } } },
                onEditEntry = { id -> nav.navigate(EditorRoute(id)) { popUpTo<CaptureRoute> { inclusive = true } } },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

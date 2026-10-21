package com.vknxworks.craftyctrl

import com.vknxworks.craftyctrl.ui.lock.findFragmentActivity
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vknxworks.craftyctrl.data.AppSettings
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.Session
import com.vknxworks.craftyctrl.monitor.MonitorService
import com.vknxworks.craftyctrl.ui.ScreenPrivacy
import com.vknxworks.craftyctrl.ui.SessionViewModel
import com.vknxworks.craftyctrl.ui.lock.AppLockViewModel
import com.vknxworks.craftyctrl.ui.lock.LockScreen
import com.vknxworks.craftyctrl.ui.lock.PinRequestScreen
import com.vknxworks.craftyctrl.ui.lock.PinRequests
import com.vknxworks.craftyctrl.ui.screens.CreateServerScreen
import com.vknxworks.craftyctrl.ui.screens.FileEditorScreen
import com.vknxworks.craftyctrl.ui.screens.LoginScreen
import com.vknxworks.craftyctrl.ui.screens.ManagePage
import com.vknxworks.craftyctrl.ui.screens.AddonsScreen
import com.vknxworks.craftyctrl.ui.screens.LogsScreen
import com.vknxworks.craftyctrl.ui.screens.PlayersScreen
import com.vknxworks.craftyctrl.ui.screens.ServerOptionsScreen
import com.vknxworks.craftyctrl.ui.screens.ServerPropertiesScreen
import com.vknxworks.craftyctrl.ui.screens.ServerDetailScreen
import com.vknxworks.craftyctrl.ui.screens.ServerListScreen
import com.vknxworks.craftyctrl.ui.screens.SettingsScreen
import com.vknxworks.craftyctrl.ui.theme.CraftyTheme
import com.vknxworks.craftyctrl.ui.theme.ThemePrefs

// FragmentActivity (still a ComponentActivity) is needed for the biometric prompt.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recents shows a blank card instead of a snapshot of the console or files.
        setRecentsScreenshotEnabled(false)
        ThemePrefs.load(this)
        ScreenPrivacy.load(this)
        // Deletes what the anonymous stats (removed in 2.4.12) left in the settings, once after the update.
        if (savedInstanceState == null) AppSettings(this).removeLeftoverStats()
        enableEdgeToEdge()
        setContent {
            // FLAG_SECURE follows the privacy setting, and is forced on by sign-in and the file editor.
            val secure = ScreenPrivacy.secure
            LaunchedEffect(secure) {
                if (secure) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            // System bar icons follow the app's own light/dark choice, not just the phone's.
            val dark = ThemePrefs.isDark()
            LaunchedEffect(dark) {
                val clear = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CraftyTheme { AppRoot() }
        }
    }

    override fun onStart() {
        super.onStart()
        // Keeps the alerts service in step with settings (it can't start itself from the background).
        MonitorService.sync(this)
    }
}

@Composable
private fun AppRoot(
    sessionVm: SessionViewModel = viewModel(),
    lockVm: AppLockViewModel = viewModel(),
) {
    val activity = LocalContext.current.findFragmentActivity()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) lockVm.onBackground()
                Lifecycle.Event.ON_START -> lockVm.onForeground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val session = sessionVm.session
    val context = LocalContext.current
    // Start or stop alerts whenever someone signs in or out.
    LaunchedEffect(session, sessionVm.signIns) { MonitorService.sync(context) }
    if (session == null) {
        LoginScreen(sessionVm)
        return
    }

    // The app keeps running underneath the lock, so open screens and unsaved edits survive it.
    // While locked, the screens underneath are hidden from accessibility services and screen readers.
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (lockVm.locked) Modifier.clearAndSetSemantics {} else Modifier)) {
            AppContent(session, sessionVm)
        }
        // Turning the lock on or off, or changing the PIN (from Settings), on top of the app.
        val request = PinRequests.request
        if (!lockVm.locked && request != null) PinRequestScreen(request, lockVm)
        if (lockVm.locked) {
            // Opaque cover in the app's own window (also behind the system bars).
            androidx.compose.material3.Surface(Modifier.fillMaxSize().clearAndSetSemantics {}) {}
            // The lock itself is a full-screen window of its own. Dialogs, menus and sheets that were open when the
            // app locked are separate windows above the app's window, so drawing the lock in the app's window
            // alone would leave them visible and usable on top of it; this window covers them.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                LockScreen(
                    vm = lockVm,
                    onForgot = {
                        lockVm.forget()
                        MonitorService.sync(context)
                        sessionVm.signOut("PIN removed. Sign in again to continue.")
                    },
                )
            }
        }
    }
}

@Composable
private fun AppContent(session: Session, sessionVm: SessionViewModel) {
    val activity = LocalContext.current.findFragmentActivity()
    // A new token means a fresh API client and a fresh navigation stack.
    key(session, sessionVm.signIns) {
        val api = remember {
            CraftyApi(
                session.baseUrl,
                fixedToken = null,
                onUnauthorized = { sessionVm.signOut("Your session expired. Sign in again.") },
                // Wiped while the app is locked: the screens stay, but can't reach Crafty until you unlock.
                tokenProvider = { com.vknxworks.craftyctrl.data.lock.Lock.liveToken },
            )
        }
        val nav = rememberNavController()

        NavHost(
            navController = nav,
            startDestination = "servers",
            enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(200)) },
        ) {
            composable("servers") { entry ->
                ServerListScreen(
                    api = api,
                    onOpen = { s -> nav.go(entry, "server/${Uri.encode(s.id)}?name=${Uri.encode(s.name)}") },
                    onSettings = { nav.go(entry, "settings") },
                    onSignOut = { sessionVm.signOut() },
                    onCreate = { nav.go(entry, "create") },
                )
            }
            composable("create") { entry ->
                CreateServerScreen(
                    api = api,
                    onBack = { nav.back(entry) },
                    onOpenServer = { id, name ->
                        nav.navigate("server/${Uri.encode(id)}?name=${Uri.encode(name)}") {
                            popUpTo("servers")
                        }
                    },
                )
            }
            composable(
                route = "server/{id}?name={name}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val serverId = entry.arguments?.getString("id").orEmpty()
                ServerDetailScreen(
                    api = api,
                    serverId = serverId,
                    serverName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { nav.back(entry) },
                    onOpenFile = { path ->
                        nav.go(entry, "file/${Uri.encode(serverId)}?path=${Uri.encode(path)}")
                    },
                    onDeleted = { nav.popBackStack("servers", inclusive = false) },
                    onManage = { page -> nav.go(entry, "manage/${page.route}/${Uri.encode(serverId)}") },
                    onOpenServer = { id, name ->
                        nav.navigate("server/${Uri.encode(id)}?name=${Uri.encode(name)}") { popUpTo("servers") }
                    },
                )
            }
            composable(
                route = "manage/{page}/{id}",
                arguments = listOf(
                    navArgument("page") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                val serverId = entry.arguments?.getString("id").orEmpty()
                val back: () -> Unit = { nav.back(entry) }
                when (entry.arguments?.getString("page")) {
                    ManagePage.PROPERTIES.route -> ServerPropertiesScreen(api, serverId, back)
                    ManagePage.PLAYERS.route -> PlayersScreen(api, serverId, back)
                    ManagePage.ADDONS.route -> AddonsScreen(api, serverId, back)
                    ManagePage.LOGS.route -> LogsScreen(api, serverId, back)
                    else -> ServerOptionsScreen(api, serverId, back)
                }
            }
            composable(
                route = "file/{id}?path={path}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                FileEditorScreen(
                    api = api,
                    serverId = entry.arguments?.getString("id").orEmpty(),
                    path = entry.arguments?.getString("path").orEmpty(),
                    onBack = { nav.back(entry) },
                )
            }
            composable("settings") { entry ->
                SettingsScreen(onBack = { nav.back(entry) })
            }
        }
    }
}

/**
 * Back from the screen [from]. Repeat taps while a screen change is still animating are ignored,
 * as is a late call from a screen that's already gone, and the server list at the bottom is never
 * popped (that emptied the screen stack and left a black screen).
 */
private fun NavHostController.back(from: NavBackStackEntry) {
    val current = currentBackStackEntry ?: return
    if (previousBackStackEntry == null) return
    if (current.id != from.id) return
    if (!current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    popBackStack()
}

/** Opens a screen from a tap on [from], ignoring taps on a screen that's on its way out or not shown yet. */
private fun NavHostController.go(from: NavBackStackEntry, route: String) {
    if (currentBackStackEntry?.id != from.id) return
    if (!from.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    navigate(route)
}

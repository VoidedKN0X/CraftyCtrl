package com.vknxworks.craftyremote

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.data.Session
import com.vknxworks.craftyremote.monitor.MonitorService
import com.vknxworks.craftyremote.ui.SessionViewModel
import com.vknxworks.craftyremote.ui.lock.AppLockViewModel
import com.vknxworks.craftyremote.ui.lock.LockScreen
import com.vknxworks.craftyremote.ui.screens.FileEditorScreen
import com.vknxworks.craftyremote.ui.screens.LoginScreen
import com.vknxworks.craftyremote.ui.screens.ServerDetailScreen
import com.vknxworks.craftyremote.ui.screens.ServerListScreen
import com.vknxworks.craftyremote.ui.screens.SettingsScreen
import com.vknxworks.craftyremote.ui.theme.CraftyTheme

// FragmentActivity (still a ComponentActivity) is needed for the biometric prompt.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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
    val activity = LocalContext.current as? FragmentActivity
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
    LaunchedEffect(session?.token) { MonitorService.sync(context) }
    if (session == null) {
        LoginScreen(sessionVm)
        return
    }

    // The app keeps running underneath the lock, so open screens and unsaved edits survive it.
    Box(Modifier.fillMaxSize()) {
        AppContent(session, sessionVm)
        if (lockVm.locked) {
            LockScreen(
                vm = lockVm,
                onForgot = {
                    lockVm.settings.clearPin()
                    lockVm.settings.alertsEnabled = false
                    lockVm.unlock()
                    sessionVm.signOut("PIN removed. Sign in again to continue.")
                },
            )
        }
    }
}

@Composable
private fun AppContent(session: Session, sessionVm: SessionViewModel) {
    val activity = LocalContext.current as? FragmentActivity
    // A new token means a fresh API client and a fresh navigation stack.
    key(session.token) {
        val api = remember {
            CraftyApi(session.baseUrl, session.token) {
                sessionVm.signOut("Your session expired. Sign in again.")
            }
        }
        val nav = rememberNavController()

        NavHost(navController = nav, startDestination = "servers") {
            composable("servers") {
                ServerListScreen(
                    api = api,
                    onOpen = { s -> nav.navigate("server/${Uri.encode(s.id)}?name=${Uri.encode(s.name)}") },
                    onSettings = { nav.navigate("settings") },
                    onSignOut = { sessionVm.signOut() },
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
                    onBack = { nav.popBackStack() },
                    onOpenFile = { path ->
                        nav.navigate("file/${Uri.encode(serverId)}?path=${Uri.encode(path)}")
                    },
                )
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
                    onBack = { nav.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

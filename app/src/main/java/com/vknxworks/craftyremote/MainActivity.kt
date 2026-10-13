package com.vknxworks.craftyremote

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.ui.SessionViewModel
import com.vknxworks.craftyremote.ui.screens.LoginScreen
import com.vknxworks.craftyremote.ui.screens.ServerDetailScreen
import com.vknxworks.craftyremote.ui.screens.ServerListScreen
import com.vknxworks.craftyremote.ui.theme.CraftyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CraftyTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot(sessionVm: SessionViewModel = viewModel()) {
    val session = sessionVm.session
    if (session == null) {
        LoginScreen(sessionVm)
        return
    }

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
                ServerDetailScreen(
                    api = api,
                    serverId = entry.arguments?.getString("id").orEmpty(),
                    serverName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

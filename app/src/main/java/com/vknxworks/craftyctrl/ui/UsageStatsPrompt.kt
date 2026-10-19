package com.vknxworks.craftyctrl.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vknxworks.craftyctrl.data.Telemetry

/** What's shared, in the words used by the first-run question and the Settings footer. */
const val USAGE_STATS_DESCRIPTION =
    "Which screens you open, whether you start, stop, restart or kill a server, open the console or start a backup, " +
        "how you sign in (API key or password), the app version, Android version, phone model and language. " +
        "Never your panel address, username, server or player names, or what's in the console. " +
        "Sent to VKNXWorks' own server, not to any analytics company."

/** Asked once, after signing in. Nothing is sent until the user says yes; they can change it in Settings. */
@Composable
fun UsageStatsPrompt() {
    if (!Telemetry.available || Telemetry.asked) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Help improve CraftyCtrl?") },
        text = {
            Text(
                "Share anonymous usage stats so I can see which features people use and which Android " +
                    "versions to support.\n\n$USAGE_STATS_DESCRIPTION\n\nYou can change this any time in Settings.",
            )
        },
        confirmButton = { TextButton(onClick = { Telemetry.setOptedIn(context, true) }) { Text("Share stats") } },
        dismissButton = { TextButton(onClick = { Telemetry.setOptedIn(context, false) }) { Text("No thanks") } },
    )
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.craftyctrl.ui.components.CtrlButton
import com.vknxworks.craftyctrl.ui.components.initialsOf
import com.vknxworks.craftyctrl.ui.theme.CtrlColors
import com.vknxworks.craftyctrl.ui.theme.CtrlType
import com.vknxworks.craftyctrl.ui.components.CtrlTopBar
import com.vknxworks.craftyctrl.ui.components.CtrlBackButton
import com.vknxworks.craftyctrl.ui.components.CtrlDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.craftyctrl.BuildConfig
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.AuthMode
import com.vknxworks.craftyctrl.data.Session
import com.vknxworks.craftyctrl.data.lock.Lock
import com.vknxworks.craftyctrl.ui.settings.Group
import com.vknxworks.craftyctrl.ui.settings.GroupRow
import java.net.URI

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/CraftyCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/CraftyCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/**
 * The You page, opened from the profile picture on the Servers and Host tabs: who you're signed in as and
 * on which Crafty panel, the way into Settings, signing out, and the About section every Ctrl app ends with.
 */
@Composable
fun YouScreen(session: Session, onBack: () -> Unit, onSettings: () -> Unit, onSignOut: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var confirmSignOut by remember { mutableStateOf(false) }
    val host = remember(session.baseUrl) { runCatching { URI(session.baseUrl).host }.getOrNull() ?: session.baseUrl }
    val username = session.username?.trim()?.takeIf { it.isNotEmpty() }
    fun open(url: String) {
        // Opening the browser from here doesn't count as leaving CraftyCtrl for the app lock.
        Lock.expectReturn()
        runCatching { uriHandler.openUri(url) }
    }

    Scaffold(
        topBar = {
            CtrlTopBar(
                title = { Text(stringResource(R.string.signal_profile), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    CtrlBackButton(onClick = onBack)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // Account: the 68 dp accent picture, who you are, and the panel address in mono.
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (username == null) {
                        Icon(Icons.Outlined.AccountCircle, null, Modifier.size(34.dp), tint = CtrlColors.OnAccent)
                    } else {
                        Text(initialsOf(username), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CtrlColors.OnAccent)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        username ?: stringResource(R.string.you_api_key_user),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em,
                        color = CtrlColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(host, style = CtrlType.MonoSmall, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(if (session.mode == AuthMode.API_KEY) R.string.you_signed_in_api_key else R.string.you_signed_in_password),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                    )
                }
            }

            Group(stringResource(R.string.you_panel_group)) {
                GroupRow(
                    host,
                    session.baseUrl.takeIf { it != host },
                    icon = Icons.Outlined.Dns,
                )
            }

            Group {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Outlined.Settings,
                    showChevron = true,
                    onClick = onSettings,
                )
                GroupRow(
                    stringResource(R.string.sign_out),
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = cs.error,
                    titleColor = cs.error,
                    onClick = { confirmSignOut = true },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Outlined.BugReport,
                    showChevron = true,
                    onClick = { open(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Outlined.Code,
                    showChevron = true,
                    onClick = { open(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Outlined.Gavel,
                    showChevron = true,
                    onClick = { open(LICENCE_URL) },
                )
            }
            Spacer(Modifier.height(16.dp))
            CtrlButton(
                onClick = { open(SPONSOR_URL) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp),
            ) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.support_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.signal_footer).uppercase(),
                style = CtrlType.Label.copy(letterSpacing = 0.12.em),
                color = CtrlColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (confirmSignOut) {
        CtrlDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_body)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }) {
                    Text(stringResource(R.string.sign_out), color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

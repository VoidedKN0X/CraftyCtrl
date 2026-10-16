@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyctrl.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vknxworks.craftyctrl.ui.theme.Mono
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyctrl.data.CraftyApi
import com.vknxworks.craftyctrl.data.FileEntry
import com.vknxworks.craftyctrl.data.FileResult
import com.vknxworks.craftyctrl.data.HistoryPoint
import com.vknxworks.craftyctrl.data.downsample
import com.vknxworks.craftyctrl.ui.ChartCard
import com.vknxworks.craftyctrl.ui.ChartCpu
import com.vknxworks.craftyctrl.ui.ChartMem
import com.vknxworks.craftyctrl.ui.ChartPlayers
import com.vknxworks.craftyctrl.ui.ErrorBanner
import com.vknxworks.craftyctrl.ui.SectionLabel
import com.vknxworks.craftyctrl.ui.ServerDetailViewModel
import com.vknxworks.craftyctrl.ui.theme.ConsoleBackground
import com.vknxworks.craftyctrl.ui.theme.ConsoleText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- stats tab

private enum class Range(val label: String, val millis: Long?) {
    HOUR("1 h", 3_600_000L),
    SIX("6 h", 6 * 3_600_000L),
    DAY("24 h", 24 * 3_600_000L),
    ALL("All", null),
}

@Composable
fun StatsTab(vm: ServerDetailViewModel) {
    LaunchedEffect(vm) { if (vm.history.data == null && !vm.history.loading) vm.loadHistory() }
    var range by rememberSaveable { mutableStateOf(Range.SIX) }

    val fromCrafty = vm.history.data.orEmpty()
    val useHistory = fromCrafty.size >= 2
    val source = if (useHistory) fromCrafty else vm.liveSamples
    val cutoff = range.millis?.let { System.currentTimeMillis() - it }
    val points = remember(source, range) {
        (if (cutoff != null && useHistory) source.filter { it.time >= cutoff } else source).downsample(240)
    }
    val maxPlayers = vm.stats?.max?.takeIf { it > 0 }?.toFloat()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(if (useHistory) "History from Crafty" else "Live, since you opened this server", Modifier.weight(1f))
            if (vm.history.loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = vm::loadHistory) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            }
        }
        if (useHistory) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r ->
                    val sel = range == r
                    Surface(
                        onClick = { range = r },
                        shape = RoundedCornerShape(50),
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (sel) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            r.label.replace(" ", ""),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = Mono,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        } else if (vm.history.error != null) {
            Text(
                "Crafty didn't return stored history (${vm.history.error}). Showing live samples instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val span = spanLabel(points)
        ChartCard(
            title = "CPU",
            current = vm.stats?.cpu?.let { "%.1f%%".format(it) } ?: "—",
            values = points.map { it.cpu?.toFloat() },
            color = ChartCpu,
            maxY = null,
            footer = span,
            format = { "%.0f%%".format(it) },
        )
        ChartCard(
            title = "Memory",
            current = vm.stats?.let { s -> listOfNotNull(s.mem, s.memPercent?.let { "%.0f%%".format(it) }).joinToString(" · ") }?.ifEmpty { null } ?: "—",
            values = points.map { it.memPercent?.toFloat() },
            color = ChartMem,
            maxY = 100f,
            footer = span,
            format = { "%.0f%%".format(it) },
        )
        if (vm.stats?.playersKnown != false) ChartCard(
            title = "Players",
            current = vm.stats?.let { playersText(it.online, it.max) } ?: "—",
            values = points.map { it.players?.toFloat() },
            color = ChartPlayers,
            maxY = maxPlayers,
            footer = span,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String?) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, fontFamily = Mono)
    }
}

private fun spanLabel(points: List<HistoryPoint>): String? {
    if (points.size < 2) return null
    val fmt = SimpleDateFormat(
        if (points.last().time - points.first().time > 86_400_000L) "MMM d, HH:mm" else "HH:mm",
        Locale.getDefault(),
    )
    return "${fmt.format(Date(points.first().time))} – ${fmt.format(Date(points.last().time))}"
}

// ---------------------------------------------------------------- files tab

private val editableHint = setOf(
    "properties", "json", "yml", "yaml", "txt", "cfg", "conf", "config", "toml", "ini", "log", "sh", "bat",
    "mcmeta", "md", "csv", "xml", "js", "lang",
)
private val binaryExt = setOf(
    "jar", "zip", "gz", "tar", "dat", "dat_old", "mca", "mcr", "png", "jpg", "jpeg", "gif", "db", "sqlite",
    "exe", "so", "dll", "ldb", "nbt", "bin",
)

@Composable
fun FilesTab(vm: ServerDetailViewModel, onOpenFile: (String) -> Unit) {
    LaunchedEffect(vm) { if (vm.filePath == null && !vm.files.loading) vm.openFolder(null) }
    val parent = vm.parentPath
    BackHandler(enabled = parent != null) { vm.openFolder(parent) }
    var binaryWarning by remember { mutableStateOf<FileEntry?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var rowMenu by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<FileEntry?>(null) }
    var renaming by remember { mutableStateOf<FileEntry?>(null) }
    // true = new folder, false = new file, null = dialog closed
    var newEntry by remember { mutableStateOf<Boolean?>(null) }
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.upload(resolver, uri)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.openFolder(null) }) { Icon(Icons.Filled.Home, contentDescription = "Server folder") }
            IconButton(onClick = { vm.openFolder(parent) }, enabled = parent != null) {
                Icon(Icons.Filled.SubdirectoryArrowLeft, contentDescription = "Up one folder")
            }
            Text(
                relativeLabel(vm.filePath, vm.rootPath),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.openFolder(vm.filePath) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            Box {
                FilledTonalIconButton(onClick = { addMenu = true }, enabled = vm.filePath != null && vm.uploadProgress == null) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("New file") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                        onClick = { addMenu = false; newEntry = false },
                    )
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = { addMenu = false; newEntry = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Upload file") },
                        leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                        onClick = { addMenu = false; picker.launch(arrayOf("*/*")) },
                    )
                }
            }
        }
        vm.uploadProgress?.let { p ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Uploading… ${(p * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (vm.files.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.files.error?.let { ErrorBanner(it, Modifier.padding(12.dp), onRetry = { vm.openFolder(vm.filePath) }) }

        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            val entries = vm.files.data.orEmpty()
            if (entries.isEmpty() && !vm.files.loading && vm.files.error == null) {
                item { Text("This folder is empty.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(entries, key = { it.path }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                e.isDir -> vm.openFolder(e.path)
                                e.name.substringAfterLast('.', "").lowercase() in binaryExt -> binaryWarning = e
                                else -> onOpenFile(e.path)
                            }
                        }
                        .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    Icon(
                        if (e.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = when {
                            e.isDir -> MaterialTheme.colorScheme.primary
                            ext in editableHint -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        e.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { rowMenu = e.path }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More for ${e.name}")
                        }
                        DropdownMenu(expanded = rowMenu == e.path, onDismissRequest = { rowMenu = null }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                                onClick = { rowMenu = null; renaming = e },
                            )
                            DropdownMenuItem(
                                text = { Text("Move…") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                onClick = { rowMenu = null; vm.startClip(e, copy = false) },
                            )
                            // Crafty has no copy command; the app copies text files by reading and re-saving them.
                            val copyable = !e.isDir && e.canOpen != false &&
                                e.name.substringAfterLast('.', "").lowercase() !in binaryExt
                            DropdownMenuItem(
                                text = { Text(if (copyable) "Copy…" else "Copy (text files only)") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                enabled = copyable,
                                onClick = { rowMenu = null; vm.startClip(e, copy = true) },
                            )
                            if (!e.isDir && e.name.endsWith(".zip", ignoreCase = true)) {
                                DropdownMenuItem(
                                    text = { Text("Extract here") },
                                    leadingIcon = { Icon(Icons.Filled.Unarchive, contentDescription = null) },
                                    onClick = { rowMenu = null; vm.unzip(e) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { rowMenu = null; confirmDelete = e },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        vm.clip?.let { c ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (c.copy) "Copying " else "Moving ") + c.entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Open the destination folder, then paste",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    TextButton(onClick = vm::cancelClip) { Text("Cancel") }
                    FilledTonalButton(onClick = vm::pasteHere) { Text("Paste here") }
                }
            }
        }
    }

    renaming?.let { e ->
        NameDialog(
            title = "Rename ${e.name}",
            hint = "New name",
            initial = e.name,
            confirmLabel = "Rename",
            onDismiss = { renaming = null },
            onConfirm = { name -> renaming = null; if (name != e.name) vm.renameEntry(e, name) },
        )
    }

    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${e.name}?") },
            text = {
                Text(
                    if (e.isDir) "This folder and everything inside it will be permanently deleted. This can't be undone."
                    else "This file will be permanently deleted. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; vm.deleteEntry(e) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    newEntry?.let { folder ->
        NameDialog(
            title = if (folder) "New folder" else "New file",
            hint = if (folder) "Folder name" else "File name, e.g. notes.txt",
            onDismiss = { newEntry = null },
            onConfirm = { name -> newEntry = null; vm.createEntry(name, folder) },
        )
    }

    binaryWarning?.let { e ->
        AlertDialog(
            onDismissRequest = { binaryWarning = null },
            title = { Text("Can't edit ${e.name}") },
            text = { Text("This looks like a binary file (world data, an archive or a program). Editing it as text would corrupt it.") },
            confirmButton = { TextButton(onClick = { binaryWarning = null }) { Text("OK") } },
        )
    }
}

private fun relativeLabel(path: String?, root: String?): String {
    if (path == null) return ""
    val r = root?.trimEnd('/').orEmpty()
    return if (r.isNotEmpty() && path.startsWith(r)) "/" + path.removePrefix(r).trimStart('/') else path
}

// ---------------------------------------------------------------- file editor

class FileEditorViewModel(private val api: CraftyApi, private val serverId: String, val path: String) : ViewModel() {
    var original by mutableStateOf<String?>(null)
        private set
    var text by mutableStateOf("")
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val dirty: Boolean get() = original != null && text != original

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                when (val r = api.browse(serverId, path)) {
                    is FileResult.Text -> {
                        if (r.contents.length > MAX_EDIT_CHARS) {
                            error = "This file is too large to edit on a phone (${r.contents.length / 1024} KB)."
                        } else {
                            original = r.contents
                            text = r.contents
                        }
                    }
                    is FileResult.Folder -> error = "Crafty returned a folder listing instead of the file's text."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't open the file."
            } finally {
                loading = false
            }
        }
    }

    suspend fun save(): String? = try {
        saving = true
        api.saveFile(serverId, path, text)
        original = text
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.message ?: "Couldn't save."
    } finally {
        saving = false
    }

    companion object {
        const val MAX_EDIT_CHARS = 512 * 1024
    }
}

@Composable
fun FileEditorScreen(api: CraftyApi, serverId: String, path: String, onBack: () -> Unit) {
    // Config files often hold passwords and tokens (server.properties RCON, plugin configs).
    com.vknxworks.craftyctrl.ui.SecureScreen()
    val vm: FileEditorViewModel = viewModel(factory = viewModelFactory { initializer { FileEditorViewModel(api, serverId, path) } })
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmLeave by remember { mutableStateOf(false) }
    var wrap by rememberSaveable { mutableStateOf(false) }

    val leave: () -> Unit = {
        if (vm.dirty) confirmLeave = true else onBack()
    }
    BackHandler(onBack = leave)

    fun save() {
        scope.launch {
            val err = vm.save()
            snackbar.showSnackbar(err ?: "Saved. Most settings apply after a server restart.")
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            path.substringAfterLast('/') + if (vm.dirty) " •" else "",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    FilterChip(selected = wrap, onClick = { wrap = !wrap }, label = { Text("Wrap") })
                    if (vm.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = ::save, enabled = vm.dirty) { Icon(Icons.Filled.Save, contentDescription = "Save") }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null -> ErrorBanner(vm.error!!, Modifier.padding(16.dp), onRetry = vm::load)
                else -> Surface(color = ConsoleBackground, modifier = Modifier.fillMaxSize()) {
                    val textStyle = TextStyle(color = ConsoleText, fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp)
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    val base = Modifier.fillMaxSize().verticalScroll(vScroll)
                    val scrollModifier = (if (wrap) base else base.horizontalScroll(hScroll)).padding(12.dp)
                    BasicTextField(
                        value = vm.text,
                        onValueChange = { vm.text = it },
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = scrollModifier,
                    )
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved edits to ${path.substringAfterLast('/')}.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep editing") } },
        )
    }
}

/** Names can't contain path separators or be "." / "..", so nothing lands outside the folder. */
private fun validName(name: String): String? = when {
    name.isBlank() -> "Enter a name"
    name.trim() in listOf(".", "..") -> "That name isn't allowed"
    name.any { it == '/' || it == '\\' || it.code < 32 } -> "Names can't contain / or \\"
    name.length > 120 -> "That name is too long"
    else -> null
}

@Composable
private fun NameDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
    confirmLabel: String = "Create",
) {
    var name by remember { mutableStateOf(initial) }
    val error = if (name.isEmpty()) null else validName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(hint) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = validName(name) == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

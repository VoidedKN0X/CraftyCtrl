@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vknxworks.craftyremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vknxworks.craftyremote.data.CraftyApi
import com.vknxworks.craftyremote.data.ServerSummary
import com.vknxworks.craftyremote.ui.ErrorBanner
import com.vknxworks.craftyremote.ui.SectionLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class Edition { JAVA, BEDROCK }

/** Server types offered when Crafty's download list can't be loaded. */
private val FALLBACK_TYPES = listOf("vanilla", "paper", "purpur", "fabric")

private val versionPattern = Regex("""^\d+(\.\d+)*([.-][\w.]+)?$""")

/** Newest first, comparing numerically part by part ("1.21.10" > "1.21.9"). */
private val versionOrder = Comparator<String> { a, b ->
    val pa = a.split('.', '-').map { it.toIntOrNull() ?: -1 }
    val pb = b.split('.', '-').map { it.toIntOrNull() ?: -1 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val c = (pb.getOrElse(i) { -1 }).compareTo(pa.getOrElse(i) { -1 })
        if (c != 0) return@Comparator c
    }
    0
}

sealed interface CreateStep {
    data object Form : CreateStep
    data object Creating : CreateStep
    data class Downloading(val seconds: Int) : CreateStep
    data object Starting : CreateStep
    data class Done(val id: String, val name: String, val started: Boolean, val note: String?) : CreateStep
}

class CreateServerViewModel(private val api: CraftyApi) : ViewModel() {
    var existing by mutableStateOf<List<ServerSummary>>(emptyList())
        private set

    /** Java server types and their versions from Crafty's download service; empty if unavailable. */
    var catalog by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    var catalogLoading by mutableStateOf(true)
        private set

    var step by mutableStateOf<CreateStep>(CreateStep.Form)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { existing = runCatching { api.servers() }.getOrDefault(emptyList()) }
        viewModelScope.launch {
            catalog = runCatching { loadCatalog() }.getOrDefault(emptyMap())
            catalogLoading = false
        }
    }

    fun suggestPort(edition: Edition): Int {
        val used = existing.mapNotNull { it.port }.toSet()
        var p = if (edition == Edition.JAVA) 25565 else 19132
        while (p in used) p += if (edition == Edition.JAVA) 1 else 2 // Bedrock also uses port+1 for IPv6
        return p
    }

    fun create(body: JsonObject, name: String, startAfter: Boolean) {
        if (step != CreateStep.Form) return
        error = null
        viewModelScope.launch {
            step = CreateStep.Creating
            val id = try {
                api.createServer(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Crafty couldn't create the server."
                step = CreateStep.Form
                return@launch
            }

            // Crafty downloads the server files in the background; wait until it's done.
            val started = System.currentTimeMillis()
            var ready = false
            while (System.currentTimeMillis() - started < DOWNLOAD_TIMEOUT_MS) {
                step = CreateStep.Downloading(((System.currentTimeMillis() - started) / 1000).toInt())
                val s = runCatching { api.stats(id) }.getOrNull()
                if (s != null && !s.importing && !s.updating) {
                    ready = true
                    break
                }
                delay(2_000)
            }
            if (!ready) {
                step = CreateStep.Done(id, name, false, "Crafty is still preparing the server. Start it from its page once it's ready.")
                return@launch
            }
            if (!startAfter) {
                step = CreateStep.Done(id, name, false, null)
                return@launch
            }
            step = CreateStep.Starting
            val note = try {
                // The user accepted the EULA in the form; Java servers need it written to eula.txt.
                val info = runCatching { api.serverInfo(id) }.getOrNull()
                if (info?.path != null && !info.isBedrock) api.acceptEula(id, info.path)
                api.action(id, "start_server")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Created, but starting failed: ${e.message}"
            }
            step = CreateStep.Done(id, name, note == null, note)
        }
    }

    fun clearError() {
        error = null
    }

    /**
     * Reads Crafty's download list ("big bucket") from the phone. The format isn't documented,
     * so this looks for the "mc_java_servers" category and reads type names and version keys.
     */
    private suspend fun loadCatalog(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$BUCKET/manifest.json").header("Accept", "application/json").build()
        val text = catalogHttp.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return@withContext emptyMap()
        val root = Json.parseToJsonElement(text)
        val category = findKey(root, "mc_java_servers") as? JsonObject ?: return@withContext emptyMap()
        val types = (category["types"] as? JsonObject) ?: category
        types.mapNotNull { (type, value) ->
            val versions = versionsOf(value).sortedWith(versionOrder)
            if (versions.isEmpty()) null else type to versions
        }.toMap()
    }

    private fun findKey(e: JsonElement, key: String): JsonElement? = when (e) {
        is JsonObject -> e[key] ?: e.values.firstNotNullOfOrNull { findKey(it, key) }
        is JsonArray -> e.firstNotNullOfOrNull { findKey(it, key) }
        else -> null
    }

    private fun versionsOf(e: JsonElement): List<String> {
        val holder = (e as? JsonObject)?.get("versions") ?: e
        return when (holder) {
            is JsonObject -> holder.keys.filter { versionPattern.matches(it) }
            is JsonArray -> holder.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(versionPattern::matches) }
            else -> emptyList()
        }
    }

    companion object {
        private const val BUCKET = "https://jars.arcadiatech.org"
        private const val DOWNLOAD_TIMEOUT_MS = 10 * 60_000L
        private val catalogHttp = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

@Composable
fun CreateServerScreen(
    api: CraftyApi,
    onBack: () -> Unit,
    onOpenServer: (id: String, name: String) -> Unit,
) {
    val vm: CreateServerViewModel = viewModel(factory = viewModelFactory { initializer { CreateServerViewModel(api) } })
    val busy = vm.step !is CreateStep.Form && vm.step !is CreateStep.Done
    // Leaving mid-creation is fine (Crafty keeps going), but ask the system back to wait while busy.
    BackHandler(enabled = busy) {}

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("New server", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            when (val s = vm.step) {
                CreateStep.Form -> CreateForm(vm)
                CreateStep.Creating -> Progress("Creating the server…", null)
                is CreateStep.Downloading -> Progress(
                    "Downloading server files…",
                    "Crafty is fetching the server software. This usually takes under a minute (${s.seconds}s so far).",
                )
                CreateStep.Starting -> Progress("Starting the server…", null)
                is CreateStep.Done -> DoneView(s, onBack = onBack, onOpen = { onOpenServer(s.id, s.name) })
            }
        }
    }
}

@Composable
private fun CreateForm(vm: CreateServerViewModel) {
    val uri = LocalUriHandler.current
    var edition by rememberSaveable { mutableStateOf(Edition.JAVA) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("paper") }
    var version by rememberSaveable { mutableStateOf("") }
    var memMin by rememberSaveable { mutableStateOf("2") }
    var memMax by rememberSaveable { mutableStateOf("4") }
    var port by rememberSaveable { mutableStateOf("") }
    var eula by rememberSaveable { mutableStateOf(false) }
    var startAfter by rememberSaveable { mutableStateOf(true) }
    var versionMenu by remember { mutableStateOf(false) }

    // Fill in a free port once the existing servers are known, unless the user typed one.
    LaunchedEffect(vm.existing, edition) {
        if (port.isEmpty() || port == "25565" || port == "19132") port = vm.suggestPort(edition).toString()
    }

    val types = vm.catalog.keys.sorted().ifEmpty { FALLBACK_TYPES }
    val versions = vm.catalog[type].orEmpty()
    LaunchedEffect(type, vm.catalog) {
        if (versions.isNotEmpty() && version !in versions) version = versions.first()
    }

    val portNum = port.toIntOrNull()
    val portTaken = vm.existing.any { it.port == portNum }
    val minGb = memMin.replace(',', '.').toDoubleOrNull()
    val maxGb = memMax.replace(',', '.').toDoubleOrNull()

    val problem = when {
        name.trim().length < 2 -> "Give the server a name"
        vm.existing.any { it.name.equals(name.trim(), ignoreCase = true) } -> "A server with that name already exists"
        portNum == null || portNum !in 1024..65535 -> "Port must be between 1024 and 65535"
        edition == Edition.JAVA && version.isBlank() -> "Choose a version"
        edition == Edition.JAVA && (minGb == null || maxGb == null || minGb <= 0 || maxGb < minGb) ->
            "Memory: max must be at least min, and both above 0"
        !eula -> "Accept the Minecraft EULA to continue"
        else -> null
    }

    fun gb(v: Double): JsonPrimitive = if (v % 1.0 == 0.0) JsonPrimitive(v.toInt()) else JsonPrimitive(v)

    fun body(): JsonObject = when (edition) {
        Edition.JAVA -> buildJsonObject {
            put("name", name.trim())
            put("monitoring_type", "minecraft_java")
            putJsonObject("minecraft_java_monitoring_data") {
                put("host", "127.0.0.1")
                put("port", portNum!!)
            }
            put("create_type", "minecraft_java")
            putJsonObject("minecraft_java_create_data") {
                put("create_type", "download_jar")
                putJsonObject("download_jar_create_data") {
                    put("category", "mc_java_servers")
                    put("type", type)
                    put("version", version.trim())
                    put("mem_min", gb(minGb!!))
                    put("mem_max", gb(maxGb!!))
                    put("server_properties_port", portNum!!)
                }
            }
        }
        Edition.BEDROCK -> buildJsonObject {
            put("name", name.trim())
            put("monitoring_type", "minecraft_bedrock")
            putJsonObject("minecraft_bedrock_monitoring_data") {
                put("host", "127.0.0.1")
                put("port", portNum!!)
            }
            put("create_type", "minecraft_bedrock")
            putJsonObject("minecraft_bedrock_create_data") {
                put("create_type", "download_exe")
                putJsonObject("download_exe_create_data") { put("agree_to_eula", true) }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        vm.error?.let { ErrorBanner(it, onRetry = vm::clearError) }

        SectionLabel("Edition")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Edition.entries.forEachIndexed { i, e ->
                SegmentedButton(
                    selected = edition == e,
                    onClick = { edition = e },
                    shape = SegmentedButtonDefaults.itemShape(i, Edition.entries.size),
                    icon = {
                        Icon(
                            if (e == Edition.JAVA) Icons.Filled.Dns else Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) { Text(if (e == Edition.JAVA) "Java" else "Bedrock") }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(48) },
            label = { Text("Server name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (edition == Edition.JAVA) {
            SectionLabel("Server software")
            if (vm.catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            ExposedDropdownMenuBox(expanded = versionMenu && versions.isNotEmpty(), onExpandedChange = { versionMenu = it }) {
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it.trim().take(32) },
                    label = { Text("Minecraft version") },
                    placeholder = { Text("e.g. 1.21.4") },
                    supportingText = {
                        Text(
                            if (versions.isEmpty() && !vm.catalogLoading) "Couldn't load the version list, so type the exact version."
                            else "Pick from the list or type a version.",
                        )
                    },
                    singleLine = true,
                    trailingIcon = { if (versions.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(expanded = versionMenu && versions.isNotEmpty(), onDismissRequest = { versionMenu = false }) {
                    versions.take(60).forEach { v ->
                        DropdownMenuItem(text = { Text(v) }, onClick = { version = v; versionMenu = false })
                    }
                }
            }

            SectionLabel("Memory (GB)")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = memMin, onValueChange = { memMin = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
                    label = { Text("Minimum") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = memMax, onValueChange = { memMax = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
                    label = { Text("Maximum") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit).take(5) },
            label = { Text("Game port") },
            singleLine = true,
            isError = portTaken,
            supportingText = {
                Text(
                    when {
                        portTaken -> "Another server already uses this port."
                        edition == Edition.BEDROCK -> "Crafty checks this port. If you change it, set the same server-port in server.properties after creating."
                        else -> "Suggested: the first port none of your servers use."
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { eula = !eula }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = eula, onCheckedChange = { eula = it })
                    Text("I agree to the Minecraft EULA", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { uri.openUri("https://www.minecraft.net/eula") }) { Text("Read") }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Start the server when it's ready", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = startAfter, onCheckedChange = { startAfter = it })
                }
            }
        }

        problem?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = { vm.create(body(), name.trim(), startAfter) },
            enabled = problem == null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Create server") }
        Text(
            "Needs an API key or account with permission to create servers in Crafty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Progress(title: String, body: String?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        body?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DoneView(s: CreateStep.Done, onBack: () -> Unit, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            if (s.started) "${s.name} is starting" else "${s.name} was created",
            style = MaterialTheme.typography.headlineSmall,
        )
        s.note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open server") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onBack) { Text("Back to servers") }
    }
}

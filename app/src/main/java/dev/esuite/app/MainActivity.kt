package dev.esuite.app

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.esuite.app.browser.BrowserProfiles
import dev.esuite.app.browser.BrowserScreen
import dev.esuite.app.core.*
import dev.esuite.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Activity-scoped ViewModel shared with Compose (same ViewModelStore), so onStop can flush it.
    private val model: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WebView.setWebContentsDebuggingEnabled(false)
        setContent { ESuiteTheme { ESuiteApp(model) } }
    }

    // Best-effort background save. Only the committed-snapshot indicator acknowledges durability.
    // flush() is a safe no-op when the workspace is not ready and never throws into this callback.
    override fun onStop() {
        model.flush()
        super.onStop()
    }
}

enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home), Browser("Browser", Icons.Outlined.Search), Traffic("Traffic", Icons.AutoMirrored.Outlined.List),
    Api("API Lab", Icons.AutoMirrored.Outlined.Send), Findings("Findings", Icons.Outlined.CheckCircle), Tools("Tools", Icons.Outlined.Build)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ESuiteApp(model: AppViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var selectedName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val screen = Screen.valueOf(selectedName)
    BackHandler(enabled = screen != Screen.Home) { selectedName = Screen.Home.name }
    val project = state.workspace.projects.firstOrNull { it.id == state.projectId }
    if (!state.ready) {
        if (state.openingWorkspace) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Opening workspace", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }
        var erase by remember { mutableStateOf(false) }
        var removeProfiles by remember { mutableStateOf(false) }
        // Recovery is reachable without the vault, so its report is screen-local: an unreadable
        // workspace cannot tell us which projects owned which profile.
        var profileReport by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ESuite · encrypted workspace", style = MaterialTheme.typography.titleLarge)
            Text(state.message)
            OutlinedButton(onClick = model::loadWorkspace) { Text("Retry open") }
            TextButton(onClick = { removeProfiles = true }) { Text("Remove all ESuite browser profiles") }
            if (profileReport.isNotBlank()) Text(profileReport, style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = { erase = true }) { Text("Erase unreadable vault") }
            if (removeProfiles) AlertDialog(onDismissRequest = { removeProfiles = false },
                title = { Text("Remove all ESuite browser profiles?") },
                text = { Text("Every browser profile ESuite created is deleted by name prefix, including profiles belonging to projects in a workspace that cannot be opened. Other apps' WebView data and the default profile are untouched. This does not need the vault and cannot be undone.") },
                confirmButton = { TextButton(onClick = {
                    removeProfiles = false
                    val result = BrowserProfiles.clearAllEsuiteOwned()
                    profileReport = "Deleted ${result.deleted.size} · pending ${result.pending.size}. ${result.describe()}"
                }) { Text("Remove profiles") } },
                dismissButton = { TextButton(onClick = { removeProfiles = false }) { Text("Cancel") } })
            if (erase) EraseVaultDialog({ erase = false }, model)
        }
        return
    }
    val pageScroll = key(screen, state.projectId, state.identity) { rememberScrollState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val compactNavigation = with(density) { windowSize.height.toDp() < 450.dp }
    val displayLabels = !compactNavigation && density.fontScale <= 1.3f && with(density) { windowSize.width.toDp() >= 360.dp }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = Midnight,
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        bottomBar = {
            if (!imeVisible) WorkspaceNavigation(screen, compactNavigation, displayLabels) { selectedName = it.name }
        },
    ) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).imePadding()) {
            state.manualRequestLabel?.let { label ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sending · $label", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = model::cancelRequest, modifier = Modifier.testTag("cancel_running_request")) { Text("Cancel") }
                }
            }
            when {
                screen == Screen.Browser && project != null -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                        Row(Modifier.fillMaxWidth().testTag("browser_identity_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(project.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Signal)
                            listOf("A", "B").forEach { identity ->
                                FilterChip(modifier = Modifier.testTag("identity_$identity"), selected = state.identity == identity,
                                    onClick = { model.identity(identity) }, label = { Text(identity) })
                            }
                            WorkspaceStatusButton(project, state)
                        }
                        BrowserScreen(project, state.identity, model, Modifier.weight(1f))
                    }
                }
                screen == Screen.Findings -> {
                    key(state.projectId) { FindingsScreen(state, model, project) { selectedName = Screen.Api.name } }
                }
                screen == Screen.Traffic -> {
                    key(state.projectId) { TrafficScreen(state, model, project) { selectedName = Screen.Api.name } }
                }
                else -> {
                    Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Spacer(Modifier.height(8.dp))
                        WorkspaceStatus(project, state)
                        when (screen) {
                            Screen.Home -> HomeScreen(state, model, project) { selectedName = it.name }
                            Screen.Browser -> Text("Create a project first.")
                            Screen.Traffic -> Unit
                            Screen.Api -> ApiScreen(state, model) { scope.launch { pageScroll.scrollTo(0) } }
                            Screen.Findings -> Unit
                            Screen.Tools -> ToolsScreen(state, model)
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun WorkspaceNavigation(screen: Screen, compact: Boolean, displayLabels: Boolean, navigate: (Screen) -> Unit) {
    val destinations = Screen.entries.filter { it != Screen.Tools }
    if (compact) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
            destinations.forEach { target ->
                Box(Modifier.weight(1f).heightIn(min = 48.dp).testTag("nav_${target.name.lowercase()}")
                    .selectable(screen == target, role = Role.Tab, onClick = { navigate(target) }), contentAlignment = Alignment.Center) {
                    Icon(target.icon, contentDescription = target.label, tint = if (screen == target) Signal else Muted)
                }
            }
        }
    } else {
        NavigationBar(containerColor = Midnight) {
            destinations.forEach { target ->
                NavigationBarItem(
                    modifier = Modifier.testTag("nav_${target.name.lowercase()}"),
                    selected = screen == target,
                    onClick = { navigate(target) },
                    icon = { Icon(target.icon, contentDescription = if (displayLabels) null else target.label) },
                    label = if (displayLabels) ({ Text(target.label, maxLines = 1) }) else null,
                )
            }
        }
    }
}

@Composable internal fun IdentityControls(state: AppState, model: AppViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("A", "B").forEach { identity ->
            FilterChip(modifier = Modifier.testTag("identity_$identity"), selected = state.identity == identity,
                onClick = { model.identity(identity) }, label = { Text("Identity $identity") })
        }
    }
}

@Composable internal fun WorkspaceStatus(project: Project?, state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(project?.let { "${it.name} · ${it.origin} · identity ${state.identity}" } ?: "No active project", Modifier.testTag("workspace_context"),
            color = Signal, style = MaterialTheme.typography.bodySmall)
        if (state.message.isNotBlank()) Text(state.message, Modifier.testTag("workspace_status").semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(state.saveError ?: if (state.pendingWrites == 0 && state.persistedWorkspace == state.workspace) "Workspace saved with AES-GCM" else "Workspace edits pending encrypted save…",
            Modifier.testTag("workspace_save_status"), style = MaterialTheme.typography.bodySmall, color = if (state.saveError == null) Muted else Amber)
    }
}

@Composable private fun WorkspaceStatusButton(project: Project, state: AppState) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Info, contentDescription = "Workspace status", tint = if (state.saveError == null) Signal else Amber)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Workspace status") },
        text = { Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) { WorkspaceStatus(project, state) } },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}

@Composable fun Panel(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Raised)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(body, color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable private fun TrafficScreen(state: AppState, model: AppViewModel, project: Project?, openApi: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Exchange?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val history = remember(state.workspace.exchanges, state.projectId) {
        state.workspace.exchanges.filter { it.projectId == state.projectId }
    }
    val entries = remember(history, filter) {
        val query = filter.trim()
        if (query.isEmpty()) history else history.filter { exchange ->
            "${exchange.request.method} ${exchange.request.url} ${exchange.source}".contains(query, ignoreCase = true)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { WorkspaceStatus(project, state) }
        item { Text("Traffic", style = MaterialTheme.typography.headlineSmall) }
        item {
            OutlinedTextField(filter, { filter = it }, singleLine = true, keyboardOptions = TechnicalKeyboard,
                label = { Text("Filter method, URL or source") }, modifier = Modifier.fillMaxWidth())
        }
        if (entries.isEmpty()) item { Text("No matching history. Browse an in-scope page or send an API request.") }
        items(entries, key = { it.id }) { exchange ->
            OutlinedButton(onClick = { model.select(exchange); detail = exchange }, modifier = Modifier.fillMaxWidth().testTag("traffic_exchange")) {
                Text("${exchange.request.method} ${exchange.request.url}\n${exchange.source} · identity ${exchange.identity} · ${exchange.status ?: if (exchange.error != null) "error" else "metadata only"}", Modifier.fillMaxWidth())
            }
        }
        if (history.isNotEmpty()) item { OutlinedButton(onClick = { confirmClear = true }) { Text("Clear this project's history") } }
    }
    detail?.let { exchange ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text("Exchange detail", Modifier.testTag("traffic_detail")) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ResultAttribution.label(exchange, project?.name))
                Text(exchange.completeness)
                exchange.error?.let { Text(it) }
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${exchange.request.method} ${exchange.request.url}", style = technicalTextStyle())
                        if (exchange.request.headers.isNotEmpty()) Text(exchange.request.headers.joinToString("\n") { "${it.name}: ${it.value}" }, style = technicalTextStyle())
                        if (exchange.request.body.isNotEmpty()) Text(exchange.request.body, style = technicalTextStyle())
                        if (exchange.status != null) Text("HTTP ${exchange.status} · ${exchange.durationMs} ms")
                        if (exchange.responseHeaders.isNotEmpty()) Text(exchange.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" }, style = technicalTextStyle())
                        if (exchange.response.value.isNotEmpty()) {
                            Text("Response body · ${exchange.response.encoding}")
                            Text(exchange.response.value, style = technicalTextStyle())
                        }
                        if (exchange.response.truncated) Text("Response body truncated", color = Amber)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { detail = null; model.replay(exchange); openApi() }) { Text("Review in API Lab") } },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("Close") } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear project history?") },
        text = { Text("This removes history entries for the active project. Evidence saved with observations stays in the workspace.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; model.clearHistory() }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

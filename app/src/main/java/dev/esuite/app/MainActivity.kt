package dev.esuite.app

import android.os.Bundle
import android.webkit.WebView
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.esuite.app.browser.BrowserProfiles
import dev.esuite.app.browser.BrowserScreen
import dev.esuite.app.core.*
import dev.esuite.app.ui.*

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
    Api("API Lab", Icons.Outlined.Build), Findings("Findings", Icons.Outlined.CheckCircle), Tools("Tools", Icons.Outlined.Build)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ESuiteApp(model: AppViewModel = viewModel()) {
    val state by model.state.collectAsState()
    var selectedName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val screen = Screen.valueOf(selectedName)
    val project = state.workspace.projects.firstOrNull { it.id == state.projectId }
    if (!state.ready) {
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
                screen == Screen.Traffic -> {
                    key(state.projectId) { TrafficScreen(state, model, project) { selectedName = Screen.Api.name } }
                }
                else -> {
                    Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Spacer(Modifier.height(8.dp))
                        WorkspaceStatus(project, state)
                        if (project != null) IdentityControls(state, model)
                        when (screen) {
                            Screen.Home -> HomeScreen(state, model) { selectedName = it.name }
                            Screen.Browser -> Text("Create a project first.")
                            Screen.Traffic -> Unit
                            Screen.Api -> ApiScreen(state, model)
                            Screen.Findings -> FindingsScreen(state, model) { selectedName = Screen.Api.name }
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

@Composable private fun IdentityControls(state: AppState, model: AppViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("A", "B").forEach { identity ->
            FilterChip(modifier = Modifier.testTag("identity_$identity"), selected = state.identity == identity,
                onClick = { model.identity(identity) }, label = { Text("Identity $identity") })
        }
    }
}

@Composable private fun WorkspaceStatus(project: Project?, state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(project?.let { "${it.name} · ${it.origin} · identity ${state.identity}" } ?: "No active project", Modifier.testTag("workspace_context"),
            color = Signal, style = MaterialTheme.typography.bodySmall)
        Text(state.message, Modifier.testTag("workspace_status").semantics { liveRegion = LiveRegionMode.Polite },
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

@Composable private fun HomeScreen(state: AppState, model: AppViewModel, navigate: (Screen) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf("") }; var origin by remember { mutableStateOf("") }
    Text("Your workspace.", style = MaterialTheme.typography.headlineLarge)
    state.workspace.projects.forEach { p -> OutlinedButton(onClick = { model.selectProject(p.id) }, modifier = Modifier.fillMaxWidth()) { Text("${if (state.projectId == p.id) "✓ " else ""}${p.name} · ${p.origin}") } }
    OutlinedTextField(name, { name = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth().testTag("project_name"))
    OutlinedTextField(origin, { origin = it }, label = { Text("Exact origin, e.g. https://api.example.test") }, keyboardOptions = UrlKeyboard, modifier = Modifier.fillMaxWidth().testTag("project_origin"))
    Button(modifier = Modifier.testTag("create_project"), onClick = { val count = model.state.value.workspace.projects.size; model.createProject(name, origin); if (model.state.value.workspace.projects.size > count) { focus.clearFocus(); keyboard?.hide() } }, enabled = !state.busy) { Text("Create scoped project") }
    Text("HTTPS-only runtime with system trust validation. No requests run on startup.", style = MaterialTheme.typography.bodySmall)
    Text("${state.workspace.exchanges.count { it.projectId == state.projectId }} history entries · ${state.workspace.findings.count { it.projectId == state.projectId }} observations")
    listOf(Screen.Browser, Screen.Traffic, Screen.Api, Screen.Tools, Screen.Findings).forEach { target ->
        OutlinedButton(onClick = { navigate(target) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home_${target.name.lowercase()}")) { Text(target.label, Modifier.weight(1f)); Text("↗") }
    }
    Panel("Capture status", "Browser request metadata and API responses are available. Full browser interception and device-wide VPN forwarding are not implemented yet.")
    WorkspaceControls(state, model)
}

@Composable private fun TrafficScreen(state: AppState, model: AppViewModel, project: Project?, openApi: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Exchange?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val history = state.workspace.exchanges.filter { it.projectId == state.projectId }
    val entries = history.filter { "${it.request.method} ${it.request.url} ${it.source}".contains(filter.trim(), ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { WorkspaceStatus(project, state) }
        if (project != null) item { IdentityControls(state, model) }
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

@Composable private fun ApiScreen(state: AppState, model: AppViewModel) {
    val draft = state.draft
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pending by model.drafts.values.collectAsState()
    fun draftText(editor: Editor, fallback: String = "") = pending[DraftKey.of(state.projectId, editor, state.identity)] ?: fallback
    fun write(editor: Editor, value: String) = model.drafts.set(DraftKey.of(state.projectId, editor, state.identity), value)
    val headers = draftText(Editor.ApiHeaders, draft.headers.joinToString("\n") { "${it.name}: ${it.value}" })
    val raw = draftText(Editor.ApiRaw)
    val savedName = draftText(Editor.ApiSavedName)
    // Keyed by identity as well as project, so this field always shows the SELECTED identity's
    // pending text or its saved configuration — never the other identity's.
    val identityKey = DraftKey.of(state.projectId, Editor.ApiIdentityHeaders, state.identity)
    val identityHeaders = pending[identityKey]
        ?: state.workspace.identityConfigs.firstOrNull { it.projectId == state.projectId && it.identity == state.identity }?.headers.orEmpty().joinToString("\n") { "${it.name}: ${it.value}" }
    fun prepare(action: () -> Unit) { focus.clearFocus(); keyboard?.hide(); runCatching { DraftParser.headers(headers) }.onSuccess { model.draft(draft.copy(headers = it)); action() }.onFailure { model.message(it.message ?: "Invalid headers") } }
    Text("Request lab.", style = MaterialTheme.typography.headlineLarge)
    DraftNotice()
    OutlinedTextField(draft.method, { model.draft(draft.copy(method = it.uppercase())) }, label = { Text("HTTP method") }, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_method"))
    OutlinedTextField(draft.url, { model.draft(draft.copy(url = it)) }, label = { Text("URL (must match project origin)") }, keyboardOptions = UrlKeyboard, modifier = Modifier.fillMaxWidth().testTag("api_url"))
    OutlinedTextField(headers, { write(Editor.ApiHeaders, it) }, label = { Text("Headers — Name: value") }, minLines = 3, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_headers"))
    OutlinedTextField(draft.body, { model.draft(draft.copy(body = it)) }, label = { Text("Request body") }, minLines = 3, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_body"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(modifier = Modifier.testTag("api_send"), onClick = { prepare { model.send() } }, enabled = !state.busy && state.projectId != null) { Text("Send") }
        OutlinedButton(onClick = { model.cancelRequest() }, enabled = state.busy) { Text("Cancel") }
    }
    OutlinedButton(modifier = Modifier.testTag("api_compare"), onClick = { prepare { model.send(compare = true) } }, enabled = !state.busy && state.projectId != null) { Text("Send as A then B (two requests)") }
    Text("Redirects/retries are disabled. Comparison strips known auth headers and uses each identity's configuration/jar. Body credentials remain unchanged; review them before comparing.", style = MaterialTheme.typography.bodySmall)
    if (state.comparison.isNotBlank()) Panel("Comparison", state.comparison)
    state.selected?.let { e -> Panel(ResultAttribution.label(e, state.workspace.projects.firstOrNull { it.id == e.projectId }?.name) + "\n${e.status ?: "Error"} · ${e.durationMs} ms · ${e.response.encoding}${if (e.response.truncated) " · TRUNCATED" else ""}", e.error ?: e.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" } + "\n\n" + e.response.value) }
    OutlinedTextField(raw, { write(Editor.ApiRaw, it) }, label = { Text("Import raw HTTP/1.x message") }, minLines = 3, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { model.currentProject()?.let { p -> runCatching { DraftParser.raw(raw, p.origin) }.onSuccess(model::draft).onFailure { model.message(it.message ?: "Invalid request") } } }) { Text("Import into editor") }
    OutlinedButton(modifier = Modifier.testTag("api_session_metadata"), onClick = { model.message(model.currentProject()?.let { model.engine.sessions.jar(it.id, state.identity).describe() }.orEmpty().ifBlank { "No session cookies for this identity" }) }) { Text("Show API session metadata") }
    OutlinedButton(onClick = { model.clearSessions() }) { Text("Clear API sessions") }
    OutlinedTextField(identityHeaders, { model.drafts.set(identityKey, it) }, label = { Text("Identity ${state.identity} headers (encrypted)") }, minLines = 2, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_identity_headers"))
    OutlinedButton(modifier = Modifier.testTag("api_save_identity"), onClick = { focus.clearFocus(); keyboard?.hide(); model.configureIdentity(identityHeaders) }) { Text("Save this identity's auth headers") }
    OutlinedTextField(savedName, { write(Editor.ApiSavedName, it) }, label = { Text("Saved request name") }, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_saved_name"))
    OutlinedButton(modifier = Modifier.testTag("api_save_draft"), onClick = { prepare { model.saveDraft(savedName) } }) { Text("Save request to collection") }
    state.workspace.savedRequests.filter { it.projectId == state.projectId }.forEach { saved ->
        OutlinedButton(onClick = { model.draft(saved.draft) }, modifier = Modifier.fillMaxWidth()) { Text("Load: ${saved.name}") }
        TextButton(onClick = { model.deleteSavedRequest(saved.id) }) { Text("Remove saved request") }
    }
}

@Composable private fun FindingsScreen(state: AppState, model: AppViewModel, openApi: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pending by model.drafts.values.collectAsState()
    val titleKey = DraftKey.of(state.projectId, Editor.FindingTitle, state.identity)
    val detailKey = DraftKey.of(state.projectId, Editor.FindingDetail, state.identity)
    val title = pending[titleKey].orEmpty(); val detail = pending[detailKey].orEmpty()
    Text("Evidence before conclusions.", style = MaterialTheme.typography.headlineSmall)
    DraftNotice()
    state.selected?.let { exchange ->
        PassiveAnalyzer.inspect(exchange).forEach { observation ->
            Panel(observation.title, observation.evidence)
            OutlinedButton(onClick = { model.addFinding(observation.title, observation.evidence, exchange) }) { Text("Save as observation") }
        }
    }
    OutlinedTextField(title, { model.drafts.set(titleKey, it) }, label = { Text("Observation title") }, modifier = Modifier.fillMaxWidth().testTag("finding_title"))
    OutlinedTextField(detail, { model.drafts.set(detailKey, it) }, label = { Text("Evidence / reproduction / next test") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("finding_detail"))
    Button(modifier = Modifier.testTag("save_finding"), onClick = { if (model.addFinding(title, detail)) { model.drafts.clear(titleKey); model.drafts.clear(detailKey); focus.clearFocus(); keyboard?.hide() } }, enabled = state.projectId != null) { Text("Save observation") }
    state.workspace.findings.filter { it.projectId == state.projectId }.forEach { finding ->
        key(finding.id) {
            var confirmRemoval by remember { mutableStateOf(false) }
            Panel("${finding.state}: ${finding.title}", finding.detail)
            FindingEvidence(finding, model, openApi)
            LocalReportAction("Preview observation report", "esuite-observation.json", { model.exportObservation(finding.id) }, model::message)
            TextButton(onClick = { confirmRemoval = true }) { Text("Remove observation") }
            if (confirmRemoval) AlertDialog(onDismissRequest = { confirmRemoval = false },
                title = { Text("Remove this observation?") },
                text = { Text("The note and its ${finding.evidence.size} retained request/response copies are deleted from the encrypted workspace. This cannot be undone.") },
                confirmButton = { TextButton(onClick = { confirmRemoval = false; model.deleteFinding(finding.id) }) { Text("Remove observation and evidence") } },
                dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Keep observation") } })
        }
    }
}

/** One row per referenced evidence id: a viewer for a retained snapshot, an explicit state for a reference with none. */
@Composable private fun FindingEvidence(finding: Finding, model: AppViewModel, openApi: () -> Unit) {
    if (finding.evidenceIds.isEmpty()) {
        Text("No exchange was attached to this observation.", style = MaterialTheme.typography.bodySmall, color = Muted)
        return
    }
    val missing = EvidenceRetention.missing(finding).toSet()
    finding.evidenceIds.forEachIndexed { index, evidenceId ->
        key(evidenceId) {
            val snapshot = finding.evidence.firstOrNull { it.id == evidenceId }
            if (snapshot == null || evidenceId in missing) {
                Text("Evidence not retained — history was cleared or this observation predates evidence retention.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            } else {
                var shown by remember { mutableStateOf(false) }
                var confirmEvidence by remember { mutableStateOf(false) }
                OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("finding_evidence_$index"), onClick = { shown = !shown }) {
                    Text("${if (shown) "Hide" else "Show"} retained evidence · ${snapshot.request.method} ${snapshot.request.url}")
                }
                if (shown) {
                    Panel("Retained evidence · ${snapshot.status ?: if (snapshot.error != null) "error" else "metadata only"} · ${snapshot.durationMs} ms",
                        "identity ${snapshot.identity} · ${snapshot.source}\n${snapshot.completeness}\n" +
                            "Immutable copy saved with the observation; bodies are capped at ${Limits.EVIDENCE_BYTES} bytes.\n${snapshot.error.orEmpty()}\n\n" +
                            snapshot.request.headers.joinToString("\n") { "${it.name}: ${it.value}" } + "\n\n${snapshot.request.body}\n\n" +
                            snapshot.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" } +
                            "\n\n${snapshot.response.encoding}${if (snapshot.response.truncated) " · TRUNCATED" else ""}\n${snapshot.response.value}")
                    Button(onClick = { model.replay(snapshot); openApi() }) { Text("To API Lab (review before sending)") }
                    TextButton(onClick = { confirmEvidence = true }) { Text("Remove retained evidence") }
                }
                if (confirmEvidence) AlertDialog(onDismissRequest = { confirmEvidence = false },
                    title = { Text("Remove this retained evidence?") },
                    text = { Text("The saved request/response copy is deleted. The observation keeps its reference and will report evidence as not retained.") },
                    confirmButton = { TextButton(onClick = { confirmEvidence = false; model.removeEvidence(finding.id, snapshot.id) }) { Text("Remove evidence") } },
                    dismissButton = { TextButton(onClick = { confirmEvidence = false }) { Text("Keep evidence") } })
            }
        }
    }
}

@Composable private fun ToolsScreen(state: AppState, model: AppViewModel) {
    val pending by model.drafts.values.collectAsState()
    val inputKey = DraftKey.of(state.projectId, Editor.ToolsInput, state.identity)
    val input = pending[inputKey].orEmpty()
    // Output is derived from the input and the pressed transformation, so it stays screen-local.
    var output by remember { mutableStateOf("") }
    fun transform(action: (String) -> String) { output = runCatching { action(input) }.getOrElse { it.message ?: "Invalid input" } }
    Text("Local utilities.", style = MaterialTheme.typography.headlineLarge)
    DraftNotice()
    OutlinedTextField(input, { model.drafts.set(inputKey, it) }, label = { Text("Input") }, minLines = 4, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth())
    listOf<Pair<String, (String) -> String>>("Base64 encode" to Codec::base64Encode, "Base64 decode" to Codec::base64Decode, "Form URL encode" to Codec::formEncode, "Form URL decode" to Codec::formDecode, "SHA-256" to Codec::sha256, "JWT claims (unverified)" to TokenTools::jwtClaims,
        "Token sample statistics" to { TokenTools.sample(it.lines().filter(String::isNotBlank)) }, "Review AWS JSON offline" to CloudConfigReview::review).forEach { (name, action) -> OutlinedButton(onClick = { transform(action) }, modifier = Modifier.fillMaxWidth()) { Text(name) } }
    OutlinedButton(onClick = { model.importCurl(input) }, modifier = Modifier.fillMaxWidth()) { Text("Import cURL into API Lab (no execution)") }
    OutlinedButton(onClick = { model.importOpenApi(input) }, modifier = Modifier.fillMaxWidth()) { Text("Import OpenAPI 3 JSON to collection") }
    OutlinedButton(onClick = { model.importHar(input) }, modifier = Modifier.fillMaxWidth()) { Text("Import in-scope HAR request drafts") }
    OutlinedTextField(output, {}, readOnly = true, label = { Text("Output") }, minLines = 4, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth())
    Text("All transformations run locally. JWT decoding is not verification; sample statistics do not prove unpredictability.", style = MaterialTheme.typography.bodySmall)
    if (BuildConfig.AUTOMATED_REQUESTS) WorkflowsScreen(state, model)
}

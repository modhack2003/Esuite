package dev.esuite.app

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.esuite.app.browser.BrowserScreen
import dev.esuite.app.core.*
import dev.esuite.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        setContent { ESuiteTheme { ESuiteApp() } }
    }
}

enum class Screen(val label: String, val symbol: String) {
    Home("Home", "E"), Browser("Browser", "◎"), Traffic("Traffic", "⇄"), Api("API Lab", "{}"), Findings("Findings", "⚑"), Tools("Tools", "⌘")
}

@Composable
fun ESuiteApp(model: AppViewModel = viewModel()) {
    val state by model.state.collectAsState()
    var selectedName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val screen = Screen.valueOf(selectedName)
    val project = state.workspace.projects.firstOrNull { it.id == state.projectId }
    if (!state.ready) {
        var erase by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ESuite · encrypted workspace", style = MaterialTheme.typography.titleLarge)
            Text(state.message)
            OutlinedButton(onClick = model::loadWorkspace) { Text("Retry open") }
            TextButton(onClick = { erase = true }) { Text("Erase unreadable vault") }
            if (erase) EraseVaultDialog({ erase = false }, model)
        }
        return
    }
    Scaffold(containerColor = Midnight, bottomBar = {
        NavigationBar(containerColor = Midnight) {
            Screen.entries.filter { it != Screen.Tools }.forEach { target ->
                NavigationBarItem(selected = screen == target, onClick = { selectedName = target.name }, icon = { Text(target.symbol) }, label = { Text(target.label) })
            }
        }
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).imePadding().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("E  ESuite", style = MaterialTheme.typography.titleLarge, color = Signal)
                Text("DEVELOPMENT", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(project?.let { "${it.name} · ${it.origin} · identity ${state.identity}" } ?: "No active project", color = Signal, style = MaterialTheme.typography.bodySmall)
            if (project != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A", "B").forEach { identity -> FilterChip(selected = state.identity == identity, onClick = { model.identity(identity) }, label = { Text("Identity $identity") }) }
            }
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = Muted)
            when (screen) {
                Screen.Home -> HomeScreen(state, model) { selectedName = it.name }
                Screen.Browser -> if (project != null) BrowserScreen(project, state.identity, model) else Text("Create a project first.")
                Screen.Traffic -> TrafficScreen(state, model) { selectedName = Screen.Api.name }
                Screen.Api -> ApiScreen(state, model)
                Screen.Findings -> FindingsScreen(state, model)
                Screen.Tools -> ToolsScreen(state, model)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable fun Panel(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Raised)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(body, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun HomeScreen(state: AppState, model: AppViewModel, navigate: (Screen) -> Unit) {
    var name by remember { mutableStateOf("") }; var origin by remember { mutableStateOf("") }
    Text("Your workspace.", style = MaterialTheme.typography.headlineLarge)
    state.workspace.projects.forEach { p -> OutlinedButton(onClick = { model.selectProject(p.id) }, modifier = Modifier.fillMaxWidth()) { Text("${if (state.projectId == p.id) "✓ " else ""}${p.name} · ${p.origin}") } }
    OutlinedTextField(name, { name = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(origin, { origin = it }, label = { Text("Exact origin, e.g. https://api.example.test") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { model.createProject(name, origin) }, enabled = !state.busy) { Text("Create scoped project") }
    Text("HTTPS-only runtime with system trust validation. No requests run on startup.", style = MaterialTheme.typography.bodySmall)
    Text("${state.workspace.exchanges.count { it.projectId == state.projectId }} history entries · ${state.workspace.findings.count { it.projectId == state.projectId }} observations")
    listOf(Screen.Browser, Screen.Traffic, Screen.Api, Screen.Tools, Screen.Findings).forEach { target ->
        OutlinedButton(onClick = { navigate(target) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(target.label, Modifier.weight(1f)); Text("↗") }
    }
    Panel("Capture status", "Browser request metadata and API responses are available. Full browser interception and device-wide VPN forwarding are not implemented yet.")
    WorkspaceControls(state, model)
}

@Composable private fun TrafficScreen(state: AppState, model: AppViewModel, openApi: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    Text("Every observed exchange.", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(filter, { filter = it }, label = { Text("Filter method, URL or source") }, modifier = Modifier.fillMaxWidth())
    val entries = state.workspace.exchanges.filter { it.projectId == state.projectId && (it.request.method + it.request.url + it.source).contains(filter, true) }
    if (entries.isEmpty()) Text("No matching history. Browse an in-scope page or send an API request.")
    entries.take(50).forEach { e ->
        OutlinedButton(onClick = { model.select(e) }, modifier = Modifier.fillMaxWidth()) { Text("${e.request.method} ${e.request.url}\n${e.source} · identity ${e.identity} · ${e.status ?: if (e.error != null) "error" else "metadata only"}") }
    }
    state.selected?.takeIf { it.projectId == state.projectId }?.let { e ->
        Panel("Exchange detail", "${e.completeness}\n${e.error.orEmpty()}\n\n" + e.request.headers.joinToString("\n") { "${it.name}: ${it.value}" } + "\n\n" + e.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" } + "\n\n${e.response.encoding}${if (e.response.truncated) " · TRUNCATED" else ""}\n${e.response.value}")
        Button(onClick = { model.replay(e); openApi() }) { Text("To API Lab (review before sending)") }
    }
    OutlinedButton(onClick = { model.clearHistory() }) { Text("Clear this project's history") }
}

@Composable private fun ApiScreen(state: AppState, model: AppViewModel) {
    val draft = state.draft
    var headers by remember(draft.headers) { mutableStateOf(draft.headers.joinToString("\n") { "${it.name}: ${it.value}" }) }
    var raw by remember { mutableStateOf("") }
    var savedName by remember { mutableStateOf("") }
    var identityHeaders by remember(state.projectId, state.identity, state.workspace.identityConfigs) { mutableStateOf(state.workspace.identityConfigs.firstOrNull { it.projectId == state.projectId && it.identity == state.identity }?.headers.orEmpty().joinToString("\n") { "${it.name}: ${it.value}" }) }
    fun prepare(action: () -> Unit) { runCatching { DraftParser.headers(headers) }.onSuccess { model.draft(draft.copy(headers = it)); action() }.onFailure { model.message(it.message ?: "Invalid headers") } }
    Text("Request lab.", style = MaterialTheme.typography.headlineLarge)
    OutlinedTextField(draft.method, { model.draft(draft.copy(method = it.uppercase())) }, label = { Text("HTTP method") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(draft.url, { model.draft(draft.copy(url = it)) }, label = { Text("URL (must match project origin)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(headers, { headers = it }, label = { Text("Headers — Name: value") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(draft.body, { model.draft(draft.copy(body = it)) }, label = { Text("Request body") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { prepare { model.send() } }, enabled = !state.busy && state.projectId != null) { Text("Send") }
        OutlinedButton(onClick = { model.cancelRequest() }, enabled = state.busy) { Text("Cancel") }
    }
    OutlinedButton(onClick = { prepare { model.send(compare = true) } }, enabled = !state.busy && state.projectId != null) { Text("Send as A then B (two requests)") }
    Text("Redirects/retries are disabled. Comparison strips known auth headers and uses each identity's configuration/jar. Body credentials remain unchanged; review them before comparing.", style = MaterialTheme.typography.bodySmall)
    if (state.comparison.isNotBlank()) Panel("Comparison", state.comparison)
    state.selected?.let { e -> Panel("${e.status ?: "Error"} · ${e.durationMs} ms · ${e.response.encoding}${if (e.response.truncated) " · TRUNCATED" else ""}", e.error ?: e.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" } + "\n\n" + e.response.value) }
    OutlinedTextField(raw, { raw = it }, label = { Text("Import raw HTTP/1.x message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { model.currentProject()?.let { p -> runCatching { DraftParser.raw(raw, p.origin) }.onSuccess(model::draft).onFailure { model.message(it.message ?: "Invalid request") } } }) { Text("Import into editor") }
    OutlinedButton(onClick = { model.message(model.currentProject()?.let { model.engine.sessions.jar(it.id, state.identity).describe() }.orEmpty().ifBlank { "No session cookies for this identity" }) }) { Text("Show API session metadata") }
    OutlinedButton(onClick = { model.clearSessions() }) { Text("Clear API sessions") }
    OutlinedTextField(identityHeaders, { identityHeaders = it }, label = { Text("Identity ${state.identity} headers (encrypted)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { model.configureIdentity(identityHeaders) }) { Text("Save this identity's auth headers") }
    OutlinedTextField(savedName, { savedName = it }, label = { Text("Saved request name") }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { prepare { model.saveDraft(savedName) } }) { Text("Save request to collection") }
    state.workspace.savedRequests.filter { it.projectId == state.projectId }.forEach { saved ->
        OutlinedButton(onClick = { model.draft(saved.draft) }, modifier = Modifier.fillMaxWidth()) { Text("Load: ${saved.name}") }
        TextButton(onClick = { model.deleteSavedRequest(saved.id) }) { Text("Remove saved request") }
    }
}

@Composable private fun FindingsScreen(state: AppState, model: AppViewModel) {
    var title by remember { mutableStateOf("") }; var detail by remember { mutableStateOf("") }
    Text("Evidence before conclusions.", style = MaterialTheme.typography.headlineSmall)
    state.selected?.let { exchange ->
        PassiveAnalyzer.inspect(exchange).forEach { observation ->
            Panel(observation.title, observation.evidence)
            OutlinedButton(onClick = { model.addFinding(observation.title, observation.evidence, exchange) }) { Text("Save as observation") }
        }
    }
    OutlinedTextField(title, { title = it }, label = { Text("Observation title") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(detail, { detail = it }, label = { Text("Evidence / reproduction / next test") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Button(onClick = { if (model.addFinding(title, detail)) { title = ""; detail = "" } }, enabled = state.projectId != null) { Text("Save observation") }
    state.workspace.findings.filter { it.projectId == state.projectId }.forEach { finding ->
        Panel("${finding.state}: ${finding.title}", finding.detail + "\nEvidence: " + finding.evidenceIds.joinToString())
        TextButton(onClick = { model.deleteFinding(finding.id) }) { Text("Remove observation") }
    }
}

@Composable private fun ToolsScreen(state: AppState, model: AppViewModel) {
    var input by remember { mutableStateOf("") }; var output by remember { mutableStateOf("") }
    fun transform(action: (String) -> String) { output = runCatching { action(input) }.getOrElse { it.message ?: "Invalid input" } }
    Text("Local utilities.", style = MaterialTheme.typography.headlineLarge)
    OutlinedTextField(input, { input = it }, label = { Text("Input") }, minLines = 4, modifier = Modifier.fillMaxWidth())
    listOf<Pair<String, (String) -> String>>("Base64 encode" to Codec::base64Encode, "Base64 decode" to Codec::base64Decode, "Form URL encode" to Codec::formEncode, "Form URL decode" to Codec::formDecode, "SHA-256" to Codec::sha256, "JWT claims (unverified)" to TokenTools::jwtClaims,
        "Token sample statistics" to { TokenTools.sample(it.lines().filter(String::isNotBlank)) }, "Review AWS JSON offline" to CloudConfigReview::review).forEach { (name, action) -> OutlinedButton(onClick = { transform(action) }, modifier = Modifier.fillMaxWidth()) { Text(name) } }
    OutlinedButton(onClick = { model.importCurl(input) }, modifier = Modifier.fillMaxWidth()) { Text("Import cURL into API Lab (no execution)") }
    OutlinedButton(onClick = { model.importOpenApi(input) }, modifier = Modifier.fillMaxWidth()) { Text("Import OpenAPI 3 JSON to collection") }
    OutlinedButton(onClick = { model.importHar(input) }, modifier = Modifier.fillMaxWidth()) { Text("Import in-scope HAR request drafts") }
    OutlinedTextField(output, {}, readOnly = true, label = { Text("Output") }, minLines = 4, modifier = Modifier.fillMaxWidth())
    Text("All transformations run locally. JWT decoding is not verification; sample statistics do not prove unpredictability.", style = MaterialTheme.typography.bodySmall)
    WorkflowsScreen(state, model)
}

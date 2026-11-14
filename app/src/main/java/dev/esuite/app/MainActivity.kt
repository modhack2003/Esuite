package dev.esuite.app

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.esuite.app.core.Codec
import dev.esuite.app.data.demoTraffic
import dev.esuite.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ESuiteTheme { ESuiteApp() } }
    }
}

private enum class Screen(val label: String, val symbol: String) {
    Home("Home", "E"), Browser("Browser", "◎"), Traffic("Traffic", "⇄"), Api("API Lab", "{}"), Tools("Tools", "⌘")
}

@Composable
fun ESuiteApp() {
    var selectedName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val screen = Screen.valueOf(selectedName)
    Scaffold(
        containerColor = Midnight,
        bottomBar = {
            NavigationBar(containerColor = Midnight) {
                Screen.entries.forEach { target ->
                    NavigationBarItem(selected = screen == target, onClick = { selectedName = target.name },
                        icon = { Text(target.symbol) }, label = { Text(target.label) })
                }
            }
        },
    ) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).imePadding().padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("E  ESuite", style = MaterialTheme.typography.titleLarge, color = Signal)
                Text("ALPHA UI", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text("SYNTHETIC DEMO · NO LIVE CAPTURE", color = Signal, style = MaterialTheme.typography.labelSmall)
            when (screen) {
                Screen.Home -> HomeScreen { selectedName = it.name }
                Screen.Browser -> BrowserConcept()
                Screen.Traffic -> TrafficScreen { selectedName = Screen.Api.name }
                Screen.Api -> ApiConcept()
                Screen.Tools -> ToolsScreen()
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun Panel(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Raised)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(body, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HomeScreen(navigate: (Screen) -> Unit) {
    Text("Your workspace.", style = MaterialTheme.typography.headlineLarge)
    Panel("Sandbox API", "api.esuite.test\n\nA bundled demo project. No traffic is captured or transmitted.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("05 fixture requests", color = Signal)
        Text("02 demo identities", color = Amber)
    }
    Text("Start investigating", style = MaterialTheme.typography.titleMedium)
    listOf(Screen.Browser, Screen.Traffic, Screen.Api, Screen.Tools).forEach { target ->
        OutlinedButton(onClick = { navigate(target) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(target.label, Modifier.weight(1f)); Text("↗")
        }
    }
    Panel("Your work stays with you.", "This starter uses in-memory fixtures and local utilities. Browser inspection, VPN capture and encrypted project storage are planned.")
}

@Composable
private fun BrowserConcept() {
    var tab by rememberSaveable { mutableStateOf("Elements") }
    Text("Look closer.", style = MaterialTheme.typography.headlineLarge)
    Panel("sandbox.esuite.test", "Browser inspector concept. This screen is not a live WebView.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Elements", "Cookies").forEach { name -> FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) }) }
    }
    SelectionContainer {
        Text(if (tab == "Elements") "<button class=\"profile-action\">\n  View profile\n</button>"
            else "session_demo = synthetic-session-value\nDomain: sandbox.esuite.test\nPath: /\nHttpOnly: true\nSecure: true\nSameSite: Lax",
            color = Signal, fontFamily = FontFamily.Monospace)
    }
    Panel("Native inspection is next", "Cookie APIs and the capture engine must be validated before this becomes a live inspection screen.")
}

@Composable
private fun TrafficScreen(openApi: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Text("Every request.", style = MaterialTheme.typography.headlineLarge)
    Text("Bundled fixture history · no capture service running", color = Muted)
    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Filter path or method") }, modifier = Modifier.fillMaxWidth())
    val filtered = demoTraffic.filter { (it.method + it.path).contains(query, ignoreCase = true) }
    if (filtered.isEmpty()) Text("No fixture requests match.", color = Muted)
    filtered.forEach { request ->
        Panel("${request.method}  ${request.path}", "${request.status} · ${request.durationMs} ms · api.esuite.test\nSynthetic response record")
    }
    Button(onClick = openApi, modifier = Modifier.fillMaxWidth()) { Text("Open API Lab concept") }
}

@Composable
private fun ApiConcept() {
    var identity by rememberSaveable { mutableStateOf("Account A") }
    var request by rememberSaveable { mutableStateOf("GET /api/profile HTTP/1.1\nHost: api.esuite.test\nAccept: application/json") }
    var response by rememberSaveable { mutableStateOf("") }
    Text("Request lab.", style = MaterialTheme.typography.headlineLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Account A", "Account B").forEach { account -> FilterChip(selected = identity == account, onClick = { identity = account; response = "" }, label = { Text(account) }) }
    }
    OutlinedTextField(value = request, onValueChange = { request = it; response = "" }, label = { Text("Editable request · no network") }, minLines = 5, modifier = Modifier.fillMaxWidth())
    Button(onClick = { response = if (identity == "Account A") "{\"id\":\"user_a\",\"role\":\"member\"}" else "{\"id\":\"user_b\",\"role\":\"member\"}" }, modifier = Modifier.fillMaxWidth()) { Text("Load identity fixture") }
    if (response.isNotEmpty()) SelectionContainer { Text(response, color = Signal, fontFamily = FontFamily.Monospace) }
    Text("The edited request is not executed. Live HTTP replay and real session isolation are planned.", color = Muted)
}

@Composable
private fun ToolsScreen() {
    var input by rememberSaveable { mutableStateOf("ESuite: inspect anywhere.") }
    var output by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    fun transform(action: (String) -> String) {
        runCatching { action(input) }.onSuccess { output = it; error = false }
            .onFailure { output = "Invalid input for this transformation."; error = true }
    }
    Text("Local utilities.", style = MaterialTheme.typography.headlineLarge)
    Text("Working locally · no network or persistence", color = Signal)
    OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Input") }, minLines = 4, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { transform(Codec::base64Encode) }) { Text("Base64 encode") }
        OutlinedButton(onClick = { transform(Codec::base64Decode) }) { Text("Decode") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { transform(Codec::formEncode) }) { Text("Form URL encode") }
        OutlinedButton(onClick = { transform(Codec::formDecode) }) { Text("Decode") }
    }
    OutlinedButton(onClick = { transform(Codec::sha256) }, modifier = Modifier.fillMaxWidth()) { Text("SHA-256 hash") }
    OutlinedTextField(value = output, onValueChange = {}, readOnly = true, isError = error, label = { Text("Output") }, minLines = 4, modifier = Modifier.fillMaxWidth())
    Text("Form URL encoding uses '+' for spaces. Hashing is one-way.", color = Muted)
}

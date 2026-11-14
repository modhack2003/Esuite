package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.automation.PayloadMode

@Composable
fun WorkflowsScreen(state: AppState, model: AppViewModel) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PayloadMode.ONE_POSITION) }
    var sequence by remember { mutableStateOf("") }; var tokenPath by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf("") }
    Text("Bounded tasks", style = MaterialTheme.typography.titleLarge)
    Text(state.taskProgress)
    Text("Use the current API draft. Place {{P1}} / {{P2}} in its URL, header values or body. Each run sends up to 50 sequential requests, at least one second apart. Values are literal; encode them as needed.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(a, { a = it }, label = { Text("P1 values — one per line") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(b, { b = it }, label = { Text("P2 values — optional") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    PayloadMode.entries.forEach { option -> FilterChip(selected = mode == option, onClick = { mode = option; preview = "" }, label = { Text(option.name.replace('_', ' ')) }) }
    OutlinedButton(onClick = { preview = runCatching { val plan = model.previewPayloads(a, b, mode); "${plan.size} requests\n" + plan.take(5).joinToString("\n") { "${it.method} ${it.url}" } }.getOrElse { it.message ?: "Invalid plan" } }, enabled = !state.busy) { Text("Validate and preview count") }
    if (preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodySmall)
    Button(onClick = { model.runPayloads(a, b, mode) }, enabled = !state.busy && state.projectId != null) { Text("Start explicit parameter task") }
    Text("Saved-request sequence", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(sequence, { sequence = it }, label = { Text("Exact saved-request names, in execution order") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(tokenPath, { tokenPath = it }, label = { Text("First-response token JSON Pointer (optional)") }, placeholder = { Text("/access_token") }, modifier = Modifier.fillMaxWidth())
    Text("Use {{token}} in later steps. URL substitutions are encoded; headers/body use literal values. Up to 10 steps; stops on transport errors or non-2xx responses. Review non-idempotent operations before starting.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { model.runMacro(sequence, tokenPath) }, enabled = !state.busy && state.projectId != null) { Text("Run saved-request sequence") }
    OutlinedButton(onClick = model::cancelRequest, enabled = state.busy) { Text("Cancel running task / request") }
}

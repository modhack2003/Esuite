package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.automation.PayloadMode
import dev.esuite.app.core.DraftKey
import dev.esuite.app.core.Editor

private val payloadEditors = listOf(Editor.TaskPayload1, Editor.TaskPayload2, Editor.TaskPayload3, Editor.TaskPayload4)

@Composable
fun WorkflowsScreen(state: AppState, model: AppViewModel) {
    val pending by model.drafts.values.collectAsState()
    fun draftText(editor: Editor) = pending[DraftKey(state.projectId, editor)].orEmpty()
    fun write(editor: Editor, value: String) = model.drafts.set(DraftKey(state.projectId, editor), value)
    val inputs = payloadEditors.map(::draftText)
    val sequence = draftText(Editor.SequenceNames); val tokenPath = draftText(Editor.TokenPath)
    // Mode and preview are view state derived from the inputs, so they stay screen-local.
    var mode by remember { mutableStateOf(PayloadMode.ONE_POSITION) }
    var preview by remember { mutableStateOf("") }
    LaunchedEffect(state.draft) { preview = "" }
    Text("Bounded tasks", style = MaterialTheme.typography.titleLarge)
    Text(state.taskProgress)
    Text("Use the current API draft. Place {{P1}} through {{P4}} in its URL, header values or body. Each run sends up to 50 sequential requests, at least one second apart. Values are literal; encode them as needed.", style = MaterialTheme.typography.bodySmall)
    payloadEditors.forEachIndexed { index, editor ->
        OutlinedTextField(inputs[index], { write(editor, it); preview = "" },
            label = { Text("P${index + 1} values — one per line") }, minLines = 2,
            enabled = !state.busy, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(),
            modifier = Modifier.fillMaxWidth().testTag("task_payload_${index + 1}"))
    }
    Text("Only marked positions need lists. Broadcast uses P1 values for every marker. Paired mode stops at the shortest marked list; other modes hold unchanged positions at their first value.", style = MaterialTheme.typography.bodySmall)
    PayloadMode.entries.forEach { option -> FilterChip(selected = mode == option, onClick = { mode = option; preview = "" }, label = { Text(option.name.replace('_', ' ')) }) }
    OutlinedButton(modifier = Modifier.testTag("task_preview"), onClick = { preview = runCatching { val plan = model.previewPayloads(inputs, mode); "${plan.size} requests\n" + plan.take(5).joinToString("\n") { "${it.method} ${it.url}" } }.getOrElse { it.message ?: "Invalid plan" } }, enabled = !state.busy) { Text("Validate and preview count") }
    if (preview.isNotBlank()) Text(preview, modifier = Modifier.testTag("task_preview_result"), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { model.runPayloads(inputs, mode) }, enabled = !state.busy && state.projectId != null) { Text("Start explicit parameter task") }
    Text("Saved-request sequence", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(sequence, { write(Editor.SequenceNames, it) }, label = { Text("Exact saved-request names, in execution order") }, minLines = 3, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(tokenPath, { write(Editor.TokenPath, it) }, label = { Text("First-response token JSON Pointer (optional)") }, placeholder = { Text("/access_token") }, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth())
    Text("Use {{token}} in later steps. URL substitutions are encoded; headers/body use literal values. Up to 10 steps; stops on transport errors or non-2xx responses. Review non-idempotent operations before starting.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { model.runMacro(sequence, tokenPath) }, enabled = !state.busy && state.projectId != null) { Text("Run saved-request sequence") }
    OutlinedButton(onClick = model::cancelRequest, enabled = state.busy) { Text("Cancel running task / request") }
}

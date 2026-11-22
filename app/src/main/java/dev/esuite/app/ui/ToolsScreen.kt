package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ToolGroup(val label: String) {
    Encode("Encode"), Import("Import"), Review("Review")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolsScreen(state: AppState, model: AppViewModel) {
    val pending by model.drafts.values.collectAsStateWithLifecycle()
    val inputKey = DraftKey.of(state.projectId, Editor.ToolsInput, state.identity)
    val input = pending[inputKey].orEmpty()
    val scope = rememberCoroutineScope()
    var worker by remember { mutableStateOf<Job?>(null) }
    var groupName by rememberSaveable { mutableStateOf(ToolGroup.Encode.name) }
    val group = ToolGroup.valueOf(groupName)
    var output by remember(inputKey) { mutableStateOf("") }
    var working by remember(inputKey) { mutableStateOf(false) }

    fun resetOutput() {
        worker?.cancel()
        working = false
        output = ""
    }
    fun transform(action: (String) -> String) {
        resetOutput()
        working = true
        worker = scope.launch {
            output = withContext(Dispatchers.Default) { runCatching { action(input) }.getOrElse { it.message ?: "Check your input" } }
            working = false
        }
    }

    Text("Tools", style = MaterialTheme.typography.headlineSmall)
    WorkspaceTabs(ToolGroup.entries, group, "tools", { it.label }) {
        resetOutput()
        groupName = it.name
    }
    OutlinedTextField(input, { model.drafts.set(inputKey, it); resetOutput() }, label = { Text("Input") },
        minLines = 4, maxLines = 12, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(),
        modifier = Modifier.fillMaxWidth().testTag("tools_input"))
    when (group) {
        ToolGroup.Encode -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Base64 encode", input) { transform(Codec::base64Encode) }
                ToolButton("Base64 decode", input) { transform(Codec::base64Decode) }
                ToolButton("URL encode", input) { transform(Codec::formEncode) }
                ToolButton("URL decode", input) { transform(Codec::formDecode) }
                ToolButton("SHA-256", input) { transform(Codec::sha256) }
            }
        }
        ToolGroup.Import -> {
            Text("Import into the active project for manual review.", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (state.projectId == null) Text("Create a project to import requests.", color = Amber)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val importable = input.takeIf { state.projectId != null }.orEmpty()
                ToolButton("Import cURL", importable) { model.importCurl(input) }
                ToolButton("Import OpenAPI 3 JSON", importable) { model.importOpenApi(input) }
                ToolButton("Import HAR", importable) { model.importHar(input) }
            }
        }
        ToolGroup.Review -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Decode JWT claims", input) { transform(TokenTools::jwtClaims) }
                ToolButton("Token sample statistics", input) { transform { TokenTools.sample(it.lines().filter(String::isNotBlank)) } }
                ToolButton("Review AWS JSON offline", input) { transform(CloudConfigReview::review) }
            }
        }
    }
    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (output.isNotEmpty()) {
        OutlinedTextField(output, {}, readOnly = true, label = { Text("Result") }, minLines = 2, maxLines = 12,
            textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("tools_output"))
    }
    DraftNotice()
}

@Composable
private fun ToolButton(label: String, input: String, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = input.isNotEmpty()) { Text(label) }
}

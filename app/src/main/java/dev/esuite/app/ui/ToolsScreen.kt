package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    Encode("Encode"), Import("Import"), Review("Review"), Checks("Checks")
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
    var checkResults by remember(inputKey) { mutableStateOf(emptyList<CheckItem>()) }
    var template by rememberSaveable { mutableStateOf(ResponseCheck.Headers) }
    var working by remember(inputKey) { mutableStateOf(false) }

    fun resetOutput() {
        worker?.cancel()
        working = false
        output = ""
        checkResults = emptyList()
    }
    fun <T> runTool(action: () -> T, done: (T) -> Unit) {
        resetOutput()
        working = true
        worker = scope.launch {
            withContext(Dispatchers.Default) { runCatching(action) }
                .onSuccess(done).onFailure { output = it.message ?: "Check your input" }
            working = false
        }
    }
    fun transform(action: (String) -> String) = runTool({ action(input) }) { output = it }

    LaunchedEffect(inputKey) { resetOutput() }

    val contentScroll = key(group) { rememberScrollState() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            WorkspaceTabs(ToolGroup.entries, group, "tools", { it.label }) {
                resetOutput()
                groupName = it.name
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().weight(1f).testTag("tools_content").verticalScroll(contentScroll)
            .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (group == ToolGroup.Checks) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResponseCheck.entries.forEach { item ->
                        FilterChip(template == item, { resetOutput(); template = item }, { Text(item.label) })
                    }
                }
                Text(template.summary, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            OutlinedTextField(input, { model.drafts.set(inputKey, it); resetOutput() }, label = { Text(if (group == ToolGroup.Checks) "Response headers" else "Input") },
                minLines = 1, maxLines = 6, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(),
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
                    Text("Import into the selected site for manual review.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (state.projectId == null) Text("Open a site in API Lab to import requests.", color = Amber)
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
                ToolGroup.Checks -> {
                    val latest = state.workspace.exchanges.firstOrNull { it.projectId == state.projectId && it.status != null }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = input.isNotBlank() && !working, modifier = Modifier.testTag("run_response_check"), onClick = {
                            val chosen = template
                            runTool({ ResponseChecks.run(chosen, input) }) { checkResults = it }
                        }) { Text("Check pasted headers") }
                        if (latest != null) OutlinedButton(enabled = !working, onClick = {
                            val chosen = template
                            runTool({ ResponseChecks.run(chosen, latest.responseHeaders) }) { checkResults = it }
                        }) { Text("Check latest response") }
                    }
                    checkResults.forEach { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text(item.detail, Modifier.testTag("response_check_detail")) },
                            trailingContent = { Text(item.status.label, style = MaterialTheme.typography.labelMedium,
                                color = if (item.status == CheckStatus.Review) Amber else Muted) },
                        )
                        HorizontalDivider()
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
    }
}

@Composable
private fun ToolButton(label: String, input: String, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = input.isNotEmpty()) { Text(label) }
}

package dev.esuite.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private enum class ToolGroup(val label: String) {
    Encode("Encode"), Import("Import"), Review("Review"), Checks("Checks")
}

private enum class ImportDocument(val label: String) { OpenApi("OpenAPI"), Har("HAR") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolsScreen(state: AppState, model: AppViewModel, onReview: (Exchange, ResponseCheck) -> Unit) {
    val pending by model.drafts.values.collectAsStateWithLifecycle()
    val inputKey = DraftKey.of(state.projectId, Editor.ToolsInput, state.identity)
    val input = pending[inputKey].orEmpty()
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var worker by remember { mutableStateOf<Job?>(null) }
    var groupName by rememberSaveable { mutableStateOf(ToolGroup.Encode.name) }
    val group = ToolGroup.valueOf(groupName)
    var output by remember(inputKey) { mutableStateOf("") }
    var inputError by remember(inputKey) { mutableStateOf<String?>(null) }
    var documentRequest by remember { mutableStateOf<Pair<ImportDocument, RequestContext>?>(null) }
    var checkResults by remember(inputKey) { mutableStateOf(emptyList<CheckItem>()) }
    var template by rememberSaveable { mutableStateOf(ResponseCheck.Headers) }
    var working by remember(inputKey) { mutableStateOf(false) }

    fun resetOutput() {
        worker?.cancel()
        working = false
        output = ""
        checkResults = emptyList()
    }
    fun <T> runTool(action: suspend () -> T, done: (T) -> Unit) {
        resetOutput()
        working = true
        worker = scope.launch {
            try {
                done(withContext(Dispatchers.Default) { action() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                output = failure.message ?: "Check your input"
            }
            working = false
        }
    }
    fun transform(action: (String) -> String) = runTool({ action(input) }) { output = it }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val (format, started) = documentRequest ?: return@rememberLauncherForActivityResult
        documentRequest = null
        if (uri != null && group == ToolGroup.Import && ResultAttribution.accepts(started, model.currentContext())) {
            runTool({
                withContext(Dispatchers.IO) {
                    try {
                        val stream = resolver.openInputStream(uri) ?: throw IOException()
                        stream.use { readCollectionDocument(it) }
                    } catch (_: IOException) {
                        throw IllegalArgumentException("Could not read the selected document. Choose it again.")
                    } catch (_: SecurityException) {
                        throw IllegalArgumentException("Android revoked access to the document. Choose it again.")
                    }
                }
            }) { document ->
                if (ResultAttribution.accepts(started, model.currentContext())) {
                    when (format) {
                        ImportDocument.OpenApi -> model.importOpenApi(document)
                        ImportDocument.Har -> model.importHar(document)
                    }
                }
            }
        }
    }

    LaunchedEffect(inputKey, state.identity) { resetOutput() }

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
                ResponseCheckOptions(template) { resetOutput(); template = it }
                Text(template.summary, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("Pasted headers are reviewed locally without attaching an exchange.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(input, {
                resetOutput()
                inputError = if (model.drafts.trySet(inputKey, it)) null else
                    "Paste exceeds the 262,144-character editor limit. Use a file for larger OpenAPI or HAR documents."
            }, label = { Text(if (group == ToolGroup.Checks) "Response headers" else "Input") },
                isError = inputError != null,
                supportingText = inputError?.let { message -> { Text(message) } },
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
                        val importable = input.takeIf { state.projectId != null && inputError == null && !working }.orEmpty()
                        ToolButton("Import cURL", importable) { model.importCurl(input) }
                        ToolButton("Import OpenAPI 3 JSON", importable) { model.importOpenApi(input) }
                        ToolButton("Import HAR", importable) { model.importHar(input) }
                    }
                    Text("OpenAPI and HAR files can be up to 2 MiB.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImportDocument.entries.forEach { format ->
                            OutlinedButton(
                                enabled = state.projectId != null && documentRequest == null && !working,
                                onClick = {
                                    documentRequest = format to model.currentContext()
                                    openDocument.launch(arrayOf("*/*"))
                                },
                            ) { Text("Open ${format.label} file") }
                        }
                        if (working) TextButton(onClick = ::resetOutput) { Text("Cancel") }
                    }
                }
                ToolGroup.Review -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Decode JWT claims", input) { transform(TokenTools::jwtClaims) }
                        ToolButton("Token sample statistics", input) { transform { TokenTools.sample(it.lines().filter(String::isNotBlank)) } }
                        ToolButton("Review AWS JSON offline", input, Modifier.testTag("tools_review_aws")) { transform(CloudConfigReview::review) }
                    }
                }
                ToolGroup.Checks -> {
                    val latest = latestReviewableResponse(state.workspace, state.projectId, state.identity)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = input.isNotBlank() && !working, modifier = Modifier.testTag("run_response_check"), onClick = {
                            val chosen = template
                            runTool({ ResponseChecks.run(chosen, input) }) { checkResults = it }
                        }) { Text("Check pasted headers") }
                        if (latest != null) {
                            Text(exchangeLabel(latest), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(enabled = !working, onClick = { onReview(latest, template) }) { Text("Check latest response") }
                        }
                    }
                    ResponseCheckResults(checkResults)
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
internal fun ResponseCheckOptions(selected: ResponseCheck, choose: (ResponseCheck) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ResponseCheck.entries.forEach { check ->
            FilterChip(selected == check, { choose(check) }, { Text(check.label) })
        }
    }
}

@Composable
internal fun ResponseCheckResults(results: List<CheckItem>) {
    results.forEach { item ->
        ListItem(
            headlineContent = { Text(item.name) },
            supportingContent = { Text(item.detail, Modifier.testTag("response_check_detail")) },
            trailingContent = { Text(item.status.label, style = MaterialTheme.typography.labelMedium,
                color = if (item.status == CheckStatus.Review) Amber else Muted) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun ToolButton(label: String, input: String, modifier: Modifier = Modifier, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = input.isNotEmpty(), modifier = modifier) { Text(label) }
}

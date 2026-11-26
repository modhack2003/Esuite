package dev.esuite.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
private const val OUTPUT_CHARS = 65_536

private data class ImportPreview(val format: ImportFormat, val context: RequestContext, val drafts: List<SavedRequest>)
private val fileFormats = listOf(ImportFormat.OpenApi, ImportFormat.Har)

private class LocalImportDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

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
    val group = ToolGroup.entries.firstOrNull { it.name == groupName } ?: ToolGroup.Encode
    var output by remember(inputKey) { mutableStateOf("") }
    var outputClipped by remember(inputKey) { mutableStateOf(false) }
    var inputError by remember(inputKey) { mutableStateOf<String?>(null) }
    var toolError by remember(inputKey) { mutableStateOf<String?>(null) }
    var previousInput by remember(inputKey, state.identity) { mutableStateOf<String?>(null) }
    var byteConversionOpen by remember(inputKey, state.identity) { mutableStateOf(false) }
    var documentRequest by remember { mutableStateOf<Pair<ImportFormat, RequestContext>?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var importStatus by remember(inputKey) { mutableStateOf<String?>(null) }
    var checkResults by remember(inputKey) { mutableStateOf(emptyList<CheckItem>()) }
    var template by rememberSaveable { mutableStateOf(ResponseCheck.Headers) }
    var working by remember(inputKey) { mutableStateOf(false) }
    var advancedEncode by rememberSaveable { mutableStateOf(false) }

    fun resetOutput() {
        worker?.cancel()
        working = false
        output = ""
        outputClipped = false
        toolError = null
        checkResults = emptyList()
        preview = null
        importStatus = null
    }
    fun <T> runTool(action: suspend () -> T, failed: (String) -> Unit = { toolError = it }, done: (T) -> Unit) {
        resetOutput()
        working = true
        worker = scope.launch {
            try {
                done(withContext(Dispatchers.Default) { action() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failed(failure.message?.atCharacterBoundary(OUTPUT_CHARS) ?: "Check your input")
            }
            working = false
        }
    }
    fun transform(action: (String) -> String) = runTool({ action(input) }) { result ->
        outputClipped = result.length > OUTPUT_CHARS
        output = result.atCharacterBoundary(OUTPUT_CHARS)
    }
    fun stageImport(format: ImportFormat, read: suspend () -> String) {
        val project = model.currentProject() ?: return
        val started = model.currentContext()
        runTool({ model.prepareImport(format, read(), project) }) { drafts ->
            if (ResultAttribution.accepts(started, model.currentContext())) {
                preview = ImportPreview(format, started, drafts)
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(LocalImportDocument()) { uri ->
        val (format, started) = documentRequest ?: return@rememberLauncherForActivityResult
        documentRequest = null
        if (uri != null && group == ToolGroup.Import && ResultAttribution.accepts(started, model.currentContext())) {
            stageImport(format) {
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
            }
        }
    }

    LaunchedEffect(inputKey, state.identity) { resetOutput() }

    val contentScroll = key(group) { rememberScrollState() }
    val transformable = input.takeIf { inputError == null && !working }.orEmpty()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            WorkspaceTabs(ToolGroup.entries, group, "tools", { it.label }) {
                resetOutput()
                groupName = it.name
            }
        }
        HorizontalDivider()
        if (group == ToolGroup.Import) {
            importStatus?.let { status ->
                Text(status, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("tools_import_status").semantics { liveRegion = LiveRegionMode.Polite },
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).testTag("tools_content").verticalScroll(contentScroll)
            .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (group == ToolGroup.Checks) {
                ResponseCheckOptions(template) { resetOutput(); template = it }
                Text(template.summary, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("Pasted headers are reviewed locally without attaching an exchange.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(input, {
                resetOutput()
                if (model.drafts.trySet(inputKey, it)) {
                    previousInput = null
                    inputError = null
                } else {
                    inputError = "Input exceeds the 262,144-character editor limit. Use a file for larger OpenAPI or HAR documents."
                }
            }, label = { Text(if (group == ToolGroup.Checks) "Response headers" else "Input") },
                isError = inputError != null,
                supportingText = inputError?.let { message -> { Text(message) } },
                minLines = 1, maxLines = 6, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(),
                modifier = Modifier.fillMaxWidth().testTag("tools_input"))
            when (group) {
                ToolGroup.Encode -> {
                    previousInput?.let { oldInput ->
                        TextButton(onClick = {
                            model.drafts.set(inputKey, oldInput)
                            previousInput = null
                            inputError = null
                            resetOutput()
                        }, modifier = Modifier.testTag("tools_undo_input")) { Text("Undo input replacement") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Base64 encode", transformable) { transform(Codec::base64Encode) }
                        ToolButton("Base64 decode", transformable) { transform(Codec::base64Decode) }
                        ToolButton("URL encode", transformable) { transform(Codec::formEncode) }
                        ToolButton("URL decode", transformable) { transform(Codec::formDecode) }
                        ToolButton("SHA-256", transformable) { transform(Codec::sha256) }
                    }
                    TextButton(onClick = { advancedEncode = !advancedEncode }, modifier = Modifier.testTag("tools_advanced_encode")) {
                        Text(if (advancedEncode) "Hide more transformations" else "More transformations")
                    }
                    if (advancedEncode) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Base64URL encode", transformable) { transform(Codec::base64UrlEncode) }
                        ToolButton("Base64URL decode", transformable) { transform(Codec::base64UrlDecode) }
                        ToolButton("Hex encode", transformable) { transform(Codec::hexEncode) }
                        ToolButton("Hex decode", transformable) { transform(Codec::hexDecode) }
                        ToolButton("Convert bytes", transformable.takeUnless(String::isBlank).orEmpty(),
                            Modifier.testTag("tools_convert_bytes")) { byteConversionOpen = true }
                        ToolButton("URL component encode", transformable) { transform(Codec::componentEncode) }
                        ToolButton("URL component decode", transformable) { transform(Codec::componentDecode) }
                        ToolButton("SHA-512", transformable) { transform(Codec::sha512) }
                        ToolButton("Format JSON", transformable) { transform { Codec.formatJson(it, compact = false) } }
                        ToolButton("Minify JSON", transformable) { transform { Codec.formatJson(it, compact = true) } }
                    }
                }
                ToolGroup.Import -> {
                    Text("Import into the selected site for manual review.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (state.projectId == null) Text("Open a site in API Lab to import requests.", color = Amber)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val importable = input.takeIf { state.projectId != null && inputError == null && !working }.orEmpty()
                        ToolButton("Import cURL", importable, Modifier.testTag("tools_prepare_curl")) { stageImport(ImportFormat.Curl) { input } }
                        ToolButton("Import OpenAPI 3 JSON", importable) { stageImport(ImportFormat.OpenApi) { input } }
                        ToolButton("Import HAR", importable) { stageImport(ImportFormat.Har) { input } }
                    }
                    Text("OpenAPI and HAR files can be up to 2 MiB.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fileFormats.forEach { format ->
                            OutlinedButton(
                                modifier = Modifier.testTag("tools_open_${format.name.lowercase()}_file"),
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
                        ToolButton("Decode JWT claims", transformable) { transform(TokenTools::jwtClaims) }
                        ToolButton("Token sample statistics", transformable) { transform { TokenTools.sample(it.lines().filter(String::isNotBlank)) } }
                        ToolButton("Review AWS JSON offline", transformable, Modifier.testTag("tools_review_aws")) { transform(CloudConfigReview::review) }
                    }
                }
                ToolGroup.Checks -> {
                    val latest = latestReviewableResponse(state.workspace, state.projectId, state.identity)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = transformable.isNotBlank(), modifier = Modifier.testTag("run_response_check"), onClick = {
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
            toolError?.let { message ->
                Text(message, Modifier.testTag("tools_error").semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (output.isNotEmpty()) {
                OutlinedTextField(output, {}, readOnly = true, label = { Text(if (outputClipped) "Result preview" else "Result") },
                    supportingText = if (outputClipped) ({ Text("Showing the first 65,536 characters. Use a smaller input to view the full result.") }) else null,
                    minLines = 2, maxLines = 12,
                    textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("tools_output"))
                if (group == ToolGroup.Encode && !outputClipped) {
                    OutlinedButton(onClick = {
                        previousInput = input
                        model.drafts.set(inputKey, output)
                        resetOutput()
                        scope.launch { contentScroll.scrollTo(0) }
                    }, modifier = Modifier.testTag("tools_use_result")) { Text("Use result as input") }
                }
            }
            DraftNotice()
        }
    }
    if (byteConversionOpen) {
        ByteConversionDialog(close = { byteConversionOpen = false }) { from, to ->
            byteConversionOpen = false
            transform { Codec.convertBytes(it, from, to) }
        }
    }
    preview?.let { selected ->
        ImportReviewDialog(selected, close = { preview = null }) { requests ->
            runTool(
                { withContext(Dispatchers.Main.immediate) { model.applyImport(selected.format, selected.context, requests) } },
                failed = { importStatus = it },
            ) { importStatus = it }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ByteConversionDialog(close: () -> Unit, convert: (ByteEncoding, ByteEncoding) -> Unit) {
    var from by remember { mutableStateOf(ByteEncoding.Base64) }
    var to by remember { mutableStateOf(ByteEncoding.Hex) }
    AlertDialog(onDismissRequest = close, title = { Text("Convert bytes") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Input encoding", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ByteEncoding.entries.forEach { encoding ->
                    FilterChip(from == encoding, { from = encoding }, { Text(encoding.label) })
                }
            }
            Text("Output encoding", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ByteEncoding.entries.forEach { encoding ->
                    FilterChip(to == encoding, { to = encoding }, { Text(encoding.label) })
                }
            }
        }
    }, confirmButton = {
        Button(onClick = { convert(from, to) }, enabled = from != to) { Text("Convert") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun ImportReviewDialog(preview: ImportPreview, close: () -> Unit, apply: (List<SavedRequest>) -> Unit) {
    val single = preview.format == ImportFormat.Curl
    val allIds = remember(preview) { preview.drafts.map(SavedRequest::id).toSet() }
    var selected by remember(preview) { mutableStateOf(allIds) }
    WorkflowDialog("Review ${preview.format.label} import", close, "tools_import_preview") {
        Text(if (single) "Loading this draft replaces the current API Lab request. Review it before sending."
            else "${selected.size} of ${preview.drafts.size} in-scope drafts selected.")
        if (!single && allIds.isNotEmpty()) {
            TextButton(onClick = { selected = if (selected == allIds) emptySet() else allIds }) {
                Text(if (selected == allIds) "Deselect all" else "Select all")
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (preview.drafts.isEmpty()) item { Text("This document has no supported requests for the selected site.") }
            items(preview.drafts, key = SavedRequest::id) { request ->
                val checked = request.id in selected
                val toggle = if (single) Modifier else Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = {
                    selected = if (it) selected + request.id else selected - request.id
                })
                Row(Modifier.fillMaxWidth().then(toggle).heightIn(min = 48.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!single) Checkbox(checked, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text("${request.draft.method} ${request.draft.url.substringBefore('?')}", maxLines = 2,
                            overflow = TextOverflow.Ellipsis, style = technicalTextStyle())
                        Text("${request.draft.headers.size} header fields · ${request.draft.body.toByteArray().size} body bytes",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                HorizontalDivider()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = close) { Text("Cancel") }
            Button(enabled = selected.isNotEmpty(), modifier = Modifier.testTag("tools_apply_import"), onClick = {
                apply(preview.drafts.filter { it.id in selected })
            }) { Text(if (single) "Load draft" else "Save selected") }
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

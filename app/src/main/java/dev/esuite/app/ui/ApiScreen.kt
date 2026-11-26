package dev.esuite.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.IdentityControls
import dev.esuite.app.Panel
import dev.esuite.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ApiSection(val label: String) {
    Request("Request"), Response("Response"), Collection("Saved"), Identity("Session")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApiScreen(state: AppState, model: AppViewModel, returnToTop: () -> Unit,
    browserSession: Boolean, disconnectSession: () -> Unit, openSession: (String) -> Boolean, returnToBrowser: () -> Unit,
    onReview: (Exchange) -> Unit) {
    val project = model.currentProject()
    if (project == null) {
        Text("Open an HTTPS site to use API Lab.", style = MaterialTheme.typography.titleMedium)
        return
    }
    val jar = model.engine.sessions.jar(project.id, state.identity)
    val hasBearer by jar.hasBearer.collectAsStateWithLifecycle()
    val draft = state.draft
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val pending by model.drafts.values.collectAsStateWithLifecycle()
    var sectionName by rememberSaveable(project.id) { mutableStateOf(ApiSection.Request.name) }
    val section = ApiSection.entries.firstOrNull { it.name == sectionName } ?: ApiSection.Request
    var capacityCheck by remember { mutableStateOf(false) }
    fun show(next: ApiSection) {
        focus.clearFocus()
        keyboard?.hide()
        sectionName = next.name
        returnToTop()
    }
    fun key(editor: Editor) = DraftKey.of(project.id, editor, state.identity)
    fun write(editor: Editor, value: String) = model.drafts.set(key(editor), value)
    val headers = pending[key(Editor.ApiHeaders)] ?: remember(draft.headers) { draft.headers.asEditorText() }

    fun prepare(action: () -> Unit) {
        focus.clearFocus()
        keyboard?.hide()
        runCatching { DraftParser.headers(headers) }
            .onSuccess { model.draft(draft.copy(headers = it)); action() }
            .onFailure { model.message(it.message ?: "Check the request headers") }
    }

    Text("API Lab", style = MaterialTheme.typography.headlineSmall)
    WorkspaceTabs(ApiSection.entries, section, "api", { it.label }, ::show)
    when (section) {
        ApiSection.Request -> {
            IdentityControls(state, model)
            OutlinedTextField(draft.method, { model.draft(draft.copy(method = it.uppercase())) },
                singleLine = true, label = { Text("Method") }, keyboardOptions = TechnicalKeyboard,
                textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_method"))
            OutlinedTextField(draft.url, { model.draft(draft.copy(url = it)) }, singleLine = true, label = { Text("HTTPS URL") },
                keyboardOptions = UrlKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_url"))
            if (browserSession) {
                var expired by remember { mutableStateOf(false) }
                Text("Browser profile · WebView observation", style = MaterialTheme.typography.labelLarge)
                val validGet = draft.method.equals("GET", true) && draft.body.isEmpty() && headers.isBlank() &&
                    ScopePolicy(project.origin).allows(draft.url)
                if (expired) {
                    Text("This Browser session is no longer available.")
                    TextButton(onClick = returnToBrowser) { Text("Return to Browser") }
                } else {
                    if (!validGet) Text("Browser navigation requires a same-site GET with empty headers and body.")
                    Button(onClick = { expired = !openSession(draft.url) }, enabled = validGet,
                        modifier = Modifier.testTag("api_session_open")) { Text("Open with Browser session") }
                }
                TextButton(onClick = disconnectSession) { Text("Disconnect Browser session") }
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasBearer) Text("Bearer token · identity ${state.identity} · ${project.origin}")
                Button(onClick = { prepare { model.send(); show(ApiSection.Response) } },
                    enabled = !state.busy && draft.url.isNotBlank(), modifier = Modifier.testTag("api_send")) { Text("Send request") }
                OutlinedButton(onClick = { prepare { model.send(compare = true); show(ApiSection.Response) } },
                    enabled = !state.busy && draft.url.isNotBlank(), modifier = Modifier.testTag("api_compare")) { Text("Compare A and B") }
            }
            OutlinedTextField(headers, { write(Editor.ApiHeaders, it) }, label = { Text("Headers") },
                placeholder = { Text("Name: value") }, minLines = 2, maxLines = 6, keyboardOptions = TechnicalKeyboard,
                textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_headers"))
            OutlinedTextField(draft.body, { model.draft(draft.copy(body = it)) }, label = { Text("Request body") },
                minLines = 3, maxLines = 10, keyboardOptions = TechnicalKeyboard,
                textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("api_body"))
            DraftNotice()
        }
        ApiSection.Response -> {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Waiting for response", style = MaterialTheme.typography.titleMedium)
            } else if (state.selected == null) {
                Text("Responses appear here after you send a request.", color = Muted)
            }
            if (state.comparison.isNotBlank()) Panel("Identity comparison", state.comparison)
            state.selected?.let { exchange ->
                Text(ResultAttribution.label(exchange, project.origin), style = MaterialTheme.typography.bodySmall, color = Muted)
                val responseTitle = "${exchange.status ?: "Error"} · ${exchange.durationMs} ms · ${exchange.response.encoding}"
                val headerText = remember(exchange.responseHeaders) { exchange.responseHeaders.asEditorText() }
                if (exchange.hasReviewableResponse()) {
                    OutlinedButton(onClick = { onReview(exchange) }, modifier = Modifier.testTag("api_review_response")) {
                        Text("Check response headers")
                    }
                }
                Panel(responseTitle, exchange.error ?: headerText.ifBlank { "No response headers" })
                if (exchange.response.value.isNotEmpty() || exchange.response.truncated) {
                    key(exchange.id) { ResponseBodyPanel(exchange.response, exchange.responseHeaders, previewBytes = 48 * 1024) }
                }
            }
            TextButton(onClick = { show(ApiSection.Request) }) { Text("Edit request") }
        }
        ApiSection.Collection -> {
            var rawOpen by rememberSaveable(project.id) { mutableStateOf(false) }
            OutlinedButton(onClick = { rawOpen = true }, modifier = Modifier.testTag("api_raw_toggle")) {
                Text("Import raw HTTP")
            }
            if (rawOpen) {
                val rawKey = key(Editor.ApiRaw)
                val raw = pending[rawKey].orEmpty()
                var inputError by rememberSaveable { mutableStateOf<String?>(null) }
                var importing by remember { mutableStateOf(false) }
                WorkflowDialog("Import raw HTTP", { if (!importing) rawOpen = false }, "api_raw_dialog", fitKeyboard = true) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(raw, {
                            inputError = if (model.drafts.trySet(rawKey, it)) null else
                                "The paste exceeds the 262,144-character limit."
                        }, label = { Text("Raw HTTP/1.x request") }, minLines = 3, maxLines = 8,
                            enabled = !importing, isError = inputError != null,
                            keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(),
                            modifier = Modifier.fillMaxWidth().testTag("api_raw_input"))
                        inputError?.let { message ->
                            Text(message, modifier = Modifier.testTag("api_raw_error").semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { rawOpen = false }, enabled = !importing) { Text("Cancel") }
                        Button(onClick = {
                            val context = model.currentContext()
                            importing = true
                            scope.launch {
                                try {
                                    val parsed = withContext(Dispatchers.Default) { runCatching { DraftParser.raw(raw, project.origin) } }
                                    if (!ResultAttribution.accepts(context, model.currentContext()) || model.drafts.text(rawKey) != raw) {
                                        inputError = "The site or request draft changed. Review the current input again."
                                    } else parsed.onSuccess {
                                        model.replaceDraft(it)
                                        rawOpen = false
                                        show(ApiSection.Request)
                                    }.onFailure { inputError = it.message ?: "Check the imported request." }
                                } finally { importing = false }
                            }
                        }, enabled = raw.isNotBlank() && inputError == null && !importing, modifier = Modifier.testTag("api_raw_import")) {
                            Text(if (importing) "Reading draft…" else "Load draft")
                        }
                    }
                }
            }
            val name = pending[key(Editor.ApiSavedName)].orEmpty()
            OutlinedTextField(name, { write(Editor.ApiSavedName, it) }, label = { Text("Saved request name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("api_saved_name"))
            Button(onClick = {
                prepare {
                    capacityCheck = true
                    scope.launch {
                        try { model.saveDraft(name) } finally { capacityCheck = false }
                    }
                }
            }, enabled = !capacityCheck && name.isNotBlank(), modifier = Modifier.testTag("api_save_draft")) { Text("Save current request") }
            val saved = remember(state.workspace.savedRequests, project.id) { state.workspace.savedRequests.filter { it.projectId == project.id } }
            if (saved.isEmpty()) Text("Saved requests for this site appear here.", color = Muted)
            saved.forEach { request ->
                key(request.id) {
                    var remove by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { model.replaceDraft(request.draft); show(ApiSection.Request) }, modifier = Modifier.weight(1f)) {
                            Text("Load: ${request.name}")
                        }
                        TextButton(onClick = { remove = true }) { Text("Remove") }
                    }
                    if (remove) AlertDialog(
                        onDismissRequest = { remove = false }, title = { Text("Remove saved request?") },
                        text = { Text(request.name) },
                        confirmButton = { TextButton(onClick = { remove = false; model.deleteSavedRequest(request.id) }) { Text("Remove") } },
                        dismissButton = { TextButton(onClick = { remove = false }) { Text("Keep") } },
                    )
                }
            }
        }
        ApiSection.Identity -> {
            IdentityControls(state, model)
            Text("Each identity has a separate session. Review the URL and body before comparing identities.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            key(project.id, state.identity) {
                BearerEditor(project.origin, state.identity, jar, hasBearer)
            }
            val fallback = remember(state.workspace.identityConfigs, project.id, state.identity) {
                state.workspace.identityConfigs.firstOrNull { it.projectId == project.id && it.identity == state.identity }
                    ?.headers.orEmpty().asEditorText()
            }
            val identityHeaders = pending[key(Editor.ApiIdentityHeaders)] ?: fallback
            OutlinedTextField(identityHeaders, { write(Editor.ApiIdentityHeaders, it) },
                label = { Text("Identity ${state.identity} headers") }, minLines = 3, maxLines = 8,
                keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(),
                modifier = Modifier.fillMaxWidth().testTag("api_identity_headers"))
            Button(onClick = {
                focus.clearFocus()
                keyboard?.hide()
                capacityCheck = true
                scope.launch { try { model.configureIdentity(identityHeaders) } finally { capacityCheck = false } }
            }, enabled = !capacityCheck, modifier = Modifier.testTag("api_save_identity")) { Text("Save encrypted headers") }
            OutlinedButton(modifier = Modifier.testTag("api_session_metadata"), onClick = {
                model.message(model.engine.sessions.jar(project.id, state.identity).describe().ifBlank { "No session cookies for this identity" })
                returnToTop()
            }) { Text("View session metadata") }
            var clear by remember { mutableStateOf(false) }
            TextButton(onClick = { clear = true }) { Text("Clear API sessions") }
            if (clear) AlertDialog(
                onDismissRequest = { clear = false }, title = { Text("Clear API sessions?") },
                text = { Text("Remove API cookies and saved identity headers for both identities on this site.") },
                confirmButton = { TextButton(onClick = { clear = false; model.clearSessions() }) { Text("Clear sessions") } },
                dismissButton = { TextButton(onClick = { clear = false }) { Text("Keep sessions") } },
            )
        }
    }
}

private fun List<Header>.asEditorText() = joinToString("\n") { "${it.name}: ${it.value}" }

@Composable
private fun BearerEditor(origin: String, identity: String, jar: dev.esuite.app.transport.IdentityCookieJar, active: Boolean) {
    var revision by remember { mutableIntStateOf(0) }
    val field = remember(revision) { androidx.compose.foundation.text.input.TextFieldState() }
    var problem by remember { mutableStateOf<String?>(null) }
    Text("Bearer token", style = MaterialTheme.typography.titleMedium)
    Text("Manual requests · identity $identity · $origin", style = MaterialTheme.typography.bodySmall)
    if (active) {
        Text("Token in memory")
        TextButton(onClick = { jar.setBearer(null) }, modifier = Modifier.testTag("api_forget_bearer")) { Text("Forget token") }
    } else {
        BasicSecureTextField(state = field, inputTransformation = InputTransformation.maxLength(8185),
            textStyle = technicalTextStyle().copy(color = MaterialTheme.colorScheme.onSurface),
            textObfuscationMode = androidx.compose.foundation.text.input.TextObfuscationMode.Hidden,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("api_bearer_input")
                .semantics { contentDescription = "API bearer token" }
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(12.dp))
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = {
            runCatching { jar.setBearer(field.text.toString()) }.onSuccess { revision++; problem = null }
                .onFailure { problem = it.message }
        }, enabled = field.text.isNotEmpty(), modifier = Modifier.testTag("api_set_bearer")) { Text("Use token") }
    }
}

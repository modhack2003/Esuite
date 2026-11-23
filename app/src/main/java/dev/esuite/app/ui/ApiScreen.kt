package dev.esuite.app.ui

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.IdentityControls
import dev.esuite.app.Panel
import dev.esuite.app.core.*
import kotlinx.coroutines.launch

private enum class ApiSection(val label: String) {
    Request("Request"), Response("Response"), Collection("Saved"), Identity("Session")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApiScreen(state: AppState, model: AppViewModel, returnToTop: () -> Unit, onReview: (Exchange) -> Unit) {
    val project = model.currentProject()
    if (project == null) {
        Text("Open an HTTPS site to use API Lab.", style = MaterialTheme.typography.titleMedium)
        return
    }
    val draft = state.draft
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val pending by model.drafts.values.collectAsStateWithLifecycle()
    var sectionName by rememberSaveable(project.id) { mutableStateOf(ApiSection.Request.name) }
    val section = ApiSection.valueOf(sectionName)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                val preview = remember(exchange.id) { exchange.response.value.take(48_000) }
                var fullBody by remember(exchange.id) { mutableStateOf(false) }
                if (exchange.hasReviewableResponse()) {
                    OutlinedButton(onClick = { onReview(exchange) }, modifier = Modifier.testTag("api_review_response")) {
                        Text("Check response headers")
                    }
                }
                Panel(responseTitle, exchange.error ?: headerText.ifBlank { "No response headers" })
                if (exchange.response.value.isNotEmpty()) {
                    Panel("Response body", if (fullBody) exchange.response.value else preview)
                    if (preview.length < exchange.response.value.length && !fullBody) {
                        TextButton(onClick = { fullBody = true }) { Text("Show full captured body") }
                    }
                }
                if (exchange.response.truncated) Text("Capture limit reached. Response body is partial.", color = Amber)
            }
            TextButton(onClick = { show(ApiSection.Request) }) { Text("Edit request") }
        }
        ApiSection.Collection -> {
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
            val raw = pending[key(Editor.ApiRaw)].orEmpty()
            OutlinedTextField(raw, { write(Editor.ApiRaw, it) }, label = { Text("Raw HTTP/1.x request") }, minLines = 3, maxLines = 8,
                keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                runCatching { DraftParser.raw(raw, project.origin) }.onSuccess { model.replaceDraft(it); show(ApiSection.Request) }
                    .onFailure { model.message(it.message ?: "Check the imported request") }
            }, enabled = raw.isNotBlank()) { Text("Import to editor") }
        }
        ApiSection.Identity -> {
            IdentityControls(state, model)
            Text("Each identity has a separate session. Review the URL and body before comparing identities.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
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

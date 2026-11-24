package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.Panel
import dev.esuite.app.WorkspaceStatus
import dev.esuite.app.core.*
import kotlinx.coroutines.launch

@Composable
internal fun FindingsScreen(state: AppState, model: AppViewModel, project: Project?, openApi: () -> Unit, openReview: (Exchange) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pending by model.drafts.values.collectAsStateWithLifecycle()
    val titleKey = DraftKey.of(project?.id, Editor.FindingTitle, state.identity)
    val detailKey = DraftKey.of(project?.id, Editor.FindingDetail, state.identity)
    val title = pending[titleKey].orEmpty()
    val detail = pending[detailKey].orEmpty()
    var filter by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var composeNote by remember { mutableStateOf(true) }
    var evidenceIds by remember(state.identity) { mutableStateOf(emptyList<String>()) }
    var pickingEvidence by remember { mutableStateOf(false) }
    val evidenceOptions = remember(state.workspace.exchanges, project) {
        state.workspace.exchanges.filter { it.projectId == project?.id }
    }
    val optionsById = remember(evidenceOptions) { evidenceOptions.associateBy(Exchange::id) }
    val attachments = remember(optionsById, evidenceIds) { evidenceIds.mapNotNull(optionsById::get) }
    val findings = remember(state.workspace.findings, project?.id) {
        state.workspace.findings.filter { it.projectId == project?.id }
    }
    val visible = remember(findings, filter) {
        val query = filter.trim()
        if (query.isEmpty()) findings else findings.filter { finding ->
            finding.title.contains(query, ignoreCase = true) || finding.detail.contains(query, ignoreCase = true)
        }
    }

    fun saveObservation(noteTitle: String, noteDetail: String, evidence: List<Exchange>) {
        focus.clearFocus()
        keyboard?.hide()
        saving = true
        scope.launch {
            try {
                if (model.addFinding(noteTitle, noteDetail, evidence) &&
                    model.drafts.text(titleKey) == noteTitle && model.drafts.text(detailKey) == noteDetail
                ) {
                    model.drafts.clear(titleKey)
                    model.drafts.clear(detailKey)
                    evidenceIds = emptyList()
                    composeNote = false
                }
            } finally { saving = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WorkspaceStatus(project, state) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Findings", style = MaterialTheme.typography.headlineSmall)
                if (project != null) TextButton(onClick = { composeNote = !composeNote }, modifier = Modifier.testTag("finding_editor_toggle")) {
                    Text(if (composeNote) "Hide editor" else "New observation")
                }
            }
        }
        item { SitePicker(state, project, model) }
        if (project == null) {
            item { Text("Open a site in API Lab to save observations.", color = Muted) }
        } else {
            if (composeNote) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { model.drafts.set(titleKey, it) }, label = { Text("Observation title") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("finding_title"))
                    OutlinedTextField(detail, { model.drafts.set(detailKey, it) }, label = { Text("Notes") },
                        minLines = 2, maxLines = 8, modifier = Modifier.fillMaxWidth().testTag("finding_detail"))
                    Text(when {
                        attachments.size != evidenceIds.size -> "Some selected evidence is no longer in Activity. Update the selection."
                        attachments.size == 1 -> "Evidence · ${exchangeLabel(attachments.single())}"
                        attachments.isNotEmpty() -> "${attachments.size} exchanges selected"
                        else -> "No evidence selected"
                    })
                    Row {
                        TextButton(onClick = { pickingEvidence = true }) { Text("Choose evidence") }
                        if (evidenceIds.isNotEmpty()) TextButton(onClick = { evidenceIds = emptyList() }) { Text("Clear selection") }
                    }
                    Button(onClick = { saveObservation(title, detail, attachments) },
                        enabled = !saving && title.isNotBlank() && detail.isNotBlank() && attachments.size == evidenceIds.size,
                        modifier = Modifier.testTag("save_finding")) { Text("Save observation") }
                    DraftNotice()
                }
            }
            if (findings.isNotEmpty()) item {
                OutlinedTextField(filter, { filter = it }, label = { Text("Search observations") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("finding_search"))
            }
            if (visible.isEmpty()) item {
                Text(if (filter.isBlank()) "Saved observations appear here." else "No observations match your search.", color = Muted)
            }
            items(visible, key = { it.id }) { finding ->
                Card(onClick = { focus.clearFocus(); keyboard?.hide(); selectedId = finding.id },
                    modifier = Modifier.fillMaxWidth().testTag("finding_item"),
                    colors = CardDefaults.cardColors(containerColor = Raised)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(finding.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = Muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (finding.evidenceIds.isNotEmpty()) {
                            val missing = EvidenceRetention.missing(finding).size
                            val label = when (missing) {
                                0 -> "Evidence attached"
                                finding.evidenceIds.size -> "Evidence not retained"
                                else -> "${finding.evidenceIds.size - missing} of ${finding.evidenceIds.size} attachments retained"
                            }
                            Text(label, color = if (missing == 0) Signal else Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            state.selected?.takeIf { it in evidenceOptions && it.hasReviewableResponse() }?.let { exchange ->
                item {
                    OutlinedButton(onClick = { openReview(exchange) }) { Text("Review selected response") }
                    Text(exchangeLabel(exchange), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (pickingEvidence && project != null) {
        FindingEvidencePicker(project, evidenceOptions, evidenceIds, close = { pickingEvidence = false }) {
            evidenceIds = it
            pickingEvidence = false
        }
    }

    findings.firstOrNull { it.id == selectedId }?.let { finding ->
        var remove by remember(finding.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text("Observation") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Panel(finding.title, finding.detail)
                    FindingEvidence(finding, evidenceOptions,
                        replay = { evidenceId -> if (model.replayFindingEvidence(finding.id, evidenceId)) openApi() },
                        remove = { evidenceId -> model.removeEvidence(finding.id, evidenceId) },
                        openReview = { exchange -> selectedId = null; openReview(exchange) },
                    )
                    LocalReportAction("Preview observation report", "esuite-observation.json", { model.exportObservation(finding.id) }, model::message)
                    TextButton(onClick = { remove = true }) { Text("Remove observation") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedId = null }) { Text("Close") } },
        )
        if (remove) AlertDialog(
            onDismissRequest = { remove = false }, title = { Text("Remove this observation?") },
            text = { Text("This removes the encrypted note and its retained evidence.") },
            confirmButton = { TextButton(onClick = { remove = false; selectedId = null; model.deleteFinding(finding.id) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { remove = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun FindingEvidencePicker(
    project: Project,
    exchanges: List<Exchange>,
    initial: List<String>,
    close: () -> Unit,
    apply: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    val available = remember(exchanges) { exchanges.map(Exchange::id).toSet() }
    val chosen = selected.filter { it in available }
    WorkflowDialog("Choose finding evidence", close, "finding_evidence_picker") {
        Text(project.origin, style = MaterialTheme.typography.bodySmall)
        Text("${chosen.size} exchanges selected")
        LazyColumn(Modifier.weight(1f)) {
            if (exchanges.isEmpty()) item { Text("Saved Activity exchanges appear here.") }
            items(exchanges, key = Exchange::id) { exchange ->
                val checked = exchange.id in selected
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox) {
                    selected = if (it) selected + exchange.id else selected - exchange.id
                }.testTag("finding_evidence_choice").padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(exchangeLabel(exchange), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(observedAt(exchange.timestamp), style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = close) { Text("Cancel") }
            Button(onClick = { apply(chosen) }, modifier = Modifier.testTag("finding_apply_evidence")) { Text("Apply selection") }
        }
    }
}

/** One row per referenced evidence id: a viewer for a retained snapshot, an explicit state for a reference with none. */
@Composable
private fun FindingEvidence(
    finding: Finding,
    history: List<Exchange>,
    replay: (String) -> Unit,
    remove: (String) -> Unit,
    openReview: (Exchange) -> Unit,
) {
    if (finding.evidenceIds.isEmpty()) {
        Text("No exchange was attached to this observation.", style = MaterialTheme.typography.bodySmall, color = Muted)
        return
    }
    val missing = EvidenceRetention.missing(finding).toSet()
    finding.evidenceIds.forEachIndexed { index, evidenceId ->
        key(evidenceId) {
            val snapshot = finding.evidence.firstOrNull { it.id == evidenceId }
            if (snapshot == null || evidenceId in missing) {
                Text("Evidence not retained — history was cleared or this observation predates evidence retention.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            } else {
                var shown by remember { mutableStateOf(false) }
                var confirmEvidence by remember { mutableStateOf(false) }
                OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("finding_evidence_$index"), onClick = { shown = !shown }) {
                    Text("${if (shown) "Hide" else "Show"} retained evidence · ${snapshot.request.method} ${snapshot.request.url}")
                }
                if (shown) {
                    val bodyState = remember(finding, snapshot) { EvidenceRetention.requestBodyState(finding, snapshot) }
                    val canReplay = remember(finding, snapshot, history) { EvidenceRetention.requestForReplay(finding, snapshot, history) != null }
                    val bodyLabel = when (bodyState) {
                        EvidenceRequestBody.Complete -> "Retained request body · complete"
                        EvidenceRequestBody.Clipped -> "Retained request body · clipped"
                        EvidenceRequestBody.Unknown -> "Retained request preview · completeness unknown"
                        EvidenceRequestBody.Unavailable -> "Request body unavailable"
                    }
                    Panel("Retained evidence · ${snapshot.status ?: if (snapshot.error != null) "error" else "metadata only"} · ${snapshot.durationMs} ms",
                        "identity ${snapshot.identity} · ${snapshot.source}\n${snapshot.completeness}\n" +
                            "Immutable copy saved with the observation; bodies are capped at ${Limits.EVIDENCE_BYTES} bytes.\n${snapshot.error.orEmpty()}\n\n" +
                            snapshot.request.headers.joinToString("\n") { "${it.name}: ${it.value}" } + "\n\n$bodyLabel\n${snapshot.request.body}\n\n" +
                            snapshot.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" } +
                            "\n\n${snapshot.response.encoding}${if (snapshot.response.truncated) " · TRUNCATED" else ""}\n${snapshot.response.value}")
                    if (snapshot.hasReviewableResponse()) OutlinedButton(onClick = { openReview(snapshot) }) { Text("Review retained response") }
                    if (bodyState != EvidenceRequestBody.Complete) {
                        Text(if (canReplay) "API Lab will load the complete request from Activity." else "The complete request is unavailable. Replay is disabled.",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Button(onClick = { replay(evidenceId) }, enabled = canReplay) { Text("To API Lab (review before sending)") }
                    TextButton(onClick = { confirmEvidence = true }) { Text("Remove retained evidence") }
                }
                if (confirmEvidence) AlertDialog(onDismissRequest = { confirmEvidence = false },
                    title = { Text("Remove this retained evidence?") },
                    text = { Text("The saved request/response copy is deleted. The observation keeps its reference and will report evidence as not retained.") },
                    confirmButton = { TextButton(onClick = { confirmEvidence = false; remove(evidenceId) }) { Text("Remove evidence") } },
                    dismissButton = { TextButton(onClick = { confirmEvidence = false }) { Text("Keep evidence") } })
            }
        }
    }
}


package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.esuite.app.AppViewModel
import dev.esuite.app.R
import dev.esuite.app.browser.BrowserLogSnapshot
import dev.esuite.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal enum class AssessmentCommand(val shortcut: String, val label: String) {
    Map("/map", "Open Site Map"), Review("/review", "Review a response"), Finding("/finding", "Create a finding"),
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun WorkflowDialog(title: String, close: () -> Unit, tag: String, fitKeyboard: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val frame = if (fitKeyboard) Modifier.widthIn(max = 520.dp).heightIn(max = 560.dp) else Modifier.widthIn(max = 720.dp)
    val surface: @Composable () -> Unit = {
        Surface(frame.fillMaxWidth(.96f).fillMaxHeight(.92f)
            .semantics { testTagsAsResourceId = true }.testTag(tag), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
            }
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = !fitKeyboard)) {
        if (fitKeyboard) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) { surface() }
        } else surface()
    }
}

@Composable
internal fun CommandPalette(close: () -> Unit, choose: (AssessmentCommand) -> Unit) {
    SearchCommands(
        items = AssessmentCommand.entries,
        id = { it.name.lowercase() },
        label = { "${it.shortcut}  ·  ${it.label}" },
        search = { "${it.shortcut} ${it.label}" },
        dialogTag = "activity_command_palette",
        close = close,
        choose = choose,
    )
}

internal data class CommandControls<T>(val pinned: (T) -> Boolean, val recent: (T) -> Boolean, val togglePin: (T) -> Unit)

@Composable
internal fun <T> SearchCommands(
    items: List<T>,
    id: (T) -> String,
    label: (T) -> String,
    search: (T) -> String,
    dialogTag: String,
    close: () -> Unit,
    controls: CommandControls<T>? = null,
    choose: (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matching = matchingCommands(items, query, search)
    val hasGroups = query.isBlank() && controls != null && matching.any { controls.pinned(it) || controls.recent(it) }
    val groups: Map<String?, List<T>> = if (hasGroups && controls != null) matching.groupBy {
        when {
            controls.pinned(it) -> "Pinned"
            controls.recent(it) -> "Recent"
            else -> "All commands"
        }
    } else mapOf(null to matching)
    WorkflowDialog("Commands", close, dialogTag, fitKeyboard = true) {
        LazyColumn(Modifier.weight(1f).testTag("command_list")) {
            item {
                SearchField(query, { query = it }, "Find a command", "command_search") {
                    matching.singleOrNull()?.let(choose)
                }
            }
            if (matching.isEmpty()) item { Text("No commands match.") }
            groups.forEach { (group, choices) ->
                if (group != null) item(key = "group:$group") {
                    Text(group, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall)
                }
                items(choices, key = id) { command ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { choose(command) }, modifier = Modifier.weight(1f).testTag("command_${id(command)}")) {
                            Text(label(command), Modifier.fillMaxWidth())
                        }
                        if (controls != null) {
                            val pinned = controls.pinned(command)
                            IconToggleButton(pinned, { controls.togglePin(command) }, modifier = Modifier.testTag("pin_command_${id(command)}")) {
                                Icon(painterResource(R.drawable.ic_bookmark_outline),
                                    contentDescription = "${if (pinned) "Unpin" else "Pin"} ${label(command)}",
                                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = close, modifier = Modifier.align(Alignment.End).testTag("close_command_palette")) { Text("Close") }
    }
}

@Composable
internal fun BrowserActivityImport(log: BrowserLogSnapshot, sites: List<Project>, message: String, close: () -> Unit, accept: (String?) -> Unit) {
    WorkflowDialog("Open in Activity", close, "browser_activity_import") {
        Text(log.origin, style = MaterialTheme.typography.bodyLarge)
        Text("${log.rows.size} Browser observations · available until you change sites or close ESuite.")
        LazyColumn(Modifier.weight(1f)) {
            items(sites.filter { it.origin == log.origin }, key = Project::id) { site ->
                TextButton(onClick = { accept(site.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use ${site.name}", Modifier.fillMaxWidth())
                }
            }
            item {
                Button(onClick = { accept(null) }, modifier = Modifier.testTag("browser_activity_create")) { Text("Create saved site") }
            }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = close, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
    }
}

@Composable
internal fun ExchangePicker(title: String, site: Project, exchanges: List<Exchange>, close: () -> Unit, choose: (Exchange) -> Unit,
    withoutEvidence: (() -> Unit)? = null,
) {
    WorkflowDialog(title, close, "exchange_picker") {
        Text(site.origin)
        LazyColumn(Modifier.weight(1f)) {
            if (exchanges.isEmpty()) item { Text("No eligible exchanges in this site. Captured HTTP responses appear here after a manual request.") }
            items(exchanges, key = Exchange::id) { exchange ->
                TextButton(onClick = { choose(exchange) }, modifier = Modifier.fillMaxWidth().testTag("pick_exchange")) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(exchangeLabel(exchange), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${observedAt(exchange.timestamp)} · ${exchange.source}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = close) { Text("Cancel") }
            if (withoutEvidence != null) TextButton(onClick = withoutEvidence) { Text("Continue without evidence") }
        }
    }
}

internal fun exchangeLabel(exchange: Exchange): String {
    val url = exchange.request.url.substringBefore('?').substringBefore('#')
    return "${exchange.request.method} $url · Identity ${exchange.identity}"
}

internal data class ReviewLaunch(val site: Project, val context: RequestContext, val exchanges: List<Exchange>, val template: ResponseCheck)
internal data class FindingSeed(val site: Project, val context: RequestContext, val exchange: Exchange, val item: CheckItem? = null)
private data class ReviewedResponse(val exchange: Exchange, val items: List<CheckItem>)

@Composable
internal fun ResponseReviewDialog(launch: ReviewLaunch, model: AppViewModel, close: () -> Unit) {
    var template by remember(launch) { mutableStateOf(launch.template) }
    var results by remember(launch, template) { mutableStateOf(emptyList<ReviewedResponse>()) }
    var finding by remember(launch) { mutableStateOf<FindingSeed?>(null) }
    LaunchedEffect(launch, template) {
        val check = template
        for (exchange in launch.exchanges) {
            val outcome = withContext(Dispatchers.Default) { ResponseChecks.run(check, exchange) }
            results = results + ReviewedResponse(exchange, outcome)
        }
    }
    val ready = results.size == launch.exchanges.size
    val seed = finding
    if (seed != null) {
        SaveFindingDialog(seed, model) { finding = null }
        return
    }
    WorkflowDialog("Response checks", close, "api_response_checks") {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("review_results"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(launch.site.origin)
                ResponseCheckOptions(template) { template = it }
                if (!ready) {
                    LinearProgressIndicator(progress = { results.size.toFloat() / launch.exchanges.size }, modifier = Modifier.fillMaxWidth())
                    Text("Reviewed ${results.size} of ${launch.exchanges.size} responses")
                } else {
                    val all = results.flatMap(ReviewedResponse::items)
                    val available = all.count { it.status != CheckStatus.Unavailable }
                    Text("${results.size} responses · $available checks with evidence · ${all.count { it.status == CheckStatus.Review }} to review · ${all.size - available} unavailable",
                        modifier = Modifier.testTag("review_summary"), style = MaterialTheme.typography.bodySmall)
                }
            }
            results.forEach { result ->
                item(key = result.exchange.id) {
                    Text(exchangeLabel(result.exchange), style = MaterialTheme.typography.titleSmall)
                    Text("${observedAt(result.exchange.timestamp)} · ${result.exchange.source}", style = MaterialTheme.typography.bodySmall)
                }
                result.items.forEachIndexed { index, check ->
                    item(key = "${result.exchange.id}:rule:$index") {
                        ResponseCheckResults(listOf(check))
                    }
                    if (check.status != CheckStatus.Unavailable && result.exchange.hasReviewableResponse()) {
                        item(key = "${result.exchange.id}:action:$index") {
                            TextButton(onClick = { finding = FindingSeed(launch.site, launch.context, result.exchange, check) },
                                modifier = Modifier.testTag("review_save_finding")) { Text("Save as finding") }
                        }
                    }
                }
            }
        }
        TextButton(onClick = close, modifier = Modifier.align(Alignment.End).testTag("review_close")) {
            Text(if (ready) "Done" else "Cancel")
        }
    }
}

@Composable
internal fun SaveFindingDialog(seed: FindingSeed, model: AppViewModel, close: () -> Unit) {
    var title by remember(seed) { mutableStateOf(seed.item?.name.orEmpty()) }
    var notes by remember(seed) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var admitted by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val id = remember(seed) { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    val dismiss = { if (!saving) close() }
    WorkflowDialog("Save as finding", dismiss, "review_finding_confirmation") {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(seed.site.origin, style = MaterialTheme.typography.titleSmall)
                Text(exchangeLabel(seed.exchange))
                Text("${observedAt(seed.exchange.timestamp)} · ${seed.exchange.source}")
                seed.item?.let { check ->
                    Text("Rule ${check.ruleId} · version $RESPONSE_RULE_VERSION")
                    Text(check.detail)
                }
            }
            item {
                OutlinedTextField(title, { title = it.take(200) }, enabled = !saving && !admitted, label = { Text("Finding title") },
                    modifier = Modifier.fillMaxWidth().testTag("review_finding_title"))
                OutlinedTextField(notes, { notes = it.take(15000) }, enabled = !saving && !admitted, label = { Text(if (seed.item == null) "Notes" else "Additional notes (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("review_finding_notes"))
            }
        }
        if (message.isNotBlank()) Text(message, modifier = Modifier.testTag("review_finding_state"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss, enabled = !saving, modifier = Modifier.testTag("review_finding_close")) { Text(if (admitted) "Close" else "Cancel") }
            if (!committed) Button(enabled = !saving && title.isNotBlank() && (seed.item != null || notes.isNotBlank()),
                modifier = Modifier.testTag("review_finding_confirm"), onClick = {
                    saving = true
                    val check = seed.item
                    val detail = if (check == null) notes
                        else "Rule ${check.ruleId} · version $RESPONSE_RULE_VERSION\n${check.detail}\n\n$notes"
                    scope.launch {
                        try {
                            if (!admitted) admitted = model.addFinding(title, detail, seed.exchange, seed.context, id)
                            committed = admitted && model.saveWorkspace() &&
                                model.state.value.persistedWorkspace?.findings?.any { it.id == id } == true
                            message = when {
                                !admitted -> model.state.value.message
                                committed -> "Saved on this device."
                                else -> "Finding is in memory. Retry the encrypted save."
                            }
                        } finally { saving = false }
                    }
                }) { Text(if (admitted) "Retry save" else "Save finding") }
        }
    }
}

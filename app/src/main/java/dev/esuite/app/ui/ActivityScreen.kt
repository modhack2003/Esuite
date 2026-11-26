package dev.esuite.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.WorkspaceStatus
import dev.esuite.app.core.*

internal enum class ActivityView(val label: String) { History("History"), Map("Site Map") }
private enum class ActivityStatus(val label: String) {
    All("All statuses"), Success("2xx"), Redirect("3xx"), Error("Errors"), Unknown("Unavailable");

    fun matches(row: ActivityRecord): Boolean = when (this) {
        All -> true
        Success -> row.status in 200..299
        Redirect -> row.status in 300..399
        Error -> row.failed
        Unknown -> row.status == null && !row.failed
    }
}
private enum class ExchangeSection(val label: String) { Overview("Overview"), Request("Request"), Response("Response") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityScreen(
    state: AppState, model: AppViewModel, project: Project?, openApi: () -> Unit,
    openReview: (List<Exchange>, ResponseCheck) -> Unit, openFinding: (Exchange?) -> Unit,
) {
    val imported by model.browserActivity.collectAsStateWithLifecycle()
    val rows = remember(state.workspace.exchanges, project, imported) {
        if (project == null) emptyList() else activityRecords(project, state.workspace, imported)
    }
    val responses = remember(rows) { rows.mapNotNull(ActivityRecord::exchange).filter(Exchange::hasReviewableResponse) }
    var view by rememberSaveable { mutableStateOf(ActivityView.History) }
    var search by remember { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf<String?>(null) }
    var source by rememberSaveable { mutableStateOf<ObservationSource?>(null) }
    var status by rememberSaveable { mutableStateOf(ActivityStatus.All) }
    var endpoint by remember { mutableStateOf<Endpoint?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var commands by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<AssessmentCommand?>(null) }
    var clearHistory by remember { mutableStateOf(false) }
    val detail = rows.firstOrNull { it.id == detailId }
    val selectedResponses = remember(responses, selection) { responses.filter { it.id in selection } }
    val visible = remember(rows, search, method, source, status, endpoint) {
        rows.filter { row ->
            (endpoint == null || (!row.clipped && Endpoint(row.origin, row.path, row.method) == endpoint)) &&
                (method == null || row.method.orEmpty() == method) && (source == null || source == row.source) &&
                status.matches(row) && "${row.method.orEmpty()} ${row.path} ${row.sourceLabel} ${row.status ?: ""}".contains(search.trim(), true)
        }
    }
    val groups = remember(visible) { siteMap(visible) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    fun show(next: ActivityView) { view = next; endpoint = null; detailId = null }

    BackHandler(detail != null || endpoint != null) { if (detail != null) detailId = null else show(ActivityView.Map) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = maxWidth >= 760.dp
        if (detail != null && !split) {
            ActivityDetail(detail, Modifier.fillMaxSize(), { detailId = null }, {
                detail.exchange?.let { model.replay(it); openApi() }
            }, { detail.exchange?.let { openReview(listOf(it), ResponseCheck.Quick) } }, { commands = true })
        } else Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Activity", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { commands = true }, modifier = Modifier.testTag("activity_commands")) { Text("Commands") }
                }
                WorkspaceTabs(ActivityView.entries, view, "activity", { it.label }, ::show)
                if (selectedResponses.isNotEmpty()) {
                    Button(onClick = { openReview(selectedResponses, ResponseCheck.Quick) },
                        modifier = Modifier.testTag("activity_review_selected")) { Text("Review selected (${selectedResponses.size})") }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).testTag("activity_history"),
                    contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        WorkspaceStatus(project, state)
                        SitePicker(state, project, model)
                    }
                    if (project != null) item {
                        OutlinedTextField(search, { search = it.take(240) }, label = { Text("Filter Activity") },
                            singleLine = true, keyboardOptions = TechnicalKeyboard, modifier = Modifier.fillMaxWidth().testTag("activity_search"))
                        TextButton(onClick = { filters = !filters }) { Text(if (filters) "Hide filters" else "Filters") }
                        if (filters) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ActivityMenu(method?.ifBlank { "Unknown method" } ?: "All methods", listOf(null) + rows.map { it.method.orEmpty() }.distinct().sorted(),
                                { it?.ifBlank { "Unknown method" } ?: "All methods" }) { method = it }
                            ActivityMenu(source?.label ?: "All sources", listOf(null) + ObservationSource.entries,
                                { it?.label ?: "All sources" }) { source = it }
                            ActivityMenu(status.label, ActivityStatus.entries, ActivityStatus::label) { status = it }
                            TextButton(onClick = { method = null; source = null; status = ActivityStatus.All; search = "" }) { Text("Clear filters") }
                        }
                    }
                    if (endpoint != null) item { TextButton(onClick = { show(ActivityView.Map) }) { Text("Back to Site Map") } }
                    if (view == ActivityView.Map) {
                        item {
                            Text("${groups.size} observed endpoints · ${groups.values.sumOf { it.size }} observations",
                                modifier = Modifier.testTag("activity_map_summary"))
                            if (visible.any(ActivityRecord::clipped)) Text("Clipped paths are available in History.")
                        }
                        if (groups.isEmpty()) item { Text(if (rows.isEmpty()) "No observed endpoints yet." else "No complete paths match these filters.") }
                        items(groups.entries.toList(), key = { (key, _) -> listOf(key.origin, key.path, key.method).joinToString("\u0000") }) { (key, records) ->
                            OutlinedCard(onClick = { endpoint = key; view = ActivityView.History },
                                modifier = Modifier.fillMaxWidth().testTag("activity_endpoint")) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${key.method ?: "Unknown method"} ${key.path}", style = technicalTextStyle())
                                    Text(if (records.size == 1) "1 observation" else "${records.size} observations")
                                }
                            }
                        }
                    } else {
                        if (rows.isNotEmpty()) item {
                            Text("${visible.size} of ${rows.size} observations")
                            if (visible.any { it.source != ObservationSource.Exchange }) {
                                Text("A WebView callback and its resource timing can share a path. Each row is a separate observation.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (visible.isEmpty()) item {
                            Text(when {
                                project == null -> "Choose a saved site in API Lab or open Browser Network observations in Activity."
                                rows.isEmpty() -> "No history for this site. Send a manual request or open a Browser Network snapshot in Activity."
                                else -> "No observations match these filters."
                            })
                        }
                        items(visible, key = ActivityRecord::id) { row ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().clickable {
                                    detailId = row.id
                                    row.exchange?.let(model::select)
                                }.testTag("traffic_exchange").padding(10.dp)) {
                                    Text("${row.method ?: "Unknown method"} ${row.pathLabel}", style = technicalTextStyle(),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(row.sourceLabel, row.exchange?.let { "Identity ${it.identity}" },
                                        row.status?.let { "HTTP $it" } ?: if (row.failed) "Error" else "Metadata only").joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                val response = row.exchange?.takeIf(Exchange::hasReviewableResponse)
                                if (response != null) {
                                    val isSelected = response.id in selection
                                    FilterChip(selected = isSelected, enabled = isSelected || selectedResponses.size < MAX_REVIEW_EXCHANGES,
                                        onClick = { selection = if (isSelected) selection - response.id else
                                            selectedResponses.map(Exchange::id).toSet() + response.id },
                                        label = { Text(if (isSelected) "Selected for review" else "Select for review") },
                                        modifier = Modifier.padding(horizontal = 10.dp).testTag("activity_select_review"))
                                }
                            }
                        }
                    }
                    if (rows.isNotEmpty()) item {
                        OutlinedButton(onClick = { clearHistory = true }) { Text("Clear site history") }
                    }
                }
            }
            if (split && detail != null) {
                VerticalDivider()
                ActivityDetail(detail, Modifier.weight(1f).fillMaxHeight(), { detailId = null }, {
                    detail.exchange?.let { model.replay(it); openApi() }
                }, { detail.exchange?.let { openReview(listOf(it), ResponseCheck.Quick) } }, { commands = true })
            }
        }
    }
    if (commands) CommandPalette(close = { commands = false }) { command ->
        commands = false
        when (command) {
            AssessmentCommand.Map -> show(ActivityView.Map)
            AssessmentCommand.Review -> {
                val exchange = detail?.exchange?.takeIf(Exchange::hasReviewableResponse)
                if (exchange == null) picker = command else openReview(listOf(exchange), ResponseCheck.Quick)
            }
            AssessmentCommand.Finding -> picker = command
        }
    }
    picker?.let { command ->
        val target = project
        if (target == null) AlertDialog(onDismissRequest = { picker = null }, title = { Text("Choose a site first") },
            text = { Text("Open a saved site in API Lab or open a Browser Network snapshot in Activity.") },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Close") } })
        else ExchangePicker(
            if (command == AssessmentCommand.Review) "Choose a response" else "Choose finding evidence",
            target, if (command == AssessmentCommand.Review) responses else rows.mapNotNull(ActivityRecord::exchange),
            close = { picker = null }, choose = { exchange ->
                picker = null
                if (command == AssessmentCommand.Review) openReview(listOf(exchange), ResponseCheck.Quick) else openFinding(exchange)
            }, withoutEvidence = if (command == AssessmentCommand.Finding) ({ picker = null; openFinding(null) }) else null,
        )
    }
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text("Clear site history?") },
        text = { Text("This removes exchanges and the imported Browser snapshot from Activity. Evidence saved with findings stays on this device.") },
        confirmButton = { TextButton(onClick = {
            clearHistory = false; selection = emptySet(); detailId = null; endpoint = null; model.clearHistory()
        }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("Cancel") } })
}

@Composable
private fun <T> ActivityMenu(label: String, options: List<T>, display: (T) -> String, select: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { expanded = false; select(option) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityDetail(row: ActivityRecord, modifier: Modifier, close: () -> Unit, openApi: () -> Unit, review: () -> Unit, commands: () -> Unit) {
    var section by rememberSaveable(row.id) { mutableStateOf(ExchangeSection.Overview) }
    val exchange = row.exchange
    Column(modifier.padding(12.dp).testTag("traffic_detail"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Exchange detail", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = commands, modifier = Modifier.testTag("activity_detail_commands")) {
                Icon(Icons.Outlined.Search, contentDescription = "Commands")
            }
            TextButton(onClick = close) { Text("Back") }
        }
        WorkspaceTabs(ExchangeSection.entries, section, "activity_detail", { it.label }) { section = it }
        val pageScroll = key(section) { rememberScrollState() }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(pageScroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.origin, style = MaterialTheme.typography.titleSmall)
            when (section) {
                ExchangeSection.Overview -> {
                    Text("${row.method ?: "Unknown method"} ${row.pathLabel}", style = technicalTextStyle())
                    Text("${row.sourceLabel} · ${observedAt(row.timestamp)}", Modifier.testTag("activity_display_timestamp"))
                    Text("Identity ${exchange?.identity ?: "unavailable"}")
                    Text(row.status?.let { "HTTP $it" } ?: "HTTP status unavailable")
                    Text(exchange?.completeness ?: "Browser metadata. Request and response messages are unavailable.")
                    exchange?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    row.browser?.let { browser ->
                        Text(browser.kind.label)
                        browser.code?.takeIf { browser.failed && browser.status == null }?.let { Text("WebView error $it") }
                        browser.timing?.let { timing ->
                            Text("Start ${timing.start ?: "unavailable"} ms · Duration ${timing.duration ?: "unavailable"} ms")
                            timing.transfer?.let { Text("Transferred $it bytes") }
                        }
                    }
                }
                ExchangeSection.Request -> if (exchange == null) Text("Request headers and body are unavailable in this Browser observation.")
                    else {
                        SelectionContainer { Text("${exchange.request.method} ${exchange.request.url}", style = technicalTextStyle()) }
                        ActivityHeaders(exchange.request.headers)
                        ActivityBody(exchange.request.body, false, "Request body")
                    }
                ExchangeSection.Response -> if (exchange == null || !exchange.hasReviewableResponse()) {
                    Text("No captured HTTP response is available.")
                } else {
                    ActivityHeaders(exchange.responseHeaders)
                    ResponseBodyPanel(exchange.response, exchange.responseHeaders, previewBytes = ACTIVITY_PREVIEW_BYTES)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (exchange != null && exchange.hasReviewableResponse()) {
                Button(onClick = review, modifier = Modifier.testTag("activity_review_response")) { Text("Review response") }
            }
            if (exchange != null && exchange.source != "Browser metadata") {
                OutlinedButton(onClick = openApi) { Text("Review in API Lab") }
            }
        }
    }
}

@Composable
private fun ActivityHeaders(headers: List<Header>) {
    val text = remember(headers) { headers.joinToString("\n") { "${it.name}: ${it.value}" } }
    ActivityBody(text, false, "Headers")
}

@Composable
private fun ActivityBody(text: String, captureTruncated: Boolean, label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    val preview = remember(text) { utf8Preview(text, ACTIVITY_PREVIEW_BYTES) }
    if (captureTruncated) Text("The captured body is partial.", color = Amber)
    if (preview.length < text.length) Text("Showing an 8 KiB preview.", color = Amber)
    if (preview.isEmpty()) Text("Empty")
    else SelectionContainer { Text(preview, style = technicalTextStyle()) }
}

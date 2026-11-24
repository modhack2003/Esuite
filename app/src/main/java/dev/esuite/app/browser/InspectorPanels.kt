package dev.esuite.app.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.esuite.app.ui.TechnicalKeyboard
import dev.esuite.app.core.atCharacterBoundary
import dev.esuite.app.ui.observedAt
import dev.esuite.app.ui.technicalTextStyle
import java.util.Locale

private enum class DetailsTab(val label: String) { General("General"), Timing("Timing") }

@Composable
internal fun NetworkPanel(state: BrowserNetworkState, filters: NetworkFilters, update: (NetworkFilters) -> Unit,
    clearRequests: () -> Unit,
    onRecord: (Boolean) -> Unit, onPreserve: (Boolean) -> Unit, onOpenActivity: () -> Unit,
) {
    val origin = state.origin
    var selected by remember(origin) { mutableStateOf<String?>(null) }
    val methods = remember(state.rows, filters.method) {
        (state.rows.mapNotNull(BrowserRequestEvent::method) + listOfNotNull(filters.method?.takeIf(String::isNotEmpty))).distinct().sorted()
    }
    val rows = remember(state.rows, filters) { filters.select(state.rows) }
    val timedRows = remember(rows) { rows.filter { it.timing?.duration != null } }
    val start = remember(timedRows) { timedRows.minOfOrNull(BrowserRequestEvent::timestamp) ?: 0.0 }
    val span = remember(timedRows, start) {
        (timedRows.maxOfOrNull { it.timestamp + (it.timing?.duration ?: 0.0) }?.minus(start) ?: 0.0).coerceAtLeast(1.0)
    }
    val transfer = remember(rows) { rows.mapNotNull { it.timing?.transfer }.sum() }
    val knownTransfer = remember(rows) { rows.any { it.timing?.transfer != null } }
    val current = rows.firstOrNull { it.id == selected }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val wide = maxWidth >= 520.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onRecord(!state.recording) }, modifier = Modifier.testTag("network_record")) {
                    Text(if (state.recording) "Pause" else "Record", color = if (state.recording) colors.error else colors.primary)
                }
                IconButton(onClick = { selected = null; clearRequests() }, modifier = Modifier.testTag("network_clear")) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear Network log")
                }
                FilterChip(state.preserve, { onPreserve(!state.preserve) }, { Text("Preserve log") },
                    modifier = Modifier.testTag("network_preserve"))
                IconButton(onClick = { update(filters.copy(expanded = !filters.expanded)) }, modifier = Modifier.testTag("network_filters")) {
                    Icon(Icons.Outlined.Search, contentDescription = if (filters.expanded) "Hide filters" else "Search and filter",
                        tint = if (filters.active) colors.primary else LocalContentColor.current)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("network_rows")) {
                if (state.rows.isNotEmpty()) item {
                    TextButton(onClick = onOpenActivity, modifier = Modifier.testTag("network_open_activity")) { Text("Open in Activity") }
                }
                item {
                    val total = if (knownTransfer) " · ${bytes(transfer)} measured" else ""
                    val paused = if (state.recording) "" else " · Paused"
                    val filtered = if (filters.active) " · Filtered" else ""
                    InspectionMessage("${rows.size} of ${state.rows.size} same-origin observations$total$paused$filtered",
                        Modifier.testTag("network_summary"))
                    if (state.rows.size == NETWORK_ROW_LIMIT) InspectionMessage("Network keeps the latest $NETWORK_ROW_LIMIT observations.")
                }
                if (filters.expanded) item {
                    InspectionSearch(filters.query, { update(filters.copy(query = it)) }, "Filter network observations", "network_search")
                    InspectionMessage("Combine method:GET with status-code:4xx. Filter further with resource-type:script, source:timing or path:/assets. Start a term with - to exclude it.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResourceType.entries.forEach { type ->
                            FilterChip(filters.type == type, { update(filters.copy(type = type)) }, { Text(type.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusFilter.entries.forEach { option ->
                            FilterChip(filters.status == option, { update(filters.copy(status = option)) }, { Text(option.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NetworkSource.entries.forEach { option ->
                            FilterChip(filters.source == option, { update(filters.copy(source = option)) }, { Text(option.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(filters.method == null, { update(filters.copy(method = null)) }, { Text("All methods") })
                        methods.forEach { option ->
                            FilterChip(filters.method == option, { update(filters.copy(method = option)) }, { Text(option) })
                        }
                        FilterChip(filters.method == "", { update(filters.copy(method = "")) }, { Text("Method unavailable") })
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NetworkOrder.entries.forEach { option ->
                            FilterChip(filters.order == option, { update(filters.copy(order = option)) }, { Text(option.label) })
                        }
                        FilterChip(filters.waterfall, { update(filters.copy(waterfall = !filters.waterfall)) }, { Text("Waterfall") })
                    }
                    ResetNetworkFilters { update(filters.reset()) }
                }
                if (wide && rows.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Name", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                        Text("Method · Type · Status", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                        Text("Size · Time", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                        if (filters.waterfall) Text("Waterfall", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider()
                }
                if (rows.isEmpty()) item {
                    InspectionMessage(when {
                        origin == null -> "Load an HTTPS page to see its network activity."
                        state.rows.isNotEmpty() -> "No observations match these filters."
                        !state.recording -> "Recording is paused."
                        else -> "Waiting for same-origin network activity."
                    })
                    if (filters.active && !filters.expanded) ResetNetworkFilters { update(filters.reset()) }
                }
                items(rows, key = BrowserRequestEvent::id) { row ->
                    val facts = listOfNotNull(row.method, row.resourceType.takeUnless { it == "unknown" },
                        row.status?.let { "HTTP $it" }, row.kind.label.takeIf { row.timing == null }).joinToString(" · ")
                    val metrics = listOfNotNull(row.timing?.transfer?.let(::bytes),
                        row.timing?.duration?.let(::duration)).joinToString(" · ")
                    val modifier = Modifier.fillMaxWidth().testTag("network_event")
                        .clickable(onClickLabel = "Network details") { selected = row.id }
                        .padding(horizontal = 6.dp, vertical = 9.dp)
                    if (wide) Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.path + if (row.pathClipped) "…" else "", Modifier.weight(2f), style = technicalTextStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(facts, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall,
                            color = if (row.failed) colors.error else colors.onSurfaceVariant)
                        Text(metrics.ifEmpty { "—" }, Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                        if (filters.waterfall) Box(Modifier.weight(1f)) { WaterfallBar(row, start, span) }
                    } else Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.path + if (row.pathClipped) "…" else "", style = technicalTextStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(facts, metrics).filter(String::isNotEmpty).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = if (row.failed) colors.error else colors.onSurfaceVariant)
                        if (filters.waterfall) WaterfallBar(row, start, span)
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
    current?.let { row ->
        key(row.id) { NetworkDetails(row, origin) { selected = null } }
    }
}

@Composable
private fun ResetNetworkFilters(reset: () -> Unit) {
    TextButton(onClick = reset, modifier = Modifier.testTag("network_reset_filters")) { Text("Reset filters") }
}

@Composable
private fun NetworkDetails(row: BrowserRequestEvent, origin: String?, close: () -> Unit) {
    var tab by remember { mutableStateOf(DetailsTab.General) }
    val timing = row.timing
    AlertDialog(onDismissRequest = close, title = { Text("Network details") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailsTab.entries.forEach { item ->
                        FilterChip(tab == item, { tab = item }, { Text(item.label) })
                    }
                }
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    if (tab == DetailsTab.General) {
                        item {
                            if (origin != null) InspectionValue("Origin", origin)
                            InspectionValue("Path", row.path + if (row.pathClipped) "… (clipped)" else "")
                            InspectionValue("Observed at", observedAt(row.timestamp.toLong()))
                            InspectionValue("Source", if (timing == null) "WebView callback · ${row.kind.label}" else "Browser resource timing")
                            InspectionValue("Method", row.method ?: "Unavailable")
                            InspectionValue("Resource type", row.resourceType.takeUnless { it == "unknown" } ?: "Unavailable")
                            InspectionValue("HTTP status", row.status?.toString() ?: "Unavailable")
                            if (row.kind == RequestEventKind.LoadError) InspectionValue("WebView error code", row.code?.toString().orEmpty())
                            if (row.mimeType != null) InspectionValue("Response content type", row.mimeType)
                            if (timing != null) {
                                InspectionValue("Protocol", timing.protocol.ifEmpty { "Unavailable" })
                                InspectionValue("Delivery type", timing.delivery.ifEmpty { "Unavailable" })
                                InspectionValue("Render blocking", timing.renderBlocking.ifEmpty { "Unavailable" })
                                if (timing.contentType.isNotEmpty()) InspectionValue("Response content type", timing.contentType)
                                InspectionValue("Transfer size", timing.transfer?.let(::bytes) ?: "Unavailable")
                                InspectionValue("Encoded body size", timing.encoded?.let(::bytes) ?: "Unavailable")
                                InspectionValue("Decoded body size", timing.decoded?.let(::bytes) ?: "Unavailable")
                            }
                        }
                    } else if (timing == null) item {
                        InspectionMessage("This WebView callback has no timing phases.")
                    } else item {
                        InspectionValue("Start after navigation", duration(timing.start))
                        InspectionValue("Total", duration(timing.duration))
                        InspectionValue("Redirect", duration(timing.redirect))
                        InspectionValue("Service worker before fetch", duration(timing.serviceWorker))
                        InspectionValue("DNS", duration(timing.dns))
                        InspectionValue("Connection", duration(timing.connect))
                        InspectionValue("TLS within connection", duration(timing.tls))
                        InspectionValue("Waiting for response", duration(timing.wait))
                        InspectionValue("Download", duration(timing.download))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } })
}

@Composable private fun WaterfallBar(row: BrowserRequestEvent, startTime: Double, span: Double) {
    val colors = MaterialTheme.colorScheme
    val fraction = ((row.timestamp - startTime) / span).coerceIn(0.0, 1.0).toFloat()
    val width = ((row.timing?.duration ?: return) / span).coerceIn(0.0, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        drawRect(colors.outlineVariant)
        val start = (size.width * fraction).coerceAtMost((size.width - 1f).coerceAtLeast(0f))
        drawRect(colors.primary, topLeft = Offset(start, 0f),
            size = Size((size.width * width).coerceAtLeast(1f).coerceAtMost(size.width - start), size.height))
    }
}

@Composable internal fun InspectionSearch(text: String, update: (String) -> Unit, label: String, tag: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(text, { update(it.atCharacterBoundary(240)) }, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag), keyboardOptions = TechnicalKeyboard.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailingIcon = { if (text.isNotEmpty()) TextButton(onClick = { update("") }) { Text("Clear") } })
}

@Composable internal fun InspectionMessage(message: String, modifier: Modifier = Modifier) {
    Text(message, modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

@Composable internal fun InspectionValue(name: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(name, style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(value.ifBlank { "—" }, style = technicalTextStyle()) }
    }
}

private fun duration(ms: Double?): String = ms?.let { String.format(Locale.ROOT, "%.1f ms", it) } ?: "Unavailable"
private fun bytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KiB", value / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MiB", value / 1048576.0)
}

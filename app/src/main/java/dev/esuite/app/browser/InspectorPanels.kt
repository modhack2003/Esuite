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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.esuite.app.ui.technicalTextStyle
import java.util.Locale

private enum class ResourceType(val label: String, val key: String) {
    All("All", ""), Document("Doc", "document"), Fetch("Fetch / XHR", "fetch"), Script("JS", "script"),
    Style("CSS", "stylesheet"), Image("Img", "image"), Font("Font", "font"), Media("Media", "media"), Other("Other", "other"),
}

private enum class StatusFilter(val label: String) {
    All("All statuses"), Success("2xx"), Redirect("3xx"), Errors("Errors"), Unknown("Unavailable"),
    ;

    fun includes(row: BrowserRequestEvent): Boolean = when (this) {
        All -> true
        Success -> row.status in 200..299
        Redirect -> row.status in 300..399
        Errors -> row.failed
        Unknown -> row.status == null && !row.failed
    }
}

private enum class NetworkOrder(val label: String) { Start("Start time"), Slowest("Slowest"), Largest("Largest") }
private enum class DetailsTab(val label: String) { General("General"), Timing("Timing") }

@Composable
internal fun NetworkPanel(state: BrowserNetworkState, ready: Boolean, clearRequests: () -> Unit,
    onRecord: (Boolean) -> Unit, onPreserve: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var resourceType by rememberSaveable { mutableStateOf(ResourceType.All) }
    var status by rememberSaveable { mutableStateOf(StatusFilter.All) }
    var order by rememberSaveable { mutableStateOf(NetworkOrder.Start) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showWaterfall by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = remember(state.rows, resourceType, status, query, order) {
        val text = query.trim()
        val found = state.rows.filter { row ->
            val typeMatches = resourceType == ResourceType.All || row.resourceType == resourceType.key ||
                (resourceType == ResourceType.Other && row.resourceType == "unknown")
            typeMatches && status.includes(row) &&
                "${row.path} ${row.method.orEmpty()} ${row.kind.label} ${row.resourceType} ${row.status ?: ""}".contains(text, true)
        }
        when (order) {
            NetworkOrder.Start -> found.sortedBy { it.timestamp }
            NetworkOrder.Slowest -> found.sortedByDescending { it.timing?.duration ?: -1.0 }
            NetworkOrder.Largest -> found.sortedByDescending { it.timing?.transfer ?: -1L }
        }
    }
    val end = remember(rows) { rows.maxOfOrNull { (it.timing?.start ?: 0.0) + (it.timing?.duration ?: 0.0) }?.coerceAtLeast(1.0) ?: 1.0 }
    val transfer = remember(rows) { rows.mapNotNull { it.timing?.transfer }.sum() }
    val knownTransfer = remember(rows) { rows.any { it.timing?.transfer != null } }
    val current = state.rows.firstOrNull { it.id == selected }
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
                IconButton(onClick = { showFilters = !showFilters }, modifier = Modifier.testTag("network_filters")) {
                    Icon(Icons.Outlined.Search, contentDescription = if (showFilters) "Hide filters" else "Search and filter")
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("network_rows")) {
                if (showFilters) item {
                    InspectionSearch(query, { query = it }, "Filter by path, type, method, or status", "network_search")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResourceType.entries.forEach { type ->
                            FilterChip(resourceType == type, { resourceType = type }, { Text(type.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusFilter.entries.forEach { option ->
                            FilterChip(status == option, { status = option }, { Text(option.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NetworkOrder.entries.forEach { option ->
                            FilterChip(order == option, { order = option }, { Text(option.label) })
                        }
                        if (wide) FilterChip(showWaterfall, { showWaterfall = !showWaterfall }, { Text("Waterfall") })
                    }
                }
                item {
                    val total = if (knownTransfer) " · ${bytes(transfer)} measured" else ""
                    val paused = if (state.recording) "" else " · Paused"
                    InspectionMessage("${rows.size} of ${state.rows.size} same-origin observations$total$paused")
                }
                if (wide && rows.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Name", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                        Text("Method · Type · Status", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                        Text("Size · Time", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                        if (showWaterfall) Text("Waterfall", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider()
                }
                if (rows.isEmpty()) item {
                    InspectionMessage(when {
                        !ready -> "Load an HTTPS page to see its network activity."
                        state.rows.isNotEmpty() -> "No observations match these filters."
                        !state.recording -> "Recording is paused."
                        else -> "Waiting for same-origin network activity."
                    })
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
                        Text(row.path, Modifier.weight(2f), style = technicalTextStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(facts, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall,
                            color = if (row.failed) colors.error else colors.onSurfaceVariant)
                        Text(metrics.ifEmpty { "—" }, Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                        if (showWaterfall) Box(Modifier.weight(1f)) { row.timing?.let { WaterfallBar(it, end) } }
                    } else Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.path, style = technicalTextStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(facts, metrics).filter(String::isNotEmpty).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = if (row.failed) colors.error else colors.onSurfaceVariant)
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
    current?.let { row ->
        key(row.id) { NetworkDetails(row) { selected = null } }
    }
}

@Composable
private fun NetworkDetails(row: BrowserRequestEvent, close: () -> Unit) {
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
                            InspectionValue("Path", row.path)
                            InspectionValue("Source", if (timing == null) "WebView callback · ${row.kind.label}" else "Browser resource timing")
                            InspectionValue("Method", row.method ?: "Unavailable")
                            InspectionValue("Resource type", row.resourceType.takeUnless { it == "unknown" } ?: "Unavailable")
                            InspectionValue("HTTP status", row.status?.toString() ?: "Unavailable")
                            if (row.kind == RequestEventKind.LoadError) InspectionValue("WebView error code", row.code?.toString().orEmpty())
                            if (row.mimeType != null) InspectionValue("Response content type", row.mimeType)
                            if (timing != null) {
                                InspectionValue("Protocol", timing.protocol.ifEmpty { "Unavailable" })
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
                        InspectionValue("DNS", duration(timing.dns))
                        InspectionValue("Connection", duration(timing.connect))
                        InspectionValue("TLS", duration(timing.tls))
                        InspectionValue("Waiting for response", duration(timing.wait))
                        InspectionValue("Download", duration(timing.download))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } })
}

@Composable private fun WaterfallBar(resource: ResourceTiming, end: Double) {
    val colors = MaterialTheme.colorScheme
    val fraction = ((resource.start ?: return) / end).coerceIn(0.0, 1.0).toFloat()
    val width = ((resource.duration ?: return) / end).coerceIn(0.0, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        drawRect(colors.outlineVariant)
        val start = size.width * fraction
        drawRect(colors.primary, topLeft = Offset(start, 0f),
            size = Size((size.width * width).coerceAtLeast(1f).coerceAtMost(size.width - start), size.height))
    }
}

@Composable internal fun InspectionSearch(text: String, update: (String) -> Unit, label: String, tag: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(text, { update(it.take(240)) }, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag), keyboardOptions = TechnicalKeyboard.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailingIcon = { if (text.isNotEmpty()) TextButton(onClick = { update("") }) { Text("Clear") } })
}

@Composable internal fun InspectionMessage(message: String) {
    Text(message, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
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

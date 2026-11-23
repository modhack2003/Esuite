package dev.esuite.app.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable internal data class DomTree(val rows: List<DomNode>, val limited: Boolean)
@Serializable internal data class DomNode(val id: Int, val parent: Int, val depth: Int, val tag: String, val name: String,
    val classes: List<String>, val children: Int) {
    val description: String get() = buildString {
        append(tag)
        if (name.isNotEmpty()) append('#').append(name)
        classes.forEach { append('.').append(it) }
    }
}
@Serializable internal data class InspectionProperty(val name: String, val value: String)
@Serializable internal data class DomDetails(val tag: String, val html: String, val htmlCharacters: Int, val htmlTruncated: Boolean,
    val htmlLimit: Int, val css: String, val attributes: List<InspectionProperty>, val rules: List<InspectionProperty>,
    val computed: List<InspectionProperty>, val layout: List<InspectionProperty>, val bounds: List<Double>, val scroll: List<Int>)
@Serializable internal data class ResourceTiming(val path: String, val kind: String, val status: Int?, val start: Double?, val duration: Double?,
    val transfer: Long?, val protocol: String, val dns: Double?, val connect: Double?, val wait: Double?, val download: Double?)

private enum class ElementView(val label: String) { Markup("Markup"), Styles("Styles"), Layout("Layout") }

@Composable
internal fun ElementsPanel(tree: DomTree?, detail: DomDetails?, ready: Boolean, notice: String,
    onPick: () -> Unit, onRead: () -> Unit, onSelect: (Int) -> Unit, onReveal: () -> Unit,
    onEdit: (html: Boolean, text: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var finding by remember { mutableStateOf(false) }
    var collapsed by remember(tree) { mutableStateOf(emptySet<Int>()) }
    var browsing by remember { mutableStateOf(true) }
    var view by remember { mutableStateOf(ElementView.Markup) }
    var editing by remember { mutableStateOf<Boolean?>(null) }
    var editor by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val visibleRows = remember(tree, collapsed, query) {
        val childrenHidden = HashSet<Int>()
        tree?.rows.orEmpty().filter { row ->
            if (query.isNotBlank()) row.description.contains(query.trim(), ignoreCase = true)
            else (row.parent !in childrenHidden).also { visible -> if (!visible || row.id in collapsed) childrenHidden.add(row.id) }
        }
    }
    if (!ready) {
        InspectionMessage("Load an HTTPS page to inspect its elements.")
        return
    }
    LaunchedEffect(detail) { if (detail != null) browsing = false }
    LaunchedEffect(browsing, view, query) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!browsing) TextButton(onClick = { browsing = true }) { Text("Tree") }
            TextButton(onClick = { onPick() }) { Text("Pick element") }
            TextButton(onClick = { browsing = false; onRead() }) { Text("Read selection") }
            if (!browsing && detail != null) TextButton(onClick = onReveal) { Text("Show on page") }
            if (browsing) TextButton(onClick = { finding = !finding; if (!finding) query = "" }) {
                Text(if (finding) "Done" else "Find")
            }
        }
        if (notice.isNotBlank()) InspectionMessage(notice)
        if (browsing && finding) InspectionSearch(query, { query = it }, "Find tag, ID, or class", "elements_search")
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, contentPadding = PaddingValues(vertical = 4.dp)) {
            if (browsing) {
                if (visibleRows.isEmpty()) item { InspectionMessage(if (tree == null) "Reading the document…" else "No matching elements") }
                items(visibleRows, key = DomNode::id) { node ->
                    val expand = if (node.id in collapsed) "Expand" else "Collapse"
                    ListItem(
                        headlineContent = { Text(node.description, style = technicalTextStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.padding(start = (node.depth.coerceIn(0, 9) * 9).dp).testTag("elements_node_${node.id}")
                            .clickable(onClickLabel = "Inspect element") { onSelect(node.id); browsing = false },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        trailingContent = { if (node.children > 0 && query.isBlank()) {
                            IconButton(onClick = { collapsed = if (node.id in collapsed) collapsed - node.id else collapsed + node.id }) {
                                Icon(if (node.id in collapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "$expand ${node.tag}")
                            }
                        } },
                    )
                    HorizontalDivider()
                }
                if (tree?.limited == true) item { InspectionMessage("Showing the first 160 elements in document order.") }
            } else if (detail != null) {
                item {
                    Text(detail.tag, Modifier.padding(8.dp), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ElementView.entries.forEach { option -> FilterChip(view == option, { view = option }, { Text(option.label) }) }
                    }
                }
                when (view) {
                    ElementView.Markup -> {
                        item {
                            TextButton(onClick = { editor = ""; editing = true }) { Text("Replace HTML") }
                            if (detail.htmlTruncated) InspectionMessage("Showing ${detail.htmlLimit} of ${detail.htmlCharacters} characters.")
                            SelectionContainer { Text(detail.html, Modifier.padding(8.dp), style = technicalTextStyle()) }
                        }
                        item { InspectionMessage("Attributes") }
                        items(detail.attributes) { InspectionValue(it.name, it.value) }
                    }
                    ElementView.Styles -> {
                        item { TextButton(onClick = { editor = detail.css; editing = false }) { Text("Edit inline CSS") } }
                        item { InspectionMessage("Computed properties") }
                        items(detail.computed) { InspectionValue(it.name, it.value) }
                        if (detail.rules.isNotEmpty()) item { InspectionMessage("Matched rules from readable stylesheets") }
                        items(detail.rules) { InspectionValue(it.name, it.value) }
                    }
                    ElementView.Layout -> {
                        item {
                            InspectionValue("Viewport position (x, y)", detail.bounds.take(2).joinToString(", ") + " CSS px")
                            InspectionValue("Box size", detail.bounds.drop(2).joinToString(" × ") + " CSS px")
                            InspectionValue("Scrollable size", detail.scroll.take(2).joinToString(" × ") + " CSS px")
                            InspectionValue("Client size", detail.scroll.drop(2).joinToString(" × ") + " CSS px")
                        }
                        items(detail.layout) { InspectionValue(it.name, it.value) }
                    }
                }
            } else item { InspectionMessage("Select an element in the tree or pick one on the page.") }
        }
    }
    editing?.let { html ->
        val excess = editor.length > 8_192
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (html) "Replace selected HTML" else "Edit inline CSS") },
            text = { OutlinedTextField(editor, { editor = it.take(8_193) }, label = { Text(if (html) "HTML" else "CSS declarations") },
                isError = excess, minLines = 3, maxLines = 6, keyboardOptions = TechnicalKeyboard,
                textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("browser_element_edit"),
                supportingText = if (excess) ({ Text("Maximum 8,192 characters") }) else null) },
            confirmButton = { TextButton(enabled = !excess, onClick = { onEdit(html, editor); editing = null }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

private enum class NetworkView(val label: String) { Requests("Requests"), Waterfall("Waterfall") }
private enum class NetworkFilter(val label: String, val timingOnly: Boolean = false) {
    All("All"), Documents("Documents"), Errors("Errors"), Fetch("Fetch / XHR", timingOnly = true), Scripts("Scripts", timingOnly = true)
}
private enum class TimingOrder(val label: String) { Start("Start time"), Slowest("Slowest first"), Largest("Largest first") }

@Composable
internal fun NetworkPanel(requests: List<BrowserRequestEvent>, timings: List<ResourceTiming>, ready: Boolean,
    clearRequests: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(NetworkView.Requests) }
    var filter by remember { mutableStateOf(NetworkFilter.All) }
    var order by remember { mutableStateOf(TimingOrder.Start) }
    var showFilters by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<Pair<List<BrowserRequestEvent>, List<ResourceTiming>>?>(null) }
    var detail by remember { mutableStateOf<List<InspectionProperty>?>(null) }
    val list = rememberLazyListState()
    val (events, resources) = frozen ?: (requests to timings)
    val end = remember(resources) { resources.maxOfOrNull { (it.start ?: 0.0) + (it.duration ?: 0.0) }?.coerceAtLeast(1.0) ?: 1.0 }
    if (!ready) {
        InspectionMessage("Load an HTTPS page to see its network activity.")
        return
    }
    LaunchedEffect(mode, showFilters) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NetworkView.entries.forEach { option ->
                FilterChip(mode == option, {
                    mode = option
                    if (mode == NetworkView.Requests && filter.timingOnly) filter = NetworkFilter.All
                }, { Text(option.label) })
            }
            TextButton(onClick = { showFilters = !showFilters }, modifier = Modifier.testTag("network_filters")) {
                Text(if (showFilters) "Done" else "Filters")
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, contentPadding = PaddingValues(vertical = 4.dp)) {
            if (showFilters) item {
                InspectionSearch(query, { query = it }, "Filter by path, type, or status", "network_search")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val filters = NetworkFilter.entries.filter { mode == NetworkView.Waterfall || !it.timingOnly }
                    filters.forEach { option -> FilterChip(filter == option, { filter = option }, { Text(option.label) }) }
                }
                if (mode == NetworkView.Waterfall) Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimingOrder.entries.forEach { option -> FilterChip(order == option, { order = option }, { Text(option.label) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { frozen = if (frozen == null) requests to timings else null }) { Text(if (frozen == null) "Freeze view" else "Resume view") }
                    if (mode == NetworkView.Requests) TextButton(onClick = { frozen = null; clearRequests() }) { Text("Clear requests") }
                }
            }
            if (mode == NetworkView.Requests) {
                val filtered = events.filter { event ->
                    "${event.kind.label} ${event.method} ${event.path} ${event.code.orEmptyLabel()}".contains(query.trim(), true) && when (filter) {
                        NetworkFilter.All -> true
                        NetworkFilter.Documents -> event.mainFrame
                        NetworkFilter.Errors -> event.kind == RequestEventKind.HttpError || event.kind == RequestEventKind.LoadError
                        NetworkFilter.Fetch, NetworkFilter.Scripts -> false
                    }
                }
                item { InspectionMessage("Same-origin WebView events · ${filtered.size} shown${if (frozen != null) " · Frozen" else ""}") }
                if (filtered.isEmpty()) item { InspectionMessage("No matching events") }
                items(filtered, key = BrowserRequestEvent::id) { event ->
                    val label = if (event.kind == RequestEventKind.Navigation) "Navigation ${event.path}" else "${event.method} ${event.path}"
                    ListItem(headlineContent = { Text(label, Modifier.testTag("network_event"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(listOfNotNull(event.kind.label, event.code?.toString()).joinToString(" · ")) },
                        trailingContent = { TextButton(onClick = { detail = listOf(
                            InspectionProperty("Path", event.path), InspectionProperty("Method", event.method.ifBlank { "Unavailable" }),
                            InspectionProperty("Callback", event.kind.label), InspectionProperty("Frame", if (event.mainFrame) "Main" else "Subresource"),
                            InspectionProperty("Observed status or error code", event.code?.toString() ?: "Unavailable"),
                        ) }) { Text("Details") } })
                    HorizontalDivider()
                }
            } else {
                val filtered = resources.filter { resource ->
                    "${resource.path} ${resource.kind} ${resource.status.orEmptyLabel()}".contains(query.trim(), true) && when (filter) {
                        NetworkFilter.All -> true
                        NetworkFilter.Documents -> resource.kind == "document"
                        NetworkFilter.Errors -> (resource.status ?: 0) >= 400
                        NetworkFilter.Fetch -> resource.kind == "fetch" || resource.kind == "xmlhttprequest"
                        NetworkFilter.Scripts -> resource.kind == "script"
                    }
                }
                val ordered = when (order) {
                    TimingOrder.Start -> filtered.sortedBy { it.start ?: Double.POSITIVE_INFINITY }
                    TimingOrder.Slowest -> filtered.sortedByDescending { it.duration ?: -1.0 }
                    TimingOrder.Largest -> filtered.sortedByDescending { it.transfer ?: -1L }
                }
                item { InspectionMessage("Same-origin timing · ${filtered.size} shown · ${duration(end)} scale${if (frozen != null) " · Frozen" else ""}") }
                if (filtered.isEmpty()) item { InspectionMessage("No matching resource timings") }
                items(ordered) { resource ->
                    ListItem(headlineContent = { Text(resource.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(listOfNotNull(resource.kind, resource.status?.let { "HTTP $it" }, duration(resource.duration)).joinToString(" · "))
                                WaterfallBar(resource, end)
                            }
                        }, trailingContent = { TextButton(onClick = { detail = listOf(
                            InspectionProperty("Path", resource.path), InspectionProperty("Type", resource.kind),
                            InspectionProperty("Status", resource.status?.toString() ?: "Unavailable"),
                            InspectionProperty("Protocol", resource.protocol.ifBlank { "Unavailable" }),
                            InspectionProperty("Transfer size", resource.transfer?.let { "$it B" } ?: "Unavailable"),
                            InspectionProperty("Start", duration(resource.start)), InspectionProperty("Duration", duration(resource.duration)),
                            InspectionProperty("DNS", duration(resource.dns)), InspectionProperty("Connect", duration(resource.connect)),
                            InspectionProperty("Waiting for response", duration(resource.wait)), InspectionProperty("Download", duration(resource.download)),
                        ) }) { Text("Details") } })
                    HorizontalDivider()
                }
            }
        }
    }
    detail?.let { properties ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text("Network details") },
            text = { LazyColumn(Modifier.heightIn(max = 360.dp)) { items(properties) { InspectionValue(it.name, it.value) } } },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } })
    }
}

@Composable private fun WaterfallBar(resource: ResourceTiming, end: Double) {
    val colors = MaterialTheme.colorScheme
    val fraction = ((resource.start ?: 0.0) / end).coerceIn(0.0, 1.0).toFloat()
    val width = ((resource.duration ?: 0.0) / end).coerceIn(0.0, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        drawRect(colors.outlineVariant)
        val start = size.width * fraction
        drawRect(colors.primary, topLeft = Offset(start, 0f), size = Size((size.width * width).coerceAtLeast(1f).coerceAtMost(size.width - start), size.height))
    }
}

@Composable private fun InspectionSearch(text: String, update: (String) -> Unit, label: String, tag: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(text, { update(it.take(240)) }, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag), keyboardOptions = TechnicalKeyboard.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailingIcon = { if (text.isNotEmpty()) TextButton(onClick = { update("") }) { Text("Clear") } })
}

@Composable private fun InspectionMessage(message: String) {
    Text(message, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

@Composable private fun InspectionValue(name: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(name, style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(value.ifBlank { "—" }, style = technicalTextStyle()) }
    }
}

private fun duration(ms: Double?): String = ms?.let { String.format(Locale.ROOT, "%.1f ms", it) } ?: "Unavailable"
private fun Int?.orEmptyLabel(): String = this?.toString().orEmpty()

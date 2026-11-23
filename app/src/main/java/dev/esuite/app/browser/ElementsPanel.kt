package dev.esuite.app.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.esuite.app.ui.TechnicalKeyboard
import dev.esuite.app.ui.technicalTextStyle
import kotlinx.serialization.Serializable

@Serializable internal data class InspectionProperty(val name: String, val value: String)
@Serializable internal data class DomTreeOptions(val expanded: Map<Int, Int>, val query: String, val reveal: Boolean)
@Serializable internal data class DomTree(val rows: List<DomNode>, val limited: Boolean, val selected: Int?)
@Serializable internal data class DomNode(
    val id: Int, val parent: Int, val depth: Int, val tag: String,
    val attributes: List<InspectionProperty>, val text: String, val comment: Boolean,
    val children: Boolean, val expanded: Boolean, val voidTag: Boolean, val more: Boolean,
    val attributesLimited: Boolean,
)
@Serializable internal data class DomDetails(
    val tag: String, val html: String, val htmlCharacters: Int, val htmlTruncated: Boolean,
    val htmlLimit: Int, val css: String, val cssCharacters: Int, val attributes: List<InspectionProperty>, val rules: List<InspectionProperty>,
    val computed: List<InspectionProperty>, val layout: List<InspectionProperty>, val bounds: List<Double>, val scroll: List<Int>,
)

private enum class ElementView(val label: String) { Attributes("Attributes"), Styles("Styles"), Layout("Layout"), Markup("Markup") }

private data class MarkupLine(val node: DomNode, val kind: String) {
    val key: String get() = "$kind:${node.id}"
}

private fun DomTree.lines(): List<MarkupLine> = buildList {
    val parents = ArrayDeque<DomNode>()
    fun closeLast() {
        val parent = parents.removeLast()
        if (parent.more) add(MarkupLine(parent, "more"))
        add(MarkupLine(parent, "close"))
    }
    rows.forEach { node ->
        while (parents.isNotEmpty() && parents.last().depth >= node.depth) closeLast()
        add(MarkupLine(node, "open"))
        if (node.expanded) parents.addLast(node)
    }
    while (parents.isNotEmpty()) closeLast()
}

private const val EDIT_LIMIT = 8192

private fun DomTree.breadcrumbs(): List<DomNode> {
    val nodes = rows.associateBy(DomNode::id)
    val path = mutableListOf<DomNode>()
    var current = selected?.let(nodes::get)
    while (current != null && path.size <= 100) {
        path.add(current)
        current = nodes[current.parent]
    }
    return path.asReversed()
}

@Composable
internal fun ElementsPanel(
    tree: DomTree?, detail: DomDetails?, ready: Boolean, notice: String, query: String, reveal: Int,
    picking: Boolean, onPick: () -> Unit, onSearch: (String) -> Unit, onExpand: (DomNode, Boolean) -> Unit,
    onSelect: (Int) -> Unit, onReveal: () -> Unit,
    onEdit: (html: Boolean, original: String, draft: String, complete: (Boolean) -> Unit) -> Unit,
) {
    var finding by remember { mutableStateOf(query.isNotBlank()) }
    var view by remember { mutableStateOf<ElementView?>(null) }
    var editHtml by remember { mutableStateOf<Boolean?>(null) }
    var editor by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    var editOverflow by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val lines = remember(tree) { tree?.lines().orEmpty() }
    val breadcrumbs = remember(tree) { tree?.breadcrumbs().orEmpty() }
    val showGutter = LocalDensity.current.fontScale <= 1.3f
    if (!ready) {
        InspectionMessage("Load an HTTPS page to inspect its elements.")
        return
    }
    LaunchedEffect(reveal, query) {
        val index = if (query.isBlank()) lines.indexOfFirst { it.kind == "open" && it.node.id == tree?.selected } else 0
        if (index >= 0 && lines.isNotEmpty()) list.scrollToItem(index)
    }
    fun startEdit(html: Boolean, value: String) {
        editor = value
        original = value
        editOverflow = false
        editHtml = html
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onPick, modifier = Modifier.testTag("elements_pick")) {
                Text(if (picking) "Cancel pick" else "Pick element")
            }
            TextButton(onClick = { finding = !finding; if (!finding) onSearch("") }, modifier = Modifier.testTag("elements_find")) {
                Text(if (finding) "Close search" else "Find")
            }
            if (detail != null) {
                TextButton(onClick = { startEdit(true, detail.html) }, modifier = Modifier.testTag("elements_edit_html"), enabled = !detail.tag.equals("html", true) &&
                    !detail.htmlTruncated && detail.htmlCharacters <= EDIT_LIMIT) { Text("Edit as HTML") }
                TextButton(onClick = onReveal) { Text("Show on page") }
                ElementView.entries.forEach { option ->
                    FilterChip(view == option, { view = if (view == option) null else option }, { Text(option.label) },
                        modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (finding) InspectionSearch(query, onSearch, "Find tag, attribute, or text", "elements_search")
        if (breadcrumbs.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("elements_breadcrumbs"),
                verticalAlignment = Alignment.CenterVertically) {
                breadcrumbs.forEachIndexed { index, node ->
                    if (index > 0) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp))
                    TextButton(onClick = { onSelect(node.id) }, enabled = index < breadcrumbs.lastIndex) {
                        Text(node.tag, style = technicalTextStyle())
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("elements_markup"), state = list) {
            items(lines.size, key = { lines[it].key }) { index ->
                val line = lines[index]
                val selected = line.node.id == tree?.selected && line.kind == "open"
                MarkupRow(line, if (showGutter) index + 1 else null, selected, query.isBlank(), onExpand, onSelect)
                if (selected && detail != null) {
                    if (detail.htmlCharacters > EDIT_LIMIT) InspectionMessage("HTML editing supports up to 8,192 characters per element.")
                    when (view) {
                        ElementView.Attributes -> detail.attributes.forEach { InspectionValue(it.name, it.value) }
                        ElementView.Styles -> {
                            TextButton(onClick = { startEdit(false, detail.css) }, enabled = detail.cssCharacters <= EDIT_LIMIT) {
                                Text("Edit inline CSS")
                            }
                            if (detail.cssCharacters > EDIT_LIMIT) InspectionMessage("Inline CSS editing supports up to 8,192 characters per element.")
                            detail.computed.forEach { InspectionValue(it.name, it.value) }
                            if (detail.rules.isNotEmpty()) InspectionMessage("Matched rules from readable stylesheets")
                            detail.rules.forEach { InspectionValue(it.name, it.value) }
                        }
                        ElementView.Layout -> {
                            InspectionValue("Viewport position", detail.bounds.take(2).joinToString(", ") + " CSS px")
                            InspectionValue("Box size", detail.bounds.drop(2).joinToString(" × ") + " CSS px")
                            InspectionValue("Scrollable size", detail.scroll.take(2).joinToString(" × ") + " CSS px")
                            detail.layout.forEach { InspectionValue(it.name, it.value) }
                        }
                        ElementView.Markup -> {
                            if (detail.htmlTruncated) InspectionMessage("Showing a preview of ${detail.htmlLimit} characters.")
                            SelectionContainer {
                                Text(detail.html, Modifier.fillMaxWidth().padding(8.dp).testTag("elements_markup_preview"),
                                    style = technicalTextStyle())
                            }
                        }
                        null -> Unit
                    }
                }
            }
            when {
                lines.isEmpty() && tree == null -> item { InspectionMessage("Reading the document…") }
                lines.isEmpty() -> item {
                    InspectionMessage(if (tree?.limited == true) "No match in the portion of the document searched." else "No matching nodes.")
                }
                tree?.limited == true -> item { InspectionMessage("This view reached its document limit. Collapse a branch or refine your search.") }
            }
            if (notice.isNotBlank()) item { InspectionMessage(notice) }
        }
    }
    editHtml?.let { html ->
        AlertDialog(
            onDismissRequest = { if (!applying) editHtml = null },
            title = { Text(if (html) "Edit as HTML" else "Edit inline CSS") },
            text = {
                OutlinedTextField(editor, {
                    editOverflow = it.length > EDIT_LIMIT
                    if (!editOverflow) editor = it
                }, enabled = !applying,
                    label = { Text(if (html) "HTML" else "CSS declarations") }, minLines = 3, maxLines = 8,
                    isError = editOverflow, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(),
                    modifier = Modifier.fillMaxWidth().testTag("browser_element_edit"),
                    supportingText = {
                        Text(if (editOverflow) "Maximum 8,192 characters." else notice)
                    })
            },
            confirmButton = {
                TextButton(enabled = !applying && !editOverflow, onClick = {
                    applying = true
                    onEdit(html, original, editor) { success ->
                        applying = false
                        if (success) editHtml = null
                    }
                }, modifier = Modifier.testTag("elements_apply")) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { editHtml = null }, enabled = !applying) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MarkupRow(
    line: MarkupLine, number: Int?, selected: Boolean, expandable: Boolean,
    onExpand: (DomNode, Boolean) -> Unit, onSelect: (Int) -> Unit,
) {
    val node = line.node
    val colors = MaterialTheme.colorScheme
    val indent = (node.depth.coerceAtMost(12) * 10).dp
    if (line.kind == "more") {
        TextButton(onClick = { onExpand(node, true) }, modifier = Modifier.padding(start = indent + 12.dp)) {
            Text("Show more <${node.tag}> children")
        }
        return
    }
    val open = line.kind == "open"
    Row(
        Modifier.fillMaxWidth().background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(number.toString(), Modifier.width(32.dp).padding(start = 4.dp),
                maxLines = 1, style = technicalTextStyle(), color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(indent))
        if (open && node.children && expandable) {
            IconButton(onClick = { onExpand(node, false) }, modifier = Modifier.size(48.dp).testTag("elements_toggle_${node.id}")) {
                Icon(if (node.expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = (if (node.expanded) "Collapse " else "Expand ") + node.tag)
            }
        } else Spacer(Modifier.width(48.dp))
        val markup = buildAnnotatedString {
            when {
                node.comment -> withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("<!-- ${node.text} -->") }
                node.tag.isBlank() -> append(node.text)
                !open -> withStyle(SpanStyle(color = colors.primary)) { append("</${node.tag}>") }
                else -> {
                    withStyle(SpanStyle(color = colors.primary)) { append("<${node.tag}") }
                    node.attributes.forEach { attribute ->
                        withStyle(SpanStyle(color = colors.tertiary)) { append(" ${attribute.name}=") }
                        append("\"")
                        withStyle(SpanStyle(color = colors.secondary)) {
                            append(attribute.value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;"))
                        }
                        append("\"")
                    }
                    if (node.attributesLimited) append(" …")
                    withStyle(SpanStyle(color = colors.primary)) {
                        append(">")
                        if (!node.voidTag && !node.expanded) append(if (node.children) "…</${node.tag}>" else "</${node.tag}>")
                    }
                }
            }
        }
        val selectable = if (open && node.tag.isNotBlank()) Modifier.testTag("elements_node_${node.id}")
            .clickable(onClickLabel = "Inspect ${node.tag}") { onSelect(node.id) } else Modifier
        Text(markup, selectable.weight(1f).padding(end = 4.dp, top = 6.dp, bottom = 6.dp),
            style = technicalTextStyle(), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

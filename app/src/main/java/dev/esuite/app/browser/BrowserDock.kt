package dev.esuite.app.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.esuite.app.ui.technicalTextStyle
import dev.esuite.app.ui.TechnicalKeyboard
import kotlin.math.roundToInt

internal enum class BrowserTool(val label: String, val summary: String) {
    Page("Page", "Page title"),
    Elements("Elements", "Document outline"),
    Console("Console", "JavaScript console"),
    Network("Network", "Same-origin requests and resource timing"),
    Sources("Sources", "Document stylesheets and scripts"),
    Storage("Storage", "Storage keys and value lengths"),
    Cookies("Cookies", "Cookie names and available flags"),
    Performance("Performance", "Page load milestones"),
    Viewport("Viewport", "Responsive page measurements"),
    Security("Security", "Security context"),
    Accessibility("Accessibility", "Document semantics"),
}

@Composable
internal fun BrowserDock(
    dockVisible: Boolean,
    page: @Composable () -> Unit,
    dock: @Composable () -> Unit,
) {
    var heightFraction by rememberSaveable { mutableFloatStateOf(0.47f) }
    var widthFraction by rememberSaveable { mutableFloatStateOf(0.43f) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val side = maxWidth >= 720.dp || (maxWidth >= 480.dp && maxHeight < 400.dp)
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val maxFraction = if (side) 0.66f else 0.74f
        val minFraction = if (side) 0.30f else 0.27f
        val fraction = if (side) widthFraction else if (imeVisible) maxOf(heightFraction, 0.73f) else heightFraction
        fun resize(value: Float) {
            val bounded = value.coerceIn(minFraction, maxFraction)
            if (side) widthFraction = bounded else heightFraction = bounded
        }
        val handlePixels = with(LocalDensity.current) { 24.dp.roundToPx() }
        val extent = if (side) constraints.maxWidth else constraints.maxHeight
        val available = (extent - handlePixels).coerceAtLeast(0)
        val dockExtent = (available * fraction).roundToInt()
        val resizeState = rememberDraggableState { delta ->
            if (available > 0) resize((if (side) widthFraction else heightFraction) - delta / available)
        }
        Layout(content = {
            Box(Modifier.clipToBounds(), propagateMinConstraints = true) { page() }
            if (dockVisible) {
                DockHandle(side, fraction, minFraction..maxFraction, resizeState, ::resize)
                Box(Modifier.clipToBounds().testTag("browser_dock"), propagateMinConstraints = true) { dock() }
            }
        }) { children, layout ->
            val width = layout.maxWidth
            val height = layout.maxHeight
            val pageExtent = if (dockVisible) available - dockExtent else extent
            val pageBounds = if (side) Constraints.fixed(pageExtent, height) else Constraints.fixed(width, pageExtent)
            val pagePlaceable = children[0].measure(pageBounds)
            val handleSize = extent - available
            val handle = children.getOrNull(1)?.measure(if (side) Constraints.fixed(handleSize, height) else Constraints.fixed(width, handleSize))
            val panel = children.getOrNull(2)?.measure(if (side) Constraints.fixed(dockExtent, height) else Constraints.fixed(width, dockExtent))
            layout(width, height) {
                pagePlaceable.place(0, 0)
                if (side) {
                    handle?.place(pageExtent, 0)
                    panel?.place(pageExtent + handleSize, 0)
                } else {
                    handle?.place(0, pageExtent)
                    panel?.place(0, pageExtent + handleSize)
                }
            }
        }
    }
}

@Composable
private fun DockHandle(
    side: Boolean,
    fraction: Float,
    range: ClosedFloatingPointRange<Float>,
    drag: androidx.compose.foundation.gestures.DraggableState,
    resize: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(colors.surfaceContainer)
            .testTag("dock_resize_handle")
            .semantics {
                contentDescription = "Resize tools panel"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, range)
                setProgress { resize(it); true }
            }
            .draggable(drag, if (side) Orientation.Horizontal else Orientation.Vertical),
        contentAlignment = Alignment.Center,
    ) {
        val shape = MaterialTheme.shapes.extraSmall
        Box(Modifier.size(if (side) 4.dp else 38.dp, if (side) 38.dp else 4.dp).background(colors.outline, shape))
    }
}

@Composable
internal fun DockHeader(title: String, context: String, close: () -> Unit, refresh: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceContainer).padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (context.isNotBlank()) Text(context, style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (refresh != null) IconButton(onClick = refresh, modifier = Modifier.testTag("browser_refresh_inspection")) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh inspection")
        }
        IconButton(onClick = close, modifier = Modifier.testTag("browser_close_dock")) {
            Icon(Icons.Outlined.Close, contentDescription = "Close tools panel")
        }
    }
}

@Composable
internal fun BrowserInspector(
    panel: BrowserTool,
    output: String,
    currentUrl: String?,
    choose: (BrowserTool) -> Unit,
    refresh: () -> Unit,
    close: () -> Unit,
    pick: () -> Unit,
    selection: () -> Unit,
    report: () -> Unit,
    edit: (html: Boolean, code: String) -> Unit,
    openApi: (String) -> Unit,
    console: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var editingHtml by remember { mutableStateOf<Boolean?>(null) }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(currentUrl, panel) { editingHtml = null }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surfaceContainerLow)) {
        val compactConsole = panel == BrowserTool.Console && maxHeight < 205.dp && WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.fillMaxSize()) {
            if (!compactConsole) {
                DockHeader("Developer Tools", currentUrl?.let(BrowserAddress::https)?.let(BrowserAddress::origin).orEmpty(),
                    close, refresh.takeIf { panel != BrowserTool.Console && currentUrl != null })
                Row(
                    Modifier.fillMaxWidth().background(colors.surfaceContainer).testTag("browser_tool_tabs")
                        .horizontalScroll(rememberScrollState()).selectableGroup(),
                ) {
                    BrowserTool.entries.forEach { tool ->
                        val active = tool == panel
                        Column(
                            Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
                                .testTag("browser_tool_tab_${tool.name.lowercase()}")
                                .selectable(active, role = Role.Tab, onClick = { choose(tool) }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.weight(1f, fill = false).heightIn(min = 46.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(tool.label, style = MaterialTheme.typography.labelLarge,
                                    color = if (active) colors.primary else colors.onSurfaceVariant, maxLines = 1)
                            }
                            HorizontalDivider(thickness = 2.dp, color = if (active) colors.primary else colors.outlineVariant)
                        }
                    }
                }
            }
            if (panel == BrowserTool.Console) console()
            else key(panel) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(panel.summary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("browser_tool_heading"))
                    if (currentUrl != null) when (panel) {
                        BrowserTool.Page -> TextButton(onClick = { openApi(currentUrl) }, modifier = Modifier.testTag("browser_open_api")) {
                            Text("Open in API Lab")
                        }
                        BrowserTool.Elements -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = pick) { Text("Pick element") }
                            TextButton(onClick = selection) { Text("Read selection") }
                            TextButton(onClick = report) { Text("Document outline") }
                            TextButton(onClick = { code = ""; editingHtml = false }) { Text("Edit CSS") }
                            TextButton(onClick = { code = ""; editingHtml = true }) { Text("Replace HTML") }
                        }
                        else -> Unit
                    }
                    SelectionContainer {
                        Text(output.ifBlank { if (currentUrl == null) "Load an HTTPS page to inspect it." else "Reading page…" },
                            Modifier.fillMaxWidth().testTag("browser_output"), style = technicalTextStyle())
                    }
                }
            }
        }
    }
    editingHtml?.let { html ->
        AlertDialog(
            onDismissRequest = { editingHtml = null },
            title = { Text(if (html) "Replace selected HTML" else "Set inline CSS") },
            text = {
                OutlinedTextField(code, { code = it.take(8_192) }, label = { Text(if (html) "Replacement HTML" else "CSS declarations") },
                    modifier = Modifier.fillMaxWidth().testTag("browser_element_edit"),
                    keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), minLines = 3, maxLines = 6)
            },
            confirmButton = { TextButton(onClick = { edit(html, code); editingHtml = null }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { editingHtml = null }) { Text("Cancel") } },
        )
    }
}

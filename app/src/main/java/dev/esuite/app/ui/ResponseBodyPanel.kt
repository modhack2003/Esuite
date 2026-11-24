package dev.esuite.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.esuite.app.core.CapturedHexPage
import dev.esuite.app.core.Codec
import dev.esuite.app.core.Header
import dev.esuite.app.core.StoredBody
import dev.esuite.app.core.canFormatJson
import dev.esuite.app.core.hexPage
import dev.esuite.app.core.utf8Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class BodyView(val label: String) { Captured("Captured"), Json("JSON"), Hex("Hex") }

@Composable
internal fun ResponseBodyPanel(body: StoredBody, headers: List<Header>, previewBytes: Int) {
    var view by remember(body, headers) { mutableStateOf(BodyView.Captured) }
    var expanded by remember(body) { mutableStateOf(false) }
    var hexIndex by remember(body) { mutableIntStateOf(0) }
    val views = remember(body, headers) {
        buildList {
            add(BodyView.Captured)
            if (body.canFormatJson(headers)) add(BodyView.Json)
            if (body.value.isNotEmpty() && body.encoding in setOf("utf8", "base64")) add(BodyView.Hex)
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Response body", style = MaterialTheme.typography.titleSmall)
            if (body.truncated) Text("Capture limit reached. Response body is partial.", color = Amber)
            if (views.size > 1) WorkspaceTabs(views, view, "response_body", BodyView::label) { view = it }
            when (view.takeIf { it in views } ?: BodyView.Captured) {
                BodyView.Captured -> {
                    if (body.encoding == "base64") Text("Stored as Base64", style = MaterialTheme.typography.labelMedium)
                    val preview = remember(body, previewBytes) { utf8Preview(body.value, previewBytes) }
                    BodyText(if (expanded) body.value else preview)
                    if (preview.length < body.value.length) {
                        if (!expanded) Text("Showing a ${previewBytes / 1024} KiB preview.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Show preview" else "Show full captured body")
                        }
                    }
                }
                BodyView.Json -> key(body) { JsonBody(body, previewBytes) }
                BodyView.Hex -> key(body, hexIndex) { HexBody(body, hexIndex, { hexIndex-- }, { hexIndex++ }) }
            }
        }
    }
}

@Composable
private fun BodyText(text: String, wrap: Boolean = true) {
    val horizontal = if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
    Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        if (text.isEmpty()) Text("Empty")
        else SelectionContainer {
            Text(text, style = technicalTextStyle(), softWrap = wrap, modifier = horizontal.testTag("response_body_content"))
        }
    }
}

@Composable
private fun JsonBody(body: StoredBody, previewBytes: Int) {
    val formatted by produceState<Result<Pair<String, Boolean>>?>(null, body, previewBytes) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val full = Codec.formatJson(body.value, compact = false)
                val preview = utf8Preview(full, previewBytes)
                preview to (preview.length < full.length)
            }
        }
    }
    val result = formatted
    if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else result.fold(onSuccess = { (preview, clipped) ->
        if (clipped) Text("Showing the first ${previewBytes / 1024} KiB of formatted text.", style = MaterialTheme.typography.bodySmall)
        BodyText(preview)
    }, onFailure = { Text("Could not format this response as JSON. The captured view is available.", color = MaterialTheme.colorScheme.error) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HexBody(body: StoredBody, index: Int, previous: () -> Unit, next: () -> Unit) {
    val page by produceState<Result<CapturedHexPage>?>(null, body, index) {
        value = withContext(Dispatchers.Default) { runCatching { body.hexPage(index) } }
    }
    val result = page
    if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else result.fold(onSuccess = { view ->
        val source = if (body.encoding == "base64") "captured bytes" else "bytes of the displayed text in UTF-8"
        if (view.total > 0) Text("Bytes ${view.start + 1}–${view.end} of ${view.total} $source", style = MaterialTheme.typography.bodySmall)
        BodyText(view.text, wrap = false)
        if (view.start > 0 || view.end < view.total) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = previous, enabled = view.start > 0) { Text("Previous bytes") }
            TextButton(onClick = next, enabled = view.end < view.total) { Text("Next bytes") }
        }
    }, onFailure = { Text("Could not display this capture as hex.", color = MaterialTheme.colorScheme.error) })
}

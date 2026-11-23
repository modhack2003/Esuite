package dev.esuite.app.browser

import android.webkit.ConsoleMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.esuite.app.ui.TechnicalKeyboard
import dev.esuite.app.ui.technicalTextStyle
import kotlinx.serialization.json.JsonPrimitive

internal enum class ConsoleLevel(val label: String) {
    Error("Error"), Warning("Warning"), Info("Info"), Log("Log"), Debug("Debug"), Command("Input"), Result("Result");

    companion object {
        fun from(message: ConsoleMessage.MessageLevel): ConsoleLevel = when (message) {
            ConsoleMessage.MessageLevel.ERROR -> Error
            ConsoleMessage.MessageLevel.WARNING -> Warning
            ConsoleMessage.MessageLevel.DEBUG -> Debug
            ConsoleMessage.MessageLevel.TIP -> Info
            ConsoleMessage.MessageLevel.LOG -> Log
        }
    }
}

internal data class ConsoleEntry(val id: Long, val level: ConsoleLevel, val text: String, val source: String = "")

/** Runs an explicit command in the active document. Results are transient and never bridged to native APIs. */
internal object ConsoleJavaScript {
    const val INPUT_LIMIT = 8_192
    const val OUTPUT_LIMIT = 4_000

    fun start(origin: String, token: String, input: String, script: Boolean): String {
        val body = if (script) input else "return (\n${input.trim().removeSuffix(";")}\n);"
        return """
            (() => {
              if (location.origin !== ${quote(origin)}) return 'context changed';
              const key = ${quote(token)};
              const box = {value: null};
              window[key] = box;
              setTimeout(() => { if (window[key] === box) delete window[key]; }, 12000);
              const finish = (kind, value) => {
                let text;
                try {
                  if (typeof value === 'string') text = value;
                  else if (value instanceof Error) text = String(value);
                  else if (typeof value === 'object' && value !== null) {
                    text = JSON.stringify(value, null, 2);
                    if (text === undefined) text = String(value);
                  } else text = String(value);
                } catch (_) { text = Object.prototype.toString.call(value); }
                box.value = kind + (text.length > $OUTPUT_LIMIT ? text.slice(0, $OUTPUT_LIMIT) + '…' : text);
              };
              try {
                const value = (async function() {
                  $body
                }).call(window);
                Promise.resolve(value).then(value => finish('R', value), error => finish('E', error));
              } catch (error) { finish('E', error); }
              return 'started';
            })()
        """.trimIndent()
    }

    fun take(token: String): String = """
        (() => {
          const key = ${quote(token)};
          const box = window[key];
          if (!box || box.value === null) return null;
          delete window[key];
          return box.value;
        })()
    """.trimIndent()

    private fun quote(value: String): String = JsonPrimitive(value).toString()
}

@Composable
internal fun ConsolePanel(
    entries: List<ConsoleEntry>,
    draft: String,
    onDraft: (String) -> Unit,
    script: Boolean,
    onScript: (Boolean) -> Unit,
    ready: Boolean,
    running: Boolean,
    run: () -> Unit,
    clear: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var issuesOnly by remember { mutableStateOf(false) }
    val rows = if (issuesOnly) entries.filter { it.level == ConsoleLevel.Warning || it.level == ConsoleLevel.Error } else entries
    val list = rememberLazyListState()
    var lastSeen by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(rows.lastOrNull()?.id, issuesOnly) {
        val lastVisible = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (rows.isNotEmpty() && (lastSeen == -1L || lastVisible >= rows.lastIndex - 2)) list.scrollToItem(rows.lastIndex)
        lastSeen = rows.lastOrNull()?.id ?: -1L
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onScript(!script) }, enabled = !running, modifier = Modifier.testTag("console_mode")) {
                Text(if (script) "Script ▾" else "Expression ▾")
            }
            TextButton(onClick = { issuesOnly = !issuesOnly }, modifier = Modifier.weight(1f).testTag("console_levels")) {
                Text(if (issuesOnly) "Issues only" else "All levels", maxLines = 1)
            }
            TextButton(onClick = clear, enabled = entries.isNotEmpty(), modifier = Modifier.testTag("console_clear")) { Text("Clear") }
        }
        HorizontalDivider(color = colors.outlineVariant)
        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("console_messages").semantics {
                val latest = rows.lastOrNull { it.level == ConsoleLevel.Result || it.level == ConsoleLevel.Error } ?: rows.lastOrNull()
                if (latest != null) contentDescription = "Console messages. Latest ${latest.level.label.lowercase()}: ${latest.text.take(200)}"
                if (latest?.level == ConsoleLevel.Result || latest?.level == ConsoleLevel.Error) liveRegion = LiveRegionMode.Polite
            }) {
                if (rows.isEmpty()) item {
                    Text(if (issuesOnly) "No warnings or errors." else if (ready) "No console messages yet." else "Load an HTTPS page to use the console.",
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                items(rows, key = ConsoleEntry::id) { entry -> ConsoleLine(entry) }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            Modifier.fillMaxWidth().background(colors.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tooLong = draft.length > ConsoleJavaScript.INPUT_LIMIT
            val canRun = ready && !running && !tooLong && draft.isNotBlank()
            OutlinedTextField(
                value = draft,
                onValueChange = { onDraft(it.take(ConsoleJavaScript.INPUT_LIMIT + 1)) },
                enabled = ready && !running,
                isError = tooLong,
                supportingText = if (tooLong) ({ Text("Limit: ${ConsoleJavaScript.INPUT_LIMIT} characters") }) else null,
                placeholder = { Text(if (script) "JavaScript statements" else "JavaScript expression", maxLines = 1) },
                keyboardOptions = TechnicalKeyboard,
                textStyle = technicalTextStyle(),
                minLines = 1,
                maxLines = if (script) 3 else 2,
                modifier = Modifier.weight(1f).testTag("console_input").onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && (it.isCtrlPressed || it.isMetaPressed) && canRun) {
                        run()
                        true
                    } else false
                },
            )
            Button(onClick = run, enabled = canRun, modifier = Modifier.testTag("console_run"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text(if (running) "Running" else "Run")
            }
        }
    }
}

@Composable
private fun ConsoleLine(entry: ConsoleEntry) {
    val colors = MaterialTheme.colorScheme
    val accent = when (entry.level) {
        ConsoleLevel.Error -> colors.error
        ConsoleLevel.Warning -> colors.tertiary
        ConsoleLevel.Info, ConsoleLevel.Command, ConsoleLevel.Result -> colors.primary
        ConsoleLevel.Log, ConsoleLevel.Debug -> colors.onSurfaceVariant
    }
    val highlighted = entry.level == ConsoleLevel.Error || entry.level == ConsoleLevel.Warning
    Column(
        Modifier.fillMaxWidth().background(if (highlighted) accent.copy(alpha = 0.09f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(listOf(entry.level.label, entry.source).filter(String::isNotEmpty).joinToString(" · "),
            color = accent, style = MaterialTheme.typography.labelSmall)
        Text(entry.text, color = if (highlighted) accent else colors.onSurface, style = technicalTextStyle(),
            modifier = Modifier.testTag("console_${entry.level.name.lowercase()}"))
    }
    HorizontalDivider(color = colors.outlineVariant)
}

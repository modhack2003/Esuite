package dev.esuite.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class LocalReportDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable
fun WorkspaceControls(state: AppState, model: AppViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var export by remember { mutableStateOf("") }; var preview by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }; var erase by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(LocalReportDocument()) { uri ->
        if (uri != null) {
            val allowed = setOf("com.android.externalstorage.documents", "com.android.providers.downloads.documents", "com.android.providers.media.documents")
            if (uri.authority !in allowed) model.message("Choose device storage or Downloads. Remote and unrecognized document providers are not used for export.")
            else if (export.isBlank()) model.message("Prepare the export again; no data was written.")
            else {
                val payload = export
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload.toByteArray(Charsets.UTF_8)) } ?: error("File could not be opened") } }
                        .onSuccess { model.message("Sanitized report exported to selected device storage") }
                        .onFailure { model.message("Could not write report: ${it.message}") }
                    export = ""
                }
            }
        }
    }
    Text("Local data controls", style = MaterialTheme.typography.titleMedium)
    Text("Encrypted project history is autosaved after a short debounce; Save now flushes it explicitly. Browser profile data is managed separately by Android WebView.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = model::saveNow, modifier = Modifier.fillMaxWidth()) { Text("Save encrypted workspace now") }
    OutlinedButton(onClick = { export = model.exportSanitized(); preview = true }, enabled = state.projectId != null, modifier = Modifier.fillMaxWidth()) { Text("Preview sanitized local export") }
    OutlinedButton(onClick = { delete = true }, enabled = state.projectId != null, modifier = Modifier.fillMaxWidth()) { Text("Delete selected project and browser profiles") }
    OutlinedButton(onClick = { erase = true }, modifier = Modifier.fillMaxWidth()) { Text("Erase encrypted workspace") }
    if (preview) AlertDialog(onDismissRequest = { preview = false; export = "" }, title = { Text("Review export") }, text = {
        Column {
            Text("Bodies, queries, paths, header values, identity configuration and free-text findings are omitted. The origin and generated IDs remain visible.")
            SelectionContainer { Text(export, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { preview = false; launcher.launch("esuite-sanitized-report.json") }) { Text("Choose local file") } }, dismissButton = { TextButton(onClick = { preview = false; export = "" }) { Text("Cancel") } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete selected project?") }, text = { Text("This removes its saved requests, history, observations, identity configuration and isolated browser profiles. Other projects remain.") },
        confirmButton = { TextButton(onClick = { delete = false; model.deleteProject() }) { Text("Delete project") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
    if (erase) EraseVaultDialog({ erase = false }, model)
}

@Composable
fun EraseVaultDialog(close: () -> Unit, model: AppViewModel) {
    AlertDialog(onDismissRequest = close, title = { Text("Erase encrypted workspace?") }, text = { Text("All encrypted API projects and evidence will be removed and the vault key deleted. WebView browser data is separate; delete individual projects first to clear their browser profiles.") },
        confirmButton = { TextButton(onClick = { close(); model.eraseVault() }) { Text("Erase vault") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

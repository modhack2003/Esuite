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
import androidx.compose.ui.platform.testTag
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
fun LocalReportAction(label: String, filename: String, prepare: () -> String?, onMessage: (String) -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var export by remember { mutableStateOf("") }; var preview by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(LocalReportDocument()) { uri ->
        val payload = export
        export = ""
        if (uri != null) {
            val allowed = setOf("com.android.externalstorage.documents", "com.android.providers.downloads.documents", "com.android.providers.media.documents")
            if (uri.scheme != "content" || uri.authority !in allowed) onMessage("Choose device storage or Downloads to save the report.")
            else if (payload.isBlank()) onMessage("Prepare the export again to save the report.")
            else {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload.toByteArray(Charsets.UTF_8)) } ?: error("File could not be opened") } }
                        .onSuccess { onMessage("Report exported to selected device storage") }
                        .onFailure { onMessage("Could not write report. Check the destination and try again.") }
                }
            }
        }
    }
    OutlinedButton(onClick = {
        val payload = prepare()
        if (payload == null) onMessage("This report is unavailable.")
        else { export = payload; preview = true }
    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (preview) AlertDialog(onDismissRequest = { preview = false; export = "" }, title = { Text("Review export") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("The origin, generated IDs and report metadata appear in the file. Paths, credentials, bodies and free text are omitted.")
            SelectionContainer { Text(export, style = technicalTextStyle()) }
        }
    }, confirmButton = { TextButton(onClick = { preview = false; launcher.launch(filename) }) { Text("Choose local file") } }, dismissButton = { TextButton(onClick = { preview = false; export = "" }) { Text("Cancel") } })
}

@Composable
fun WorkspaceControls(state: AppState, model: AppViewModel) {
    var delete by remember { mutableStateOf(false) }; var erase by remember { mutableStateOf(false) }
    Text("Local data controls", style = MaterialTheme.typography.titleMedium)
    Text("This device holds the workspace key. Erasing app data or losing the device can make the workspace unrecoverable. Export a report to retain its summary outside ESuite.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = model::saveNow, modifier = Modifier.fillMaxWidth()) { Text("Save encrypted workspace now") }
    LocalReportAction("Preview sanitized local export", "esuite-sanitized-report.json", model::exportSanitized, model::message, enabled = state.projectId != null)
    OutlinedButton(onClick = { delete = true }, enabled = state.projectId != null, modifier = Modifier.fillMaxWidth()) { Text("Delete selected project and browser profiles") }
    // Beside the delete control, not only in the top status line: the audit found the deletion
    // outcome was otherwise discoverable only by scrolling back to the top.
    if (state.pendingProfileCleanup.isNotEmpty()) {
        Text("Browser profile cleanup pending: ${state.pendingProfileCleanup.joinToString(", ")}. Android WebView still has ${if (state.pendingProfileCleanup.size == 1) "it" else "them"} loaded in memory, so the profile data is still on the device. Cleanup is retried automatically when ESuite restarts.",
            Modifier.testTag("workspace_profile_cleanup"), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = model::retryProfileCleanup, modifier = Modifier.fillMaxWidth().testTag("retry_profile_cleanup")) { Text("Retry browser profile cleanup") }
    }
    OutlinedButton(onClick = { erase = true }, modifier = Modifier.fillMaxWidth()) { Text("Erase encrypted workspace") }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete selected project?") }, text = { Text("This removes its saved requests, history, observations and identity configuration, and asks Android WebView to delete its isolated browser profiles. A profile the browser still has loaded is removed when ESuite restarts. Other projects remain.") },
        confirmButton = { TextButton(onClick = { delete = false; model.deleteProject() }) { Text("Delete project") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
    if (erase) EraseVaultDialog({ erase = false }, model)
}

@Composable
fun EraseVaultDialog(close: () -> Unit, model: AppViewModel) {
    AlertDialog(onDismissRequest = close, title = { Text("Erase encrypted workspace?") }, text = { Text("All encrypted API projects and evidence will be removed and the vault key deleted. ESuite's own browser profiles are removed in the same step by name prefix, so projects do not have to be deleted individually first; other apps' WebView data and the default profile are untouched. A profile the browser still has loaded is removed when ESuite restarts.") },
        confirmButton = { TextButton(onClick = { close(); model.eraseVault() }) { Text("Erase vault") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

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

private class LocalReportDocument(mimeType: String) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable
fun LocalReportAction(
    label: String,
    filename: String,
    prepare: suspend () -> String?,
    onMessage: (String) -> Unit,
    enabled: Boolean = true,
    mimeType: String = "application/json",
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var export by remember { mutableStateOf("") }; var preview by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(remember(mimeType) { LocalReportDocument(mimeType) }) { uri ->
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
        preparing = true
        scope.launch {
            try {
                val payload = prepare()
                if (payload == null) onMessage("This report is unavailable.")
                else { export = payload; preview = true }
            } finally { preparing = false }
        }
    }, enabled = enabled && !preparing, modifier = Modifier.fillMaxWidth()) {
        Text(if (preparing) "Preparing report" else label)
    }
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
    Text("Saved site data", style = MaterialTheme.typography.titleMedium)
    Text("This device holds the encryption key. Erasing app data or losing the device can make saved records unrecoverable. Export a report to retain its summary outside ESuite.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = model::saveNow, modifier = Modifier.fillMaxWidth()) { Text("Save local data now") }
    LocalReportAction("Preview sanitized local export", "esuite-sanitized-report.json", model::exportSanitized, model::message, enabled = state.projectId != null)
    OutlinedButton(onClick = { delete = true }, enabled = state.projectId != null, modifier = Modifier.fillMaxWidth()) { Text("Remove selected site data") }
    // Beside the delete control, not only in the top status line: the audit found the deletion
    // outcome was otherwise discoverable only by scrolling back to the top.
    if (state.pendingProfileCleanup.isNotEmpty()) {
        Text("Browser profile cleanup pending: ${state.pendingProfileCleanup.joinToString(", ")}. Android WebView still has ${if (state.pendingProfileCleanup.size == 1) "it" else "them"} loaded in memory, so the profile data is still on the device. Cleanup is retried automatically when ESuite restarts.",
            Modifier.testTag("workspace_profile_cleanup"), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = model::retryProfileCleanup, modifier = Modifier.fillMaxWidth().testTag("retry_profile_cleanup")) { Text("Retry browser profile cleanup") }
    }
    OutlinedButton(onClick = { erase = true }, modifier = Modifier.fillMaxWidth()) { Text("Erase saved site data") }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Remove selected site data?") }, text = { Text("This removes its saved requests, history, observations and identity configuration, and asks Android WebView to delete its isolated browser profiles. A profile the browser still has loaded is removed when ESuite restarts. Other sites stay saved.") },
        confirmButton = { TextButton(onClick = { delete = false; model.deleteProject() }) { Text("Remove site") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
    if (erase) EraseVaultDialog({ erase = false }, model)
}

@Composable
fun EraseVaultDialog(close: () -> Unit, model: AppViewModel) {
    AlertDialog(onDismissRequest = close, title = { Text("Erase saved site data?") }, text = { Text("This removes all saved requests, activity, findings and API identity settings. Use Clear browsing data to clear the current browser’s cookies and storage.") },
        confirmButton = { TextButton(onClick = { close(); model.eraseVault() }) { Text("Erase vault") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

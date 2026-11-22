package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.BuildConfig

@Composable
internal fun SettingsScreen(
    state: AppState,
    model: AppViewModel,
    appearance: Appearance,
    motion: Boolean,
    setAppearance: (Appearance) -> Unit,
    setMotion: (Boolean) -> Unit,
    clearBrowser: () -> Unit,
    browserMessage: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val provider = remember { WebViewCompat.getCurrentWebViewPackage(context)?.versionName }
    var confirmClear by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("settings_title"))
        }
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        WorkspaceTabs(Appearance.entries, appearance, "appearance", Appearance::label, setAppearance)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Interface animations", Modifier.weight(1f))
            Switch(checked = motion, onCheckedChange = setMotion, modifier = Modifier.testTag("settings_motion"))
        }
        HorizontalDivider()
        Text("Browsing data", style = MaterialTheme.typography.titleMedium)
        Text("Browser uses the WebView profile belonging to ESuite. Its cookies and storage stay on this device.",
            style = MaterialTheme.typography.bodyMedium, color = Muted)
        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("settings_clear_browser")) { Text("Clear browsing data") }
        browserMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal) }
        HorizontalDivider()
        if (state.ready) {
            WorkspaceControls(state, model)
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = Muted)
        } else {
            Text("Saved site data", style = MaterialTheme.typography.titleMedium)
            Text(state.message.ifBlank { "Opening saved data…" }, style = MaterialTheme.typography.bodyMedium)
            if (!state.openingWorkspace) {
                OutlinedButton(onClick = model::loadWorkspace) { Text("Retry opening saved data") }
                TextButton(onClick = { erase = true }) { Text("Erase saved site data") }
            }
        }
        HorizontalDivider()
        Text("About ESuite", style = MaterialTheme.typography.titleMedium)
        Text("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", Modifier.testTag("settings_version"))
        provider?.let { Text("Android System WebView $it", style = MaterialTheme.typography.bodySmall, color = Muted) }
        Text("Credits", style = MaterialTheme.typography.titleSmall)
        Text("ESuite by Bikram Dey and contributors. MIT license.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { links.openUri("https://github.com/modhack2003/Esuite") }) { Text("Project on GitHub") }
        TextButton(onClick = { links.openUri("https://github.com/modhack2003/Esuite/blob/main/LICENSE") }) { Text("MIT license") }
    }
    if (erase) EraseVaultDialog({ erase = false }, model)
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear browsing data?") },
        text = { Text("This clears cookies, cache, storage and history in ESuite’s browser, including legacy browser profiles.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; clearBrowser() }) { Text("Clear browsing data") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

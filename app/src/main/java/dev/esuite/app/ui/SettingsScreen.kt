package dev.esuite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.esuite.app.AppState
import dev.esuite.app.AppViewModel
import dev.esuite.app.BuildConfig

private enum class SettingsSection(val title: String, val terms: String) {
    Appearance("Appearance", "theme system light dark high contrast animation motion reset"),
    Timestamps("Timestamps", "timezone time zone UTC local dates Activity observations"),
    Browsing("Browsing data", "webview history cookies storage clear browser"),
    Sites("Saved site data", "vault encryption copy export report site save restore remove erase"),
    About("About ESuite", "version build release credits license Inter MIT GitHub support diagnostics webview");

    fun matches(query: String): Boolean = query.trim().split(Regex("\\s+")).all { token ->
        title.contains(token, ignoreCase = true) || terms.contains(token, ignoreCase = true)
    }
}

@Composable
internal fun SettingsScreen(
    state: AppState,
    model: AppViewModel,
    appearance: Appearance,
    motion: Boolean,
    setAppearance: (Appearance) -> Unit,
    setMotion: (Boolean) -> Unit,
    useUtcTimestamps: Boolean,
    setUtcTimestamps: (Boolean) -> Unit,
    clearBrowser: () -> Unit,
    browserMessage: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val provider = remember(context) { WebViewCompat.getCurrentWebViewPackage(context)?.versionName }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    var showInterLicense by remember { mutableStateOf(false) }
    var confirmAppearanceReset by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf(false) }
    val visibleSections = remember(query) {
        SettingsSection.entries.filter {
            it.matches(query) || (it == SettingsSection.About && query.trim() == BuildConfig.VERSION_NAME)
        }
    }
    fun openLink(url: String) {
        linkError = runCatching { links.openUri(url) }.isFailure
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("settings_title").semantics { heading() })
        }
        OutlinedTextField(query, { query = it.take(80) }, label = { Text("Find a setting") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_search"),
            trailingIcon = if (query.isNotEmpty()) ({ TextButton(onClick = { query = "" }) { Text("Clear") } }) else null)
        if (visibleSections.isEmpty()) Text("No settings match.", color = Muted)
        visibleSections.forEach { section ->
            key(section) {
                HorizontalDivider()
                if (section != SettingsSection.Sites || !state.ready) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                when (section) {
                    SettingsSection.Appearance -> {
                        WorkspaceTabs(Appearance.entries, appearance, "appearance", Appearance::label, setAppearance)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Interface animations", Modifier.weight(1f))
                            Switch(checked = motion, onCheckedChange = setMotion,
                                modifier = Modifier.testTag("settings_motion").semantics { contentDescription = "Interface animations" })
                        }
                        TextButton(onClick = { confirmAppearanceReset = true }, enabled = appearance != Appearance.Dark || !motion) {
                            Text("Restore appearance defaults")
                        }
                    }
                    SettingsSection.Timestamps -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Use UTC for timestamps", Modifier.weight(1f))
                            Switch(checked = useUtcTimestamps, onCheckedChange = setUtcTimestamps,
                                modifier = Modifier.testTag("settings_timestamps_utc")
                                    .semantics { contentDescription = "Use UTC for timestamps" })
                        }
                        Text(if (useUtcTimestamps) "Observations display UTC." else "Observations display the device’s local time.",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    SettingsSection.Browsing -> {
                        Text("Browser uses the WebView profile belonging to ESuite. Its cookies and storage stay on this device.",
                            style = MaterialTheme.typography.bodyMedium, color = Muted)
                        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("settings_clear_browser")) { Text("Clear browsing data") }
                        browserMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signal) }
                    }
                    SettingsSection.Sites -> {
                        if (state.ready) {
                            WorkspaceControls(state, model)
                            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = Muted)
                        } else {
                            Text(state.message.ifBlank { "Opening saved data…" }, style = MaterialTheme.typography.bodyMedium)
                            if (!state.openingWorkspace) {
                                OutlinedButton(onClick = model::loadWorkspace) { Text("Retry opening saved data") }
                                TextButton(onClick = { erase = true }) { Text("Erase saved site data") }
                            }
                        }
                    }
                    SettingsSection.About -> {
                        Text("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", Modifier.testTag("settings_version"))
                        provider?.let { Text("Android System WebView $it", style = MaterialTheme.typography.bodySmall, color = Muted) }
                        OutlinedButton(onClick = { diagnostics = true }) { Text("View local diagnostics") }
                        Text("Credits", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        Text("ESuite by Bikram Dey and contributors. MIT license.", style = MaterialTheme.typography.bodyMedium)
                        Text("Inter typeface by the Inter Project Authors. SIL Open Font License.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        TextButton(onClick = { openLink("https://github.com/modhack2003/Esuite/releases") }) { Text("Release notes on GitHub") }
                        TextButton(onClick = { openLink("https://github.com/modhack2003/Esuite") }) { Text("Project on GitHub") }
                        TextButton(onClick = { openLink("https://github.com/modhack2003/Esuite/blob/main/LICENSE") }) { Text("MIT license") }
                        TextButton(onClick = { showInterLicense = true }) { Text("Inter license") }
                        if (linkError) Text("No app could open the link.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (confirmAppearanceReset) AlertDialog(
        onDismissRequest = { confirmAppearanceReset = false },
        title = { Text("Restore appearance defaults?") },
        text = { Text("ESuite will use the dark theme and enable interface animations.") },
        confirmButton = {
            TextButton(onClick = { confirmAppearanceReset = false; setAppearance(Appearance.Dark); setMotion(true) }) {
                Text("Restore defaults")
            }
        },
        dismissButton = { TextButton(onClick = { confirmAppearanceReset = false }) { Text("Cancel") } },
    )
    if (diagnostics) {
        val profiles = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
        val lines = listOf(
            "ESuite ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
            "Android API ${android.os.Build.VERSION.SDK_INT}",
            "WebView ${provider ?: "unavailable"}",
            "Multiple WebView profiles · ${if (profiles) "Available" else "Unavailable"}",
        )
        AlertDialog(onDismissRequest = { diagnostics = false }, title = { Text("Local diagnostics") },
            text = { SelectionContainer { Text(lines.joinToString("\n"), style = technicalTextStyle()) } },
            confirmButton = { TextButton(onClick = { diagnostics = false }) { Text("Close") } })
    }
    if (showInterLicense) {
        val license = remember { context.resources.openRawResource(dev.esuite.app.R.raw.inter_ofl).bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showInterLicense = false }, title = { Text("Inter font license") },
            text = { Text(license, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { showInterLicense = false }) { Text("Close") } })
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

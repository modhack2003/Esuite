package dev.esuite.app

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.esuite.app.browser.BrowserProfiles
import dev.esuite.app.browser.BrowserScreen
import dev.esuite.app.browser.BrowserSkeleton
import dev.esuite.app.core.*
import dev.esuite.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Activity-scoped ViewModel shared with Compose (same ViewModelStore), so onStop can flush it.
    private val model: AppViewModel by viewModels()
    private var developerToolsShortcut: (() -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val shortcut = developerToolsShortcut
        if (keyCode == KeyEvent.KEYCODE_F12 && shortcut != null) {
            if (event.repeatCount == 0) shortcut()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WebView.setWebContentsDebuggingEnabled(false)
        val preferences = getSharedPreferences("appearance", Context.MODE_PRIVATE)
        setContent {
            var appearance by remember {
                mutableStateOf(Appearance.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: Appearance.Dark)
            }
            var motion by remember { mutableStateOf(preferences.getBoolean("motion", true)) }
            val dark = isDark(appearance)
            SideEffect {
                val color = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(color) else SystemBarStyle.light(color, color)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ESuiteTheme(appearance) {
                ESuiteApp(model, appearance, motion,
                    onAppearance = { appearance = it; preferences.edit().putString("theme", it.name).apply() },
                    onMotion = { motion = it; preferences.edit().putBoolean("motion", it).apply() },
                    onBrowserShortcut = { developerToolsShortcut = it })
            }
        }
    }

    // Best-effort background save. Only the committed-snapshot indicator acknowledges durability.
    // flush() is a safe no-op when the workspace is not ready and never throws into this callback.
    override fun onStop() {
        model.flush()
        super.onStop()
    }
}

enum class Screen(val label: String, val icon: ImageVector) {
    Browser("Browser", Icons.Outlined.Search),
    Traffic("Activity", Icons.AutoMirrored.Outlined.List),
    Api("API Lab", Icons.AutoMirrored.Outlined.Send),
    Findings("Findings", Icons.Outlined.CheckCircle),
    Tools("Tools", Icons.Outlined.Build),
    Settings("Settings", Icons.Outlined.Settings),
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun ESuiteApp(
    model: AppViewModel = viewModel(),
    appearance: Appearance = Appearance.Dark,
    motion: Boolean = true,
    onAppearance: (Appearance) -> Unit = {},
    onMotion: (Boolean) -> Unit = {},
    onBrowserShortcut: ((() -> Unit)?) -> Unit = {},
) {
    val state by model.state.collectAsStateWithLifecycle()
    var selectedName by rememberSaveable { mutableStateOf(Screen.Browser.name) }
    var previousName by rememberSaveable { mutableStateOf(Screen.Browser.name) }
    var clearRequest by remember { mutableIntStateOf(0) }
    var starting by remember { mutableStateOf(true) }
    var browserMessage by remember { mutableStateOf<String?>(null) }
    val screen = Screen.entries.firstOrNull { it.name == selectedName } ?: Screen.Browser
    val browserVisible = screen == Screen.Browser || screen == Screen.Tools
    val site = state.workspace.projects.firstOrNull { it.id == state.projectId }
    fun navigate(next: Screen) { selectedName = next.name }
    fun settings() { previousName = screen.name; navigate(Screen.Settings) }
    fun closeSettings() { selectedName = previousName }

    BackHandler(enabled = !browserVisible) {
        if (screen == Screen.Settings) closeSettings() else navigate(Screen.Browser)
    }
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val compact = with(density) { windowSize.height.toDp() < 450.dp }
    val displayLabels = !compact && density.fontScale <= 1.3f && with(density) { windowSize.width.toDp() >= 320.dp }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            containerColor = Midnight,
            contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
            bottomBar = {
                if (!starting && !imeVisible && screen != Screen.Settings) PrimaryNavigation(screen, compact, displayLabels) {
                    navigate(if (it == Screen.Tools && screen == Screen.Tools) Screen.Browser else it)
                }
            },
        ) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).imePadding()) {
                state.manualRequestLabel?.let { label ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sending · $label", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = model::cancelRequest, modifier = Modifier.testTag("cancel_running_request")) { Text("Cancel") }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    BrowserScreen(
                        visible = browserVisible,
                        motion = motion,
                        clearRequest = clearRequest,
                        utilitiesVisible = screen == Screen.Tools,
                        closeUtilities = { navigate(Screen.Browser) },
                        onReady = { starting = false },
                        onShortcut = onBrowserShortcut,
                        onSettings = ::settings,
                        onDataCleared = { cleared ->
                            val cleanup = BrowserProfiles.clearAllEsuiteOwned()
                            browserMessage = when {
                                !cleared -> "Browser data couldn’t be cleared. Check Android System WebView."
                                cleanup.pending.isNotEmpty() -> "Browsing data cleared. Restart ESuite to finish removing legacy profiles."
                                else -> "Browsing data cleared."
                            }
                        },
                        onOpenApi = {
                            model.openSite(it)
                            navigate(Screen.Api)
                        },
                        utilities = { ToolsScreen(state, model) },
                    )
                    if (!browserVisible) {
                        AnimatedContent(
                            targetState = screen,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                if (motion) fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                                else EnterTransition.None togetherWith ExitTransition.None
                            },
                            label = "Primary navigation",
                        ) { destination ->
                            Surface(Modifier.fillMaxSize(), color = Midnight) {
                                when {
                                    destination == Screen.Settings -> SettingsScreen(state, model, appearance, motion,
                                        onAppearance, onMotion, { browserMessage = "Clearing browsing data…"; clearRequest++ },
                                        browserMessage, ::closeSettings)
                                    !state.ready -> SavedDataState(state, ::settings)
                                    destination == Screen.Findings -> key(state.projectId) {
                                        FindingsScreen(state, model, site) { navigate(Screen.Api) }
                                    }
                                    destination == Screen.Traffic -> key(state.projectId) {
                                        TrafficScreen(state, model, site) { navigate(Screen.Api) }
                                    }
                                    else -> {
                                        val pageScroll = key(destination, state.projectId) { rememberScrollState() }
                                        val scope = rememberCoroutineScope()
                                        Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            WorkspaceStatus(site, state)
                                            SitePicker(state, site, model, allowNew = true)
                                            if (site != null) ApiScreen(state, model) { scope.launch { pageScroll.scrollTo(0) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(starting, enter = EnterTransition.None,
            exit = if (motion) fadeOut(tween(160)) else ExitTransition.None) { LaunchScreen() }
    }
}

@Composable
private fun LaunchScreen() {
    Surface(Modifier.fillMaxSize().testTag("app_launch"), color = Midnight) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.esuite_splash_mark), null, Modifier.size(88.dp), tint = Color.Unspecified)
                Text("ESuite", style = MaterialTheme.typography.headlineMedium)
                Text("Starting browser", style = MaterialTheme.typography.bodySmall, color = Muted)
                LinearProgressIndicator(Modifier.width(112.dp).padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun SavedDataState(state: AppState, settings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        if (state.openingWorkspace) BrowserSkeleton("Opening saved data")
        else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Saved site data couldn’t open", style = MaterialTheme.typography.titleLarge)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = settings) { Text("Open settings") }
        }
    }
}

@Composable private fun PrimaryNavigation(screen: Screen, compact: Boolean, displayLabels: Boolean, navigate: (Screen) -> Unit) {
    val destinations = Screen.entries.filter { it != Screen.Settings }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp).selectableGroup()) {
                destinations.forEach { target ->
                    val selected = screen == target
                    Column(Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 55.dp)
                        .testTag("nav_${target.name.lowercase()}")
                        .selectable(selected, role = Role.Tab, onClick = { navigate(target) }),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(42.dp, 28.dp).background(if (selected) colors.primaryContainer else Color.Transparent,
                            MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                            Icon(target.icon, contentDescription = if (displayLabels) null else target.label, modifier = Modifier.size(19.dp),
                                tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                        }
                        if (displayLabels) Text(target.label, color = if (selected) Signal else Muted,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable internal fun IdentityControls(state: AppState, model: AppViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("A", "B").forEach { identity ->
            FilterChip(modifier = Modifier.testTag("identity_$identity"), selected = state.identity == identity,
                onClick = { model.identity(identity) }, label = { Text("Identity $identity") })
        }
    }
}

@Composable internal fun WorkspaceStatus(project: Project?, state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(project?.let { "${it.origin} · identity ${state.identity}" } ?: "No site selected", Modifier.testTag("workspace_context"),
            color = Signal, style = MaterialTheme.typography.bodySmall)
        if (state.message.isNotBlank()) Text(state.message, Modifier.testTag("workspace_status").semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(state.saveError ?: if (state.pendingWrites == 0 && state.persistedWorkspace == state.workspace) "Saved on this device" else "Saving local data…",
            Modifier.testTag("workspace_save_status"), style = MaterialTheme.typography.bodySmall, color = if (state.saveError == null) Muted else Amber)
    }
}

@Composable fun Panel(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Raised)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(body, color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable private fun TrafficScreen(state: AppState, model: AppViewModel, project: Project?, openApi: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Exchange?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val history = remember(state.workspace.exchanges, state.projectId) {
        state.workspace.exchanges.filter { it.projectId == state.projectId }
    }
    val entries = remember(history, filter) {
        val query = filter.trim()
        if (query.isEmpty()) history else history.filter { exchange ->
            "${exchange.request.method} ${exchange.request.url} ${exchange.source}".contains(query, ignoreCase = true)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { WorkspaceStatus(project, state) }
        item { Text("Activity", style = MaterialTheme.typography.headlineSmall) }
        item { SitePicker(state, project, model) }
        item {
            OutlinedTextField(filter, { filter = it }, singleLine = true, keyboardOptions = TechnicalKeyboard,
                label = { Text("Filter method, URL or source") }, modifier = Modifier.fillMaxWidth())
        }
        if (entries.isEmpty()) item { Text("No matching history. Browse an in-scope page or send an API request.") }
        items(entries, key = { it.id }) { exchange ->
            OutlinedButton(onClick = { model.select(exchange); detail = exchange }, modifier = Modifier.fillMaxWidth().testTag("traffic_exchange")) {
                Text("${exchange.request.method} ${exchange.request.url}\n${exchange.source} · identity ${exchange.identity} · ${exchange.status ?: if (exchange.error != null) "error" else "metadata only"}", Modifier.fillMaxWidth())
            }
        }
        if (history.isNotEmpty()) item { OutlinedButton(onClick = { confirmClear = true }) { Text("Clear site history") } }
    }
    detail?.let { exchange ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text("Exchange detail", Modifier.testTag("traffic_detail")) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ResultAttribution.label(exchange, project?.origin))
                Text(exchange.completeness)
                exchange.error?.let { Text(it) }
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${exchange.request.method} ${exchange.request.url}", style = technicalTextStyle())
                        if (exchange.request.headers.isNotEmpty()) Text(exchange.request.headers.joinToString("\n") { "${it.name}: ${it.value}" }, style = technicalTextStyle())
                        if (exchange.request.body.isNotEmpty()) Text(exchange.request.body, style = technicalTextStyle())
                        if (exchange.status != null) Text("HTTP ${exchange.status} · ${exchange.durationMs} ms")
                        if (exchange.responseHeaders.isNotEmpty()) Text(exchange.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" }, style = technicalTextStyle())
                        if (exchange.response.value.isNotEmpty()) {
                            Text("Response body · ${exchange.response.encoding}")
                            Text(exchange.response.value, style = technicalTextStyle())
                        }
                        if (exchange.response.truncated) Text("Response body truncated", color = Amber)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { detail = null; model.replay(exchange); openApi() }) { Text("Review in API Lab") } },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("Close") } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear site history?") },
        text = { Text("This removes history entries for the selected site. Evidence saved with observations stays on this device.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; model.clearHistory() }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

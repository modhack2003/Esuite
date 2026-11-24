package dev.esuite.app

import android.os.Bundle
import android.view.KeyEvent
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.content.edit
import dev.esuite.app.browser.BrowserProfiles
import dev.esuite.app.browser.BrowserLogSnapshot
import dev.esuite.app.browser.BrowserScreen
import dev.esuite.app.browser.BrowserShortcut
import dev.esuite.app.browser.BrowserSkeleton
import dev.esuite.app.core.*
import dev.esuite.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Activity-scoped ViewModel shared with Compose (same ViewModelStore), so onStop can flush it.
    private val model: AppViewModel by viewModels()
    private var browserShortcut: ((BrowserShortcut) -> Unit)? = null
    private var commandShortcut: (() -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val shortcut = browserShortcut
        if (keyCode == KeyEvent.KEYCODE_F12 && shortcut != null) {
            if (event.repeatCount == 0) shortcut(BrowserShortcut.Tools)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if ((event.isCtrlPressed || event.isMetaPressed) && !event.isAltPressed) {
            val commands = commandShortcut
            if (keyCode == KeyEvent.KEYCODE_K && commands != null) {
                if (event.repeatCount == 0) commands()
                return true
            }
            val browser = browserShortcut
            val shortcut = when (keyCode) {
                KeyEvent.KEYCODE_L -> BrowserShortcut.Address
                KeyEvent.KEYCODE_F -> BrowserShortcut.Find
                else -> null
            }
            if (!event.isShiftPressed && browser != null && shortcut != null) {
                if (event.repeatCount == 0) browser(shortcut)
                return true
            }
        }
        return super.onKeyShortcut(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val preferences = getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val navigation = getSharedPreferences("navigation", Context.MODE_PRIVATE)
        val initialScreen = Screen.entries.firstOrNull {
            navigation.getBoolean("resume_tab", false) && it != Screen.Settings && it.name == navigation.getString("last_tab", null)
        } ?: Screen.Browser
        setContent {
            var appearance by remember {
                mutableStateOf(Appearance.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: Appearance.Dark)
            }
            var motion by remember { mutableStateOf(preferences.getBoolean("motion", true)) }
            var useUtc by remember { mutableStateOf(preferences.getBoolean("timestamps_utc", false)) }
            var resumeTab by remember { mutableStateOf(navigation.getBoolean("resume_tab", false)) }
            val dark = isDark(appearance)
            SideEffect {
                val color = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(color) else SystemBarStyle.light(color, color)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ESuiteTheme(appearance) {
                ObservationDates(useUtc) {
                    ESuiteApp(model, appearance, motion,
                        onAppearance = { appearance = it; preferences.edit { putString("theme", it.name) } },
                        onMotion = { motion = it; preferences.edit { putBoolean("motion", it) } },
                        useUtcTimestamps = useUtc,
                        onUtcTimestamps = { useUtc = it; preferences.edit { putBoolean("timestamps_utc", it) } },
                        initialScreen = initialScreen,
                        resumeTab = resumeTab,
                        onResumeTab = { enabled, screen ->
                            resumeTab = enabled
                            navigation.edit {
                                putBoolean("resume_tab", enabled)
                                if (enabled) putString("last_tab", screen.name) else remove("last_tab")
                            }
                        },
                        onPrimaryScreen = { screen ->
                            if (resumeTab) navigation.edit { putString("last_tab", screen.name) }
                        },
                        onBrowserShortcut = { browserShortcut = it },
                        onCommandShortcut = { commandShortcut = it })
                }
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

private enum class AppCommand(val label: String, val aliases: String, val screen: Screen? = null) {
    Browser("Open Browser", "/browser webpage navigate", Screen.Browser),
    Find("Find in page", "/find page text search matches", Screen.Browser),
    Activity("Open Activity", "/activity traffic history sitemap", Screen.Traffic),
    Requests("Open API Lab", "/api request headers", Screen.Api),
    Findings("Open Findings", "/findings observations evidence", Screen.Findings),
    Tools("Open Tools", "/tools encode import review", Screen.Tools),
    Settings("Open Settings", "/settings appearance version help", Screen.Settings),
    Review("Review latest response", "/review passive checks response headers"),
}

private val appCommandIds = AppCommand.entries.map { it.name }.toSet()

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun ESuiteApp(
    model: AppViewModel = viewModel(),
    appearance: Appearance = Appearance.Dark,
    motion: Boolean = true,
    onAppearance: (Appearance) -> Unit = {},
    onMotion: (Boolean) -> Unit = {},
    useUtcTimestamps: Boolean = false,
    onUtcTimestamps: (Boolean) -> Unit = {},
    initialScreen: Screen = Screen.Browser,
    resumeTab: Boolean = false,
    onResumeTab: (Boolean, Screen) -> Unit = { _, _ -> },
    onPrimaryScreen: (Screen) -> Unit = {},
    onBrowserShortcut: (((BrowserShortcut) -> Unit)?) -> Unit = {},
    onCommandShortcut: ((() -> Unit)?) -> Unit = {},
) {
    val state by model.state.collectAsStateWithLifecycle()
    val primaryState = key(state.projectId) { rememberSaveableStateHolder() }
    var selectedName by rememberSaveable { mutableStateOf(initialScreen.name) }
    var previousName by rememberSaveable { mutableStateOf(initialScreen.name) }
    var requestRouteId by rememberSaveable { mutableIntStateOf(0) }
    var requestReturnName by rememberSaveable { mutableStateOf<String?>(null) }
    var findInPageRequest by remember { mutableIntStateOf(0) }
    var clearRequest by remember { mutableIntStateOf(0) }
    var starting by remember { mutableStateOf(true) }
    var browserMessage by remember { mutableStateOf<String?>(null) }
    var browserImport by remember { mutableStateOf<BrowserLogSnapshot?>(null) }
    var browserImportError by remember { mutableStateOf("") }
    var review by remember { mutableStateOf<ReviewLaunch?>(null) }
    var finding by remember { mutableStateOf<FindingSeed?>(null) }
    var commands by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val commandPreferences = remember(context) { context.applicationContext.getSharedPreferences("app_commands", Context.MODE_PRIVATE) }
    var commandUsage by remember(commandPreferences) {
        mutableStateOf(CommandUsage.restore(commandPreferences.getString("pinned", "").orEmpty(),
            commandPreferences.getString("recent", "").orEmpty(), appCommandIds))
    }
    val screen = Screen.entries.firstOrNull { it.name == selectedName } ?: initialScreen
    val settingsOrigin = Screen.entries.firstOrNull { it.name == previousName && it != Screen.Settings } ?: Screen.Browser
    val requestOrigin = Screen.entries.firstOrNull { it.name == requestReturnName && it != Screen.Settings }
    val browserVisible = screen == Screen.Browser || screen == Screen.Tools
    val site = state.workspace.projects.firstOrNull { it.id == state.projectId }
    fun saveCommandUsage(next: CommandUsage) {
        commandUsage = next
        commandPreferences.edit {
            putString("pinned", next.pinned.joinToString(","))
            putString("recent", next.recent.joinToString(","))
        }
    }
    fun navigate(next: Screen) {
        if (next == screen) return
        if (screen == Screen.Api && next != Screen.Settings && next != Screen.Api) requestReturnName = null
        selectedName = next.name
        if (next != Screen.Settings) onPrimaryScreen(next)
    }
    fun openApiRequest() {
        requestRouteId++
        requestReturnName = screen.name.takeUnless { screen == Screen.Api }
        navigate(Screen.Api)
    }
    fun settings() { if (screen != Screen.Settings) { previousName = screen.name; navigate(Screen.Settings) } }
    fun closeSettings() { selectedName = settingsOrigin.name }
    fun openReview(exchanges: List<Exchange>, template: ResponseCheck) {
        val target = model.currentProject() ?: return
        val current = model.state.value.workspace
        val chosen = exchanges.distinctBy(Exchange::id)
        val scope = ScopePolicy(target.origin)
        if (chosen.size !in 1..MAX_REVIEW_EXCHANGES || chosen.any { it.projectId != target.id || !current.containsEvidence(it) || !scope.allows(it.request.url) }) {
            model.message("Choose current exchanges from this site.")
            return
        }
        review = ReviewLaunch(target, model.currentContext(), chosen, template)
    }
    fun openFinding(exchange: Exchange?) {
        if (exchange == null) navigate(Screen.Findings)
        else model.currentProject()?.let { target ->
            finding = FindingSeed(target, model.currentContext(), exchange)
        }
    }

    val openCommands by rememberUpdatedState {
        if (!starting && browserImport == null && review == null && finding == null) commands = true
    }
    DisposableEffect(Unit) {
        onCommandShortcut { openCommands() }
        onDispose { onCommandShortcut(null) }
    }

    BackHandler(enabled = !browserVisible) {
        when {
            screen == Screen.Settings -> closeSettings()
            screen == Screen.Api && requestOrigin != null -> navigate(requestOrigin)
            else -> navigate(Screen.Browser)
        }
    }
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(density) { windowSize.width.toDp() }
    val windowHeight = with(density) { windowSize.height.toDp() }
    val compact = windowHeight < 450.dp
    val useRail = windowWidth >= 840.dp || (windowWidth >= 680.dp && windowWidth > windowHeight)
    val displayLabels = density.fontScale <= 1.3f && (useRail || (!compact && windowWidth >= 320.dp))
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val showNavigation = !starting && !imeVisible && screen != Screen.Settings
    fun selectTab(next: Screen) {
        navigate(if (next == Screen.Tools && screen == Screen.Tools) Screen.Browser else next)
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            containerColor = Midnight,
            contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
            bottomBar = {
                if (showNavigation && !useRail) PrimaryNavigation(screen, compact, displayLabels, false, ::selectTab)
            },
        ) { inset ->
            Row(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).imePadding()) {
                if (showNavigation && useRail) PrimaryNavigation(screen, compact, displayLabels, true, ::selectTab)
                Column(Modifier.weight(1f).fillMaxHeight()) {
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
                            findInPageRequest = findInPageRequest,
                            utilitiesVisible = screen == Screen.Tools,
                            closeUtilities = { navigate(Screen.Browser) },
                            onReady = { starting = false },
                            onShortcut = onBrowserShortcut,
                            onSettings = ::settings,
                            onCommands = openCommands,
                            onDataCleared = { cleared ->
                                model.clearBrowserActivity()
                                val cleanup = BrowserProfiles.clearAllEsuiteOwned()
                                browserMessage = when {
                                    !cleared -> "Browser data couldn’t be cleared. Check Android System WebView."
                                    cleanup.pending.isNotEmpty() -> "Browsing data cleared. Restart ESuite to finish removing legacy profiles."
                                    else -> "Browsing data cleared."
                                }
                            },
                            onOpenApi = { if (model.openSite(it)) openApiRequest() },
                            onOpenActivity = { browserImportError = ""; browserImport = it },
                            utilities = {
                                primaryState.SaveableStateProvider(Screen.Tools.name) {
                                    ToolsScreen(state, model) { exchange, template -> openReview(listOf(exchange), template) }
                                }
                            },
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
                                primaryState.SaveableStateProvider(destination.name) {
                                    Surface(Modifier.fillMaxSize(), color = Midnight) {
                                        when {
                                            destination == Screen.Settings -> SettingsScreen(state, model, appearance, motion,
                                                onAppearance, onMotion, useUtcTimestamps, onUtcTimestamps,
                                                resumeTab, { onResumeTab(it, settingsOrigin) },
                                                { browserMessage = "Clearing browsing data…"; clearRequest++ },
                                                browserMessage, ::closeSettings)
                                            !state.ready -> SavedDataState(state, ::settings)
                                            destination == Screen.Findings -> key(state.projectId) {
                                                FindingsScreen(state, model, site, ::openApiRequest) { openReview(listOf(it), ResponseCheck.Quick) }
                                            }
                                            destination == Screen.Traffic -> key(state.projectId) {
                                                ActivityScreen(state, model, site, ::openApiRequest, ::openReview, ::openFinding)
                                            }
                                            else -> key(requestRouteId, state.projectId) {
                                                val pageScroll = rememberScrollState()
                                                val scope = rememberCoroutineScope()
                                                Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    requestOrigin?.let { origin ->
                                                        TextButton(onClick = { navigate(origin) }, modifier = Modifier.testTag("api_return_to_origin")) {
                                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Return to ${origin.label}")
                                                        }
                                                    }
                                                    WorkspaceStatus(site, state)
                                                    SitePicker(state, site, model, allowNew = true)
                                                    if (site != null) ApiScreen(state, model, { scope.launch { pageScroll.scrollTo(0) } }) {
                                                        openReview(listOf(it), ResponseCheck.Quick)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        browserImport?.let { log ->
            BrowserActivityImport(log, state.workspace.projects, browserImportError, close = { browserImport = null }) { target ->
                if (model.openBrowserActivity(log, target)) {
                    browserImport = null
                    navigate(Screen.Traffic)
                } else browserImportError = if (target == null) model.state.value.message.ifBlank { "Cannot create the selected site." }
                    else "That site is no longer available."
            }
        }
        review?.let { ResponseReviewDialog(it, model) { review = null } }
        finding?.let { SaveFindingDialog(it, model) { finding = null } }
        if (commands) {
            val latest = latestReviewableResponse(state.workspace, state.projectId, state.identity)
            SearchCommands(
                items = commandUsage.order(AppCommand.entries.filter { it != AppCommand.Review || latest != null }) { it.name },
                id = { "app_${it.name.lowercase()}" },
                label = AppCommand::label,
                search = { "${it.label} ${it.aliases}" },
                dialogTag = "app_command_palette",
                close = { commands = false },
                controls = CommandControls(
                    pinned = { it.name in commandUsage.pinned },
                    recent = { it.name in commandUsage.recent },
                    togglePin = { saveCommandUsage(commandUsage.togglePin(it.name, appCommandIds)) },
                ),
            ) { command ->
                commands = false
                saveCommandUsage(commandUsage.record(command.name, appCommandIds))
                if (command == AppCommand.Find) findInPageRequest++
                when (val target = command.screen) {
                    null -> latest?.let { openReview(listOf(it), ResponseCheck.Quick) }
                    Screen.Settings -> settings()
                    else -> navigate(target)
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

@Composable
private fun PrimaryNavigation(screen: Screen, compact: Boolean, displayLabels: Boolean, rail: Boolean, navigate: (Screen) -> Unit) {
    val destinations = Screen.entries.filter { it != Screen.Settings }
    val colors = MaterialTheme.colorScheme
    if (rail) {
        Surface(color = colors.surfaceContainerLow, modifier = Modifier.fillMaxHeight().width(76.dp).testTag("primary_navigation_rail")) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 4.dp).selectableGroup()) {
                    destinations.forEach { target ->
                        NavigationDestination(target, screen == target, displayLabels, Modifier.fillMaxWidth().heightIn(min = 52.dp), navigate)
                    }
                }
                VerticalDivider(color = colors.outlineVariant)
            }
        }
        return
    }
    Surface(color = colors.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp).selectableGroup()) {
                destinations.forEach { target ->
                    NavigationDestination(target, screen == target, displayLabels, Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 55.dp), navigate)
                }
            }
        }
    }
}

@Composable
private fun NavigationDestination(target: Screen, selected: Boolean, displayLabels: Boolean, modifier: Modifier, navigate: (Screen) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.testTag("nav_${target.name.lowercase()}")
            .selectable(selected, role = Role.Tab, onClick = { navigate(target) }),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(42.dp, 28.dp).background(if (selected) colors.primaryContainer else Color.Transparent,
            MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Icon(target.icon, contentDescription = if (displayLabels) null else target.label, modifier = Modifier.size(19.dp),
                tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
        if (displayLabels) Text(target.label, color = if (selected) Signal else Muted,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

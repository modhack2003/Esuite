package dev.esuite.app.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import dev.esuite.app.R
import dev.esuite.app.core.atCharacterBoundary
import dev.esuite.app.ui.UrlKeyboard
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

private data class ConsoleRun(val document: Int, val origin: String, val token: String, val input: String, val script: Boolean)
private data class PageFindResult(val active: Int, val total: Int)

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun BrowserScreen(
    visible: Boolean,
    motion: Boolean,
    clearRequest: Int,
    findInPageRequest: Int,
    utilitiesVisible: Boolean,
    closeUtilities: () -> Unit,
    onReady: () -> Unit,
    onShortcut: ((() -> Unit)?) -> Unit,
    onSettings: () -> Unit,
    onCommands: () -> Unit,
    onDataCleared: (Boolean) -> Unit,
    onOpenApi: (String) -> Unit,
    onOpenActivity: (BrowserLogSnapshot) -> Unit,
    utilities: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hostView = LocalView.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val links = LocalUriHandler.current
    val requestLog = remember { BrowserRequestLog() }
    val coroutineScope = rememberCoroutineScope()
    var recordingNetwork by remember { mutableStateOf(true) }
    var browserStarted by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var preparing by remember { mutableStateOf(true) }
    val browser = remember(context, generation, preparing) {
        if (preparing) null else runCatching {
            WebView.setWebContentsDebuggingEnabled(false)
            WebView(context)
        }.getOrNull()
    }
    val destroyed = remember(browser) { AtomicBoolean(false) }
    var address by remember { mutableStateOf("") }
    var addressOverflow by remember { mutableStateOf(false) }
    var addressEdited by remember { mutableStateOf(false) }
    var pageUrl by remember { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf("") }
    var loadTarget by remember { mutableStateOf<String?>(null) }
    val networkOrigin = (loadTarget ?: pageUrl)?.let(BrowserAddress::https)?.let(BrowserAddress::origin)
    var networkFilters by remember(networkOrigin) { mutableStateOf(NetworkFilters()) }
    var awaitingStart by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var linkNotice by remember { mutableStateOf<String?>(null) }
    var rendererGone by remember { mutableStateOf(false) }
    var interruptedPage by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(BrowserTool.Page) }
    var output by remember { mutableStateOf("") }
    var domTree by remember { mutableStateOf<DomTree?>(null) }
    var domDetails by remember { mutableStateOf<DomDetails?>(null) }
    var domExpansion by remember { mutableStateOf(emptyMap<Int, Int>()) }
    var domQuery by remember { mutableStateOf("") }
    var domReveal by remember { mutableIntStateOf(0) }
    var domKeepSelected by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var elementNotice by remember { mutableStateOf("") }
    var document by remember { mutableIntStateOf(0) }
    var findInPage by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findResult by remember { mutableStateOf<PageFindResult?>(null) }
    var inspection by remember { mutableIntStateOf(0) }
    var console by remember { mutableStateOf(emptyList<ConsoleEntry>()) }
    var consoleId by remember { mutableLongStateOf(0L) }
    var consoleDraft by remember { mutableStateOf("") }
    var scriptMode by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<ConsoleRun?>(null) }
    var previousClearRequest by remember { mutableIntStateOf(clearRequest) }

    fun addConsole(level: ConsoleLevel, text: String, source: String = "") {
        console = (console + ConsoleEntry(consoleId++, level, text, source)).takeLast(100)
    }

    fun resetDocument() {
        if (!destroyed.get() && !rendererGone && pageUrl != null) browser?.evaluateJavascript(InspectorScripts.clearSelection, null)
        document++
        findInPage = false
        findQuery = ""
        findResult = null
        pageUrl = null
        pageTitle = ""
        console = emptyList()
        consoleDraft = ""
        running = null
        output = ""
        domTree = null
        domDetails = null
        domExpansion = emptyMap()
        domQuery = ""
        domKeepSelected = false
        picking = false
        elementNotice = ""
        linkNotice = null
    }

    fun updateHistory(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    fun beginNavigation(view: WebView, url: String) {
        val target = BrowserAddress.https(url) ?: return
        view.stopLoading()
        resetDocument()
        requestLog.page(target)
        loadTarget = url
        awaitingStart = true
        error = null
        loading = true
        progress = 0
        addressEdited = false
        addressOverflow = false
        address = url
        view.loadUrl(url)
    }

    fun openAddress() {
        focus.clearFocus()
        keyboard?.hide()
        val view = browser?.takeUnless { rendererGone || clearing } ?: return
        val target = runCatching { BrowserAddress.resolve(address) }.getOrElse {
            linkNotice = it.message
            return
        }
        beginNavigation(view, target.toString())
    }

    fun goBack() {
        addressEdited = false
        browser?.goBack()
    }

    fun openInSystemBrowser(url: String) {
        val target = BrowserAddress.https(url) ?: return
        runCatching { links.openUri(target.toString()) }.onFailure { linkNotice = "No system browser is available." }
    }

    fun markVisible(view: WebView, url: String) {
        val target = BrowserAddress.https(url) ?: return
        if (error != null || clearing || !matchesDocument(view.url, url)) return
        if (awaitingStart && !matchesDocument(loadTarget, url)) return
        pageUrl = target.toString()
        if (!addressEdited) {
            addressOverflow = false
            address = url
        }
        pageTitle = view.title.orEmpty()
        updateHistory(view)
    }

    fun activeView(): WebView? {
        val current = pageUrl ?: return null
        val view = browser ?: return null
        return view.takeIf { visible && !rendererGone && !destroyed.get() && error == null && matchesDocument(view.url, current) }
    }

    fun closePageFind() {
        keyboard?.hide()
        findInPage = false
        findQuery = ""
        findResult = null
        if (browser?.hasFocus() != true) focus.clearFocus()
    }

    fun evaluateInspection(script: String, report: (String?) -> Unit = {
        output = it.orEmpty().trim().take(20_000).ifEmpty { "No information returned by this page." }
    }) {
        val view = activeView() ?: return
        val currentDocument = document
        val request = ++inspection
        view.evaluateJavascript(script) { result ->
            if (document == currentDocument && inspection == request && !destroyed.get()) {
                report(textResult(result))
            }
        }
    }

    fun readTree(reveal: Boolean = false, after: (() -> Unit)? = null) {
        val options = DomTreeOptions(domExpansion, domQuery, domKeepSelected || reveal)
        evaluateInspection(InspectorScripts.elementTree.replace("__ESUITE_TREE_OPTIONS__", Json.encodeToString(options))) { result ->
            val tree = runCatching { Json.decodeFromString<DomTree>(result.orEmpty()) }.getOrNull()
            if (tree == null) elementNotice = "The document tree is unavailable. Refresh to try again."
            else {
                domTree = tree
                if (reveal) {
                    domKeepSelected = true
                    domReveal++
                }
            }
            after?.invoke()
        }
    }

    fun readElement(reveal: Boolean = false) {
        activeView()?.evaluateJavascript(InspectorScripts.highlightSelection, null)
        evaluateInspection(InspectorScripts.selection) { result ->
            domDetails = runCatching { Json.decodeFromString<DomDetails?>(result.orEmpty()) }.getOrNull()
            elementNotice = ""
            readTree(reveal)
        }
    }

    fun readTimings() {
        val token = requestLog.token() ?: return
        val scope = pageUrl?.let(BrowserAddress::https)?.let(BrowserAddress::origin) ?: return
        val view = activeView() ?: return
        val currentDocument = document
        coroutineScope.launch {
            val result = view.javascript(InspectorScripts.networkTiming) ?: return@launch
            val batch = withContext(Dispatchers.Default) {
                runCatching { Json.decodeFromString<TimingBatch>(result) }.getOrNull()
            }
            if (batch != null && currentDocument == document && activeView() === view) requestLog.addTimings(token, scope, batch)
        }
    }

    fun readPanel(selection: BrowserTool) {
        panel = selection
        inspection++
        val current = pageUrl
        output = ""
        if (current == null || activeView() == null) return
        when (selection) {
            BrowserTool.Page -> output = pageTitle.ifBlank { "Untitled page" }
            BrowserTool.Network -> readTimings()
            BrowserTool.Cookies -> output = runCatching {
                val cookieManager = CookieManager.getInstance()
                val rows = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
                    cookieMetadata(CookieManagerCompat.getCookieInfo(cookieManager, current), hasAttributes = true)
                } else {
                    cookieMetadata(cookieManager.getCookie(current)?.split(';').orEmpty(), hasAttributes = false)
                }
                rows.joinToString("\n").ifEmpty { "No cookies for this page." }
            }.getOrDefault("Cookie metadata is unavailable from Android System WebView.")
            BrowserTool.Elements -> if (domDetails == null) readTree() else readElement()
            BrowserTool.Storage -> evaluateInspection(InspectorScripts.storageMetadata)
            BrowserTool.Sources -> evaluateInspection(InspectorScripts.sourceFiles)
            BrowserTool.Performance -> evaluateInspection(InspectorScripts.performanceSummary)
            BrowserTool.Viewport -> evaluateInspection(InspectorScripts.viewportSummary)
            BrowserTool.Security -> evaluateInspection(InspectorScripts.securitySummary)
            BrowserTool.Accessibility -> evaluateInspection(InspectorScripts.accessibilitySummary)
            BrowserTool.Console -> Unit
        }
    }

    fun toggleInspector() {
        if (utilitiesVisible) {
            inspecting = true
            closeUtilities()
        } else inspecting = !inspecting
        if (!inspecting && browser?.hasFocus() != true) focus.clearFocus()
    }

    val toggleTools by rememberUpdatedState(::toggleInspector)
    DisposableEffect(visible, browser, rendererGone) {
        if (visible && browser != null && !rendererGone) onShortcut { toggleTools() }
        onDispose { onShortcut(null) }
    }

    LaunchedEffect(Unit) {
        // Draw the branded launch frame before initializing the WebView provider on the UI thread.
        withFrameNanos { }
        withFrameNanos { }
        preparing = false
    }
    LaunchedEffect(preparing) { if (!preparing) onReady() }
    LaunchedEffect(findInPageRequest) {
        if (findInPageRequest != 0) findInPage = true
    }
    LaunchedEffect(visible) {
        if (!visible && findInPage) closePageFind()
    }
    if (findInPage) {
        val view = activeView()
        DisposableEffect(view) {
            onDispose {
                if (!destroyed.get() && !rendererGone) {
                    view?.setFindListener(null)
                    view?.clearMatches()
                }
            }
        }
        LaunchedEffect(document, view, findQuery) {
            findResult = null
            if (view == null) return@LaunchedEffect
            view.setFindListener(null)
            view.clearMatches()
            if (findQuery.isBlank()) return@LaunchedEffect
            val currentDocument = document
            val query = findQuery
            delay(150)
            view.setFindListener { active, count, finished ->
                if (finished && currentDocument == document && activeView() === view && findInPage && findQuery == query) {
                    findResult = PageFindResult(active, count)
                }
            }
            view.findAllAsync(query)
        }
    }
    LaunchedEffect(document, visible, loading, inspecting, utilitiesVisible) {
        if (loading) return@LaunchedEffect
        val view = activeView() ?: return@LaunchedEffect
        // Some WebView providers mis-size a dynamic-height dialog as hydration finishes.
        for (pause in listOf(0L, 300L, 900L, 1_800L)) {
            delay(pause)
            if (destroyed.get() || activeView() !== view) break
            view.evaluateJavascript(InspectorScripts.fitClippedDialogs, null)
        }
    }
    LaunchedEffect(browser) {
        val url = interruptedPage
        if (browser != null && !rendererGone && url != null) {
            interruptedPage = null
            beginNavigation(browser, url)
        }
    }
    LaunchedEffect(visible, inspecting, utilitiesVisible, panel, pageUrl, pageTitle, loading) {
        if (visible && inspecting && !utilitiesVisible) readPanel(panel)
    }
    LaunchedEffect(browserStarted, recordingNetwork, document, pageUrl) {
        if (browserStarted && recordingNetwork && pageUrl != null) {
            while (true) {
                readTimings()
                delay(1500)
            }
        }
    }
    LaunchedEffect(panel, inspecting, visible, utilitiesVisible) {
        if (panel != BrowserTool.Elements || !inspecting || !visible || utilitiesVisible) {
            if (!destroyed.get() && !rendererGone && pageUrl != null) browser?.evaluateJavascript(InspectorScripts.clearSelection, null)
            domTree = null
            domDetails = null
            domExpansion = emptyMap()
            domQuery = ""
            domKeepSelected = false
            picking = false
            elementNotice = ""
        }
    }
    LaunchedEffect(picking, document, panel, inspecting, visible, utilitiesVisible) {
        if (!picking || !inspecting || utilitiesVisible || panel != BrowserTool.Elements) return@LaunchedEffect
        val view = activeView() ?: return@LaunchedEffect
        val picked = withTimeoutOrNull(25_000) {
            while (view.javascript(InspectorScripts.pickResult) != "Picked") delay(250)
            true
        } ?: false
        picking = false
        if (picked) {
            domQuery = ""
            readElement(reveal = true)
        } else {
            view.evaluateJavascript(InspectorScripts.disarmPicker, null)
            elementNotice = "Picker expired. Tap Pick element to try again."
        }
    }
    LaunchedEffect(running) {
        val run = running ?: return@LaunchedEffect
        val view = activeView() ?: run { running = null; return@LaunchedEffect }
        var started = false
        val result = withTimeoutOrNull(8_000) {
            started = view.javascript(ConsoleJavaScript.start(run.origin, run.token, run.input, run.script)) == "started"
            if (started) awaitConsole(view, run.token) else null
        }
        if (running != run || document != run.document) return@LaunchedEffect
        when {
            !started -> addConsole(ConsoleLevel.Error, "JavaScript could not run. Check the syntax and active page.")
            result == null -> addConsole(ConsoleLevel.Warning, "The command did not return a result within 8 seconds.")
            result.startsWith('E') -> addConsole(ConsoleLevel.Error, result.drop(1))
            else -> addConsole(ConsoleLevel.Result, result.drop(1))
        }
        running = null
    }
    LaunchedEffect(linkNotice) { if (linkNotice != null) { delay(3500); linkNotice = null } }

    LifecycleStartEffect(browser, visible, rendererGone) {
        browserStarted = visible && !rendererGone && browser != null
        if (visible && !destroyed.get()) browser?.onResume()
        onStopOrDispose {
            browserStarted = false
            if (!destroyed.get()) browser?.onPause()
        }
    }
    DisposableEffect(browser) {
        onDispose { if (destroyed.compareAndSet(false, true)) browser?.destroy() }
    }
    LaunchedEffect(clearRequest) {
        if (clearRequest != previousClearRequest) {
            previousClearRequest = clearRequest
            val view = browser?.takeUnless { rendererGone }
            view?.stopLoading()
            view?.clearCache(true)
            if (view != null) {
                clearing = true
                view.loadUrl("about:blank")
            }
            runCatching {
                val cookies = CookieManager.getInstance()
                WebStorage.getInstance().deleteAllData()
                cookies.removeAllCookies {
                    cookies.flush()
                    onDataCleared(true)
                }
            }.onFailure { onDataCleared(false) }
            resetDocument()
            address = ""
            loadTarget = null
            interruptedPage = null
            awaitingStart = false
            error = null
            loading = false
            canGoBack = false
            canGoForward = false
            inspecting = false
            requestLog.page(null)
        }
    }
    BackHandler(visible && (findInPage || utilitiesVisible || inspecting || canGoBack)) {
        when {
            imeVisible -> ViewCompat.getWindowInsetsController(hostView)?.hide(WindowInsetsCompat.Type.ime())
            findInPage -> closePageFind()
            utilitiesVisible -> closeUtilities()
            inspecting -> {
                inspecting = false
                if (browser?.hasFocus() != true) focus.clearFocus()
            }
            else -> goBack()
        }
    }

    Column(Modifier.fillMaxSize().clipToBounds()) {
        if (visible && !preparing) {
            BrowserToolbar(
                address, {
                    addressOverflow = it.length > BrowserAddress.MAX_CHARS
                    if (!addressOverflow) {
                        address = it
                        addressEdited = true
                        linkNotice = null
                    }
                }, ::openAddress,
                browser != null && !rendererGone && !clearing && !addressOverflow && address.isNotBlank(),
                canGoBack, canGoForward, loading, pageUrl != null,
                inspecting && !utilitiesVisible, utilitiesVisible, browser != null && !rendererGone,
                back = ::goBack, forward = { addressEdited = false; browser?.goForward() },
                reload = {
                    if (loading) {
                        browser?.stopLoading()
                        loading = false
                        if (pageUrl == null) error = "Loading was stopped."
                        browser?.let(::updateHistory)
                    } else if (browser != null) {
                        addressEdited = false
                        loadTarget = pageUrl
                        awaitingStart = true
                        loading = true
                        error = null
                        progress = 0
                        browser.reload()
                    }
                },
                tools = ::toggleInspector, commands = onCommands, settings = onSettings,
            )
            if (loading) LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
            if (findInPage) {
                PageFindBar(findQuery, { findQuery = it.atCharacterBoundary(240) }, findResult, activeView() != null, ::closePageFind) { forward ->
                    keyboard?.hide()
                    activeView()?.findNext(forward)
                }
            }
            val toolbarNotice = if (addressOverflow) "The address or search exceeds ${BrowserAddress.MAX_CHARS} characters." else linkNotice
            toolbarNotice?.let {
                Text(it, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
                    color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
        }
        BrowserDock(visible && (inspecting || utilitiesVisible), pageHasFocus = { browser?.hasFocus() == true }, page = {
            if (browser != null && !rendererGone) AndroidView(
                modifier = Modifier.fillMaxSize().testTag("browser_page").clipToBounds(),
                factory = {
                    browser.apply {
                        clipToOutline = true
                        outlineProvider = ViewOutlineProvider.BOUNDS
                        // Compose already subtracts the system bars and keyboard from the WebView bounds.
                        ViewCompat.setOnApplyWindowInsetsListener(this) { view, _ ->
                            ViewCompat.onApplyWindowInsets(view, WindowInsetsCompat.CONSUMED)
                        }
                        // Give Chromium the real touch event and the native input connection.
                        setOnTouchListener { view, event ->
                            if (event.actionMasked == MotionEvent.ACTION_DOWN && view.isEnabled && !view.hasFocus()) {
                                view.requestFocus()
                            }
                            false
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.textZoom = 100
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { if (!clearing) progress = newProgress }
                            override fun onReceivedTitle(view: WebView, title: String) {
                                if (pageUrl != null && matchesDocument(view.url, pageUrl)) pageTitle = title
                            }
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                if (!clearing && (loading || pageUrl != null)) {
                                    val source = BrowserAddress.https(message.sourceId().orEmpty())?.encodedPath?.substringAfterLast('/')
                                        ?.take(70)?.takeIf(String::isNotEmpty)?.let { "$it:${message.lineNumber()}" }.orEmpty()
                                    addConsole(ConsoleLevel.from(message.messageLevel()), message.message().take(700), source)
                                }
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val block = request.isForMainFrame && BrowserAddress.https(request.url.toString()) == null
                                if (block) linkNotice = "This link requires an HTTPS address."
                                return block
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val parsed = BrowserAddress.https(request.url.toString())
                                if (parsed != null) requestLog.add(request.method, parsed, request.isForMainFrame)
                                else if (request.isForMainFrame && request.url.toString() != "about:blank") {
                                    return WebResourceResponse("text/plain", "UTF-8", 403, "HTTPS required", emptyMap(),
                                        ByteArrayInputStream("HTTPS is required.".toByteArray()))
                                }
                                return null
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                if (clearing || url == "about:blank") return
                                val target = BrowserAddress.https(url) ?: run {
                                    view.stopLoading()
                                    error = "This page requires HTTPS."
                                    loading = false
                                    return
                                }
                                val preserve = loadTarget == url && pageUrl == null
                                resetDocument()
                                requestLog.page(target, continuing = preserve)
                                requestLog.add("", target, mainFrame = true, kind = RequestEventKind.Navigation)
                                loadTarget = url
                                awaitingStart = false
                                error = null
                                loading = true
                            }

                            override fun onPageCommitVisible(view: WebView, url: String) { markVisible(view, url) }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (clearing) {
                                    if (url == "about:blank") { view.clearHistory(); clearing = false }
                                    return
                                }
                                if (matchesDocument(view.url, url) && (!awaitingStart || matchesDocument(loadTarget, url))) {
                                    markVisible(view, url)
                                    loading = false
                                    updateHistory(view)
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                if (clearing) return
                                updateHistory(view)
                                if (!loading && pageUrl != null && BrowserAddress.https(url) != null) {
                                    loadTarget = url
                                    markVisible(view, url)
                                }
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, problem: WebResourceError) {
                                BrowserAddress.https(request.url.toString())?.let {
                                    requestLog.add(request.method, it, request.isForMainFrame, RequestEventKind.LoadError, problem.errorCode)
                                }
                                if (!clearing && request.isForMainFrame &&
                                    (matchesDocument(loadTarget, request.url.toString()) || matchesDocument(view.url, request.url.toString()))) {
                                    error = problem.description.toString()
                                    loading = false
                                    updateHistory(view)
                                }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                BrowserAddress.https(request.url.toString())?.let {
                                    requestLog.add(request.method, it, request.isForMainFrame, RequestEventKind.HttpError,
                                        response.statusCode, response.mimeType)
                                }
                            }

                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, problem: SslError) {
                                handler.cancel()
                                if (!clearing && (matchesDocument(loadTarget, problem.url) || matchesDocument(view.url, problem.url))) {
                                    error = "The site’s certificate could not be verified."
                                    loading = false
                                    updateHistory(view)
                                }
                            }

                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                (view.parent as? ViewGroup)?.removeView(view)
                                if (destroyed.compareAndSet(false, true)) view.destroy()
                                interruptedPage = pageUrl ?: loadTarget
                                resetDocument()
                                rendererGone = true
                                clearing = false
                                loading = false
                                inspecting = false
                                canGoBack = false
                                canGoForward = false
                                return true
                            }
                        }
                    }
                },
                update = { view ->
                    val showPage = visible && pageUrl != null && error == null
                    view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                    view.isEnabled = showPage
                    view.isFocusable = showPage
                    view.isFocusableInTouchMode = showPage
                    view.importantForAccessibility = if (showPage) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                },
            )
            if (visible && !preparing) {
                when {
                    rendererGone -> BrowserNotice("Browser renderer stopped", "Restart the browser to reopen an address.") {
                        Button(onClick = { generation++; rendererGone = false; error = null }) { Text("Restart browser") }
                    }
                    browser == null -> BrowserNotice("Browser unavailable", "Install or update Android System WebView.") {}
                    error != null -> BrowserNotice("Page couldn’t open", error.orEmpty()) {
                        Button(onClick = ::openAddress, enabled = address.isNotBlank()) { Text("Try again") }
                        BrowserAddress.https(address)?.let { target ->
                            TextButton(onClick = { openInSystemBrowser(target.toString()) }) { Text("Open in system browser") }
                        }
                    }
                    pageUrl == null && !loading -> BrowserNotice("Browse and inspect", "Enter an HTTPS address or search with DuckDuckGo.", brand = true) {}
                }
                AnimatedSkeleton(loading && pageUrl == null && error == null, motion)
            }
        }, dock = {
            if (utilitiesVisible) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                DockHeader("Tools", "", closeUtilities)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                utilities()
            } else BrowserInspector(panel, output, pageUrl?.takeIf { error == null }, { panel = it }, { readPanel(panel) },
                ::toggleInspector, onOpenApi, ::openInSystemBrowser,
                elements = { key(document) {
                    ElementsPanel(domTree, domDetails, pageUrl != null && error == null, elementNotice, domQuery, domReveal, picking,
                        onPick = {
                            if (picking) {
                                picking = false
                                activeView()?.evaluateJavascript(InspectorScripts.disarmPicker, null)
                                elementNotice = ""
                            } else {
                                evaluateInspection(InspectorScripts.select) {
                                    elementNotice = it.orEmpty()
                                    picking = it != null
                                }
                            }
                        },
                        onSearch = { query -> domQuery = query; readTree() },
                        onExpand = { node, more ->
                            val count = if (more) (domExpansion[node.id] ?: 24) + 24 else if (node.expanded) 0 else 24
                            domExpansion = domExpansion + (node.id to count)
                            if (count == 0) domKeepSelected = false
                            readTree()
                        },
                        onSelect = { node ->
                            picking = false
                            domDetails = null
                            evaluateInspection(InspectorScripts.selectTreeElement.replace("__ESUITE_NODE__", node.toString())) {
                                if (it == "Selected") readElement() else elementNotice = it.orEmpty()
                            }
                        },
                        onReveal = { evaluateInspection(InspectorScripts.scrollToElement) { elementNotice = it.orEmpty() } },
                        onEdit = { html, original, code, complete ->
                            val script = if (html) InspectorScripts.html else InspectorScripts.css
                            val edit = Regex("__ESUITE_(EXPECTED|VALUE)__").replace(script) {
                                JsonPrimitive(if (it.groupValues[1] == "EXPECTED") original else code).toString()
                            }
                            evaluateInspection(edit) {
                                val applied = it == "HTML updated locally" || it == "CSS updated locally"
                                complete(applied)
                                if (applied) { domQuery = ""; readElement(reveal = html) }
                                else elementNotice = it ?: "The edit couldn’t be applied. Your draft is still here."
                            }
                        })
                } }, network = {
                    val network by requestLog.state.collectAsState()
                    NetworkPanel(network, networkFilters, { networkFilters = it }, clearRequests = requestLog::clear,
                        onRecord = { recordingNetwork = it; requestLog.setRecording(it) }, onPreserve = requestLog::setPreserve,
                        onOpenActivity = { requestLog.snapshot()?.let(onOpenActivity) })
                }) {
                key(document) {
                    ConsolePanel(console, consoleDraft, { consoleDraft = it }, scriptMode, { scriptMode = it },
                        activeView() != null, running != null, run = {
                            val origin = pageUrl?.let(BrowserAddress::https)?.let(BrowserAddress::origin)
                            if (activeView() != null && origin != null && running == null &&
                                consoleDraft.isNotBlank() && consoleDraft.length <= ConsoleJavaScript.INPUT_LIMIT) {
                                keyboard?.hide()
                                focus.clearFocus()
                                addConsole(ConsoleLevel.Command, consoleDraft)
                                running = ConsoleRun(document, origin, "__esuite_" + UUID.randomUUID(), consoleDraft, scriptMode)
                                consoleDraft = ""
                            }
                        }, clear = { console = emptyList() })
                }
            }
        })
    }
}

private fun matchesDocument(first: String?, second: String?): Boolean {
    if (first == null || second == null) return false
    if (first == second) return true
    val a = BrowserAddress.https(first)?.newBuilder()?.fragment(null)?.build() ?: return false
    val b = BrowserAddress.https(second)?.newBuilder()?.fragment(null)?.build()
    return a == b
}

private fun textResult(value: String?): String? = runCatching {
    if (value == null || value.length > 256_000) return null
    (Json.parseToJsonElement(value) as? JsonPrimitive)?.takeIf { it.isString }?.content
}.getOrNull()

private suspend fun WebView.javascript(script: String): String? = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { value -> if (continuation.isActive) continuation.resume(textResult(value)) }
}

private suspend fun awaitConsole(view: WebView, token: String): String {
    val script = ConsoleJavaScript.take(token)
    while (true) {
        view.javascript(script)?.let { return it }
        delay(125)
    }
}

@Composable
private fun BrowserToolbar(
    address: String, edit: (String) -> Unit, go: () -> Unit, submitEnabled: Boolean,
    canGoBack: Boolean, canGoForward: Boolean, loading: Boolean, loaded: Boolean,
    inspecting: Boolean, utilitiesVisible: Boolean, toolsEnabled: Boolean,
    back: () -> Unit, forward: () -> Unit, reload: () -> Unit, tools: () -> Unit,
    commands: () -> Unit, settings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val addressField: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = address, onValueChange = edit, singleLine = true,
            placeholder = { Text("Search or enter an address", maxLines = 1) },
            keyboardOptions = UrlKeyboard.copy(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { if (submitEnabled) go() }),
            modifier = modifier.testTag("browser_url").semantics { contentDescription = "Search or enter an address" },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surfaceContainerLow,
                focusedContainerColor = colors.surfaceContainerLow, unfocusedBorderColor = colors.outlineVariant),
            trailingIcon = {
                TextButton(onClick = go, enabled = submitEnabled, modifier = Modifier.testTag("browser_go")) { Text("Go") }
            },
        )
    }
    val navigation: @Composable () -> Unit = {
        IconButton(onClick = back, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = forward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward")
        }
        IconButton(onClick = reload, enabled = loading || loaded, modifier = Modifier.testTag("browser_reload")) {
            Icon(if (loading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                contentDescription = if (loading) "Stop loading" else "Reload")
        }
    }
    val actions: @Composable (Boolean) -> Unit = { showLabel ->
        val toolDescription = if (inspecting) "Close Developer Tools" else "Open Developer Tools"
        val toolModifier = Modifier.testTag("browser_tools").semantics { contentDescription = toolDescription; selected = inspecting }
        if (utilitiesVisible) {
            FilledTonalIconButton(onClick = tools, enabled = toolsEnabled, modifier = toolModifier, shape = MaterialTheme.shapes.small) {
                Icon(painterResource(R.drawable.ic_terminal), null)
            }
        } else if (showLabel) {
            FilterChip(selected = inspecting, onClick = tools, enabled = toolsEnabled, modifier = toolModifier,
                label = { Text("DevTools", maxLines = 1) }, leadingIcon = {
                    Icon(painterResource(R.drawable.ic_terminal), null, Modifier.size(18.dp))
                }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer,
                    selectedLabelColor = colors.onPrimaryContainer, selectedLeadingIconColor = colors.onPrimaryContainer))
        } else {
            FilledTonalIconToggleButton(checked = inspecting, onCheckedChange = { tools() }, enabled = toolsEnabled,
                modifier = toolModifier, shape = MaterialTheme.shapes.small) {
                Icon(painterResource(R.drawable.ic_terminal), null)
            }
        }
        IconButton(onClick = commands, modifier = Modifier.testTag("open_app_commands")) {
            Icon(Icons.Outlined.Search, contentDescription = "App commands")
        }
        IconButton(onClick = settings, modifier = Modifier.testTag("open_settings")) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(colors.surface)) {
        val wide = maxWidth >= 560.dp
        val toolsLabel = maxWidth >= 420.dp && LocalDensity.current.fontScale <= 1.2f
        Column {
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    navigation()
                    addressField(Modifier.weight(1f).padding(horizontal = 6.dp))
                    actions(toolsLabel)
                }
            } else {
                addressField(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    navigation()
                    Spacer(Modifier.weight(1f))
                    actions(toolsLabel)
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

@Composable
private fun PageFindBar(query: String, edit: (String) -> Unit, result: PageFindResult?, ready: Boolean, close: () -> Unit, move: (Boolean) -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(ready) { if (ready) requester.requestFocus() }
    val hasMatches = (result?.total ?: 0) > 0
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, edit, label = { Text("Find in page") }, singleLine = true, enabled = ready,
                modifier = Modifier.weight(1f).focusRequester(requester).testTag("browser_find_query"))
            IconButton(onClick = { move(false) }, enabled = hasMatches) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = { move(true) }, enabled = hasMatches) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = close, modifier = Modifier.testTag("browser_close_find")) {
                Icon(Icons.Outlined.Close, contentDescription = "Close find in page")
            }
        }
        val count = when {
            !ready -> "Load a page to find text."
            query.isBlank() -> ""
            result == null -> "Finding matches…"
            result.total == 0 -> "No matches"
            else -> "${result.active + 1} of ${result.total}"
        }
        if (count.isNotEmpty()) {
            Text(count, Modifier.padding(start = 6.dp, bottom = 3.dp).testTag("browser_find_count")
                .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BrowserNotice(title: String, message: String, brand: Boolean = false, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)) {
        if (brand) Icon(painterResource(R.drawable.esuite_splash_mark), null, Modifier.size(62.dp), tint = Color.Unspecified)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        action()
    }
}

@Composable
private fun AnimatedSkeleton(visible: Boolean, motion: Boolean) {
    AnimatedVisibility(visible, enter = if (motion) fadeIn() else EnterTransition.None,
        exit = if (motion) fadeOut() else ExitTransition.None) { BrowserSkeleton() }
}

@Composable
internal fun BrowserSkeleton(label: String = "Loading page") {
    Surface(Modifier.fillMaxSize().testTag("browser_loading"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            listOf(0.62f to 22, 1f to 92, 0.92f to 12, 0.76f to 12, 0.85f to 12).forEach { (width, height) ->
                Box(Modifier.fillMaxWidth(width).height(height.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small))
            }
        }
    }
}

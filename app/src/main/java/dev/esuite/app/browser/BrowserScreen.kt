package dev.esuite.app.browser

import android.annotation.SuppressLint
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import dev.esuite.app.R
import dev.esuite.app.ui.UrlKeyboard
import dev.esuite.app.ui.WorkspaceTabs
import dev.esuite.app.ui.technicalTextStyle
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

private enum class ToolPanel { Page, Elements, Cookies, Storage, Console, Network }

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BrowserScreen(
    visible: Boolean,
    motion: Boolean,
    clearRequest: Int,
    onSettings: () -> Unit,
    onDataCleared: (Boolean) -> Unit,
    onOpenApi: (String) -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requestLog = remember { BrowserRequestLog() }
    var generation by remember { mutableIntStateOf(0) }
    val browser = remember(context, generation) { runCatching { WebView(context) }.getOrNull() }
    val destroyed = remember(browser) { java.util.concurrent.atomic.AtomicBoolean(false) }
    var address by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rendererGone by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var inspectors by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(ToolPanel.Page) }
    var output by remember { mutableStateOf("") }
    var console by remember { mutableStateOf(emptyList<String>()) }
    var previousClearRequest by remember { mutableIntStateOf(clearRequest) }

    fun endNavigation(view: WebView) {
        loading = false
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    fun openAddress() {
        focus.clearFocus()
        keyboard?.hide()
        if (browser == null || rendererGone) return
        inspectors = false
        val target = runCatching { BrowserAddress.resolve(address) }.getOrElse {
            error = it.message
            return
        }
        browser.stopLoading()
        address = target.toString()
        requestLog.page(target)
        error = null
        loading = true
        progress = 0
        browser.loadUrl(target.toString())
    }

    fun readPanel(selection: ToolPanel) {
        panel = selection
        output = ""
        val current = pageUrl ?: return
        val view = browser ?: return
        when (selection) {
            ToolPanel.Page -> output = pageTitle
            ToolPanel.Network -> output = requestLog.snapshot().joinToString("\n").ifEmpty { "No same-site requests recorded." }
            ToolPanel.Cookies -> {
                val cookieManager = CookieManager.getInstance()
                val attributes = WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)
                val rows = if (attributes) CookieManagerCompat.getCookieInfo(cookieManager, current)
                else cookieManager.getCookie(current)?.split(';').orEmpty()
                output = cookieMetadata(rows, attributes).joinToString("\n").ifEmpty { "No cookies for this page." }
            }
            ToolPanel.Console -> output = console.joinToString("\n").ifEmpty { "No console messages for this page." }
            ToolPanel.Elements, ToolPanel.Storage -> {
                val script = if (selection == ToolPanel.Elements) InspectorScripts.documentOutline else InspectorScripts.storageMetadata
                view.evaluateJavascript(script) { result ->
                    if (pageUrl == current && panel == selection && !loading) {
                        output = runCatching { (Json.parseToJsonElement(result) as JsonPrimitive).content }.getOrDefault("").take(12_000)
                    }
                }
            }
        }
    }

    LifecycleStartEffect(browser, visible, rendererGone) {
        if (visible && !destroyed.get()) browser?.onResume()
        onStopOrDispose { if (!destroyed.get()) browser?.onPause() }
    }
    DisposableEffect(browser) {
        onDispose {
            if (destroyed.compareAndSet(false, true)) browser?.destroy()
        }
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
            address = ""
            pageUrl = null
            pageTitle = ""
            error = null
            loading = false
            canGoBack = false
            canGoForward = false
            inspectors = false
            console = emptyList()
            output = ""
            requestLog.page(null)
        }
    }
    BackHandler(visible && (inspectors || canGoBack)) {
        if (inspectors) inspectors = false else browser?.goBack()
    }

    Column(if (visible) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
        if (visible) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; if (!loading) error = null },
                        singleLine = true,
                        placeholder = { Text("Search or enter an address") },
                        keyboardOptions = UrlKeyboard.copy(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { openAddress() }),
                        modifier = Modifier.fillMaxWidth().testTag("browser_url")
                            .semantics { contentDescription = "Search or enter an address" },
                        trailingIcon = {
                            TextButton(
                                onClick = ::openAddress,
                                enabled = browser != null && !rendererGone && address.isNotBlank(),
                                modifier = Modifier.testTag("browser_go"),
                            ) { Text("Go") }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { browser?.goBack() }, enabled = canGoBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { browser?.goForward() }, enabled = canGoForward) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = {
                            if (loading) { browser?.stopLoading(); loading = false }
                            else if (browser != null) { loading = true; progress = 0; error = null; browser.reload() }
                        }, enabled = loading || pageUrl != null, modifier = Modifier.testTag("browser_reload")) {
                            Icon(if (loading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                                contentDescription = if (loading) "Stop loading" else "Reload")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            enabled = pageUrl != null && !loading,
                            onClick = { inspectors = !inspectors; if (inspectors) readPanel(panel) },
                            modifier = Modifier.testTag("browser_tools"),
                        ) { Icon(Icons.Outlined.Build, contentDescription = if (inspectors) "Close Developer tools" else "Developer tools") }
                        IconButton(onClick = onSettings, modifier = Modifier.testTag("open_settings")) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                }
            }
            if (loading) LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
        }

        Box(if (visible) Modifier.fillMaxWidth().weight(1f).clipToBounds() else Modifier.size(0.dp)) {
            if (browser != null && !rendererGone) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().testTag("browser_page").clipToBounds(),
                    factory = {
                        browser.apply {
                            setBackgroundColor(android.graphics.Color.WHITE)
                            clipToOutline = true
                            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.safeBrowsingEnabled = true
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress
                                }

                                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                                    if (loading || pageUrl != null) {
                                        val line = "${message.messageLevel()} · ${message.message().take(400)}"
                                        console = (console + line).takeLast(30)
                                    }
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val block = request.isForMainFrame && BrowserAddress.https(request.url.toString()) == null
                                    if (block) error = "This link requires an HTTPS address."
                                    return block
                                }

                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val parsed = BrowserAddress.https(request.url.toString())
                                    if (parsed != null) {
                                        requestLog.add(request.method, parsed)
                                    } else if (request.isForMainFrame && request.url.toString() != "about:blank") {
                                        return WebResourceResponse("text/plain", "UTF-8", 403, "HTTPS required", emptyMap(),
                                            ByteArrayInputStream("HTTPS is required.".toByteArray()))
                                    }
                                    return null
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    if (url == "about:blank") return
                                    val target = BrowserAddress.https(url)
                                    if (target == null) {
                                        view.stopLoading()
                                        error = "This page requires HTTPS."
                                        endNavigation(view)
                                        return
                                    }
                                    val sameRequest = url == address && pageUrl == null
                                    requestLog.page(target, preserveExisting = sameRequest)
                                    requestLog.add("Navigation", target)
                                    loading = true
                                    pageUrl = null
                                    console = emptyList()
                                    output = ""
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (clearing && url == "about:blank") {
                                        view.clearHistory()
                                        clearing = false
                                        return
                                    }
                                    val target = BrowserAddress.https(url) ?: return
                                    val active = view.url?.let(BrowserAddress::https)
                                    if (active != null && active.newBuilder().fragment(null).build() != target.newBuilder().fragment(null).build()) return
                                    pageUrl = target.toString().takeIf { error == null }
                                    if (pageUrl != null) {
                                        address = url
                                        pageTitle = view.title.orEmpty()
                                    }
                                    endNavigation(view)
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, problem: WebResourceError) {
                                    if (request.isForMainFrame) {
                                        error = problem.description.toString()
                                        endNavigation(view)
                                    }
                                }

                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, problem: SslError) {
                                    handler.cancel()
                                    if (view.url == problem.url || address == problem.url) {
                                        error = "The site’s certificate could not be verified."
                                        endNavigation(view)
                                    }
                                }

                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    if (destroyed.compareAndSet(false, true)) view.destroy()
                                    rendererGone = true
                                    loading = false
                                    pageUrl = null
                                    inspectors = false
                                    canGoBack = false
                                    canGoForward = false
                                    return true
                                }
                            }
                        }
                    },
                    update = { view ->
                        val showPage = visible && !inspectors && pageUrl != null && error == null
                        view.visibility = if (visible && !inspectors) View.VISIBLE else View.INVISIBLE
                        view.alpha = if (showPage) 1f else 0f
                        view.importantForAccessibility = if (showPage) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    },
                )
            }

            if (visible) when {
                rendererGone -> BrowserNotice("Browser needs to restart", "The page renderer stopped.") {
                    Button(onClick = { generation++; rendererGone = false; error = null }) { Text("Restart Browser") }
                }
                browser == null -> BrowserNotice("Browser unavailable", "Install or update Android System WebView.") {}
                inspectors -> BrowserInspector(panel, output, pageUrl, { readPanel(it) }, { readPanel(panel) }, onOpenApi)
                error != null -> BrowserNotice("Page couldn’t open", error.orEmpty()) {
                    Button(onClick = ::openAddress, enabled = address.isNotBlank()) { Text("Try again") }
                }
                pageUrl == null && !loading -> BrowserNotice("ESuite Browser", "Enter an HTTPS address or search with DuckDuckGo.", brand = true) {}
            }
            AnimatedSkeleton(visible && loading && !inspectors, motion)
        }
    }
}

@Composable
private fun BrowserInspector(
    panel: ToolPanel,
    output: String,
    currentUrl: String?,
    choose: (ToolPanel) -> Unit,
    refresh: () -> Unit,
    openApi: (String) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Developer tools", style = MaterialTheme.typography.titleLarge)
            currentUrl?.let { Text(BrowserAddress.https(it)?.let(BrowserAddress::origin).orEmpty(),
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            WorkspaceTabs(ToolPanel.entries, panel, "browser_tool", { it.name }, choose)
            val detail = when (panel) {
                ToolPanel.Page -> "Page title"
                ToolPanel.Elements -> "Document outline"
                ToolPanel.Cookies -> "Cookie names and available flags"
                ToolPanel.Storage -> "Storage keys and value lengths"
                ToolPanel.Console -> "Recent console messages"
                ToolPanel.Network -> "Recent same-site navigation and requests"
            }
            Text(detail, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("browser_tool_heading"))
            SelectionContainer {
                Text(output, Modifier.fillMaxWidth().testTag("browser_output"), style = technicalTextStyle())
            }
            OutlinedButton(onClick = refresh) { Text("Refresh inspection") }
            if (panel == ToolPanel.Page && currentUrl != null) {
                TextButton(onClick = { openApi(currentUrl) }, modifier = Modifier.testTag("browser_open_api")) {
                    Text("Open in API Lab")
                }
            }
        }
    }
}

@Composable
private fun BrowserNotice(title: String, message: String, brand: Boolean = false, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (brand) Icon(painterResource(R.drawable.ic_esuite), null, Modifier.size(55.dp), tint = Color.Unspecified)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        action()
    }
}

@Composable
private fun AnimatedSkeleton(visible: Boolean, motion: Boolean) {
    AnimatedVisibility(visible,
        enter = if (motion) fadeIn() else androidx.compose.animation.EnterTransition.None,
        exit = if (motion) fadeOut() else androidx.compose.animation.ExitTransition.None) {
        BrowserSkeleton()
    }
}

@Composable
internal fun BrowserSkeleton(label: String = "Loading page") {
    Surface(Modifier.fillMaxSize().testTag("browser_loading"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            listOf(0.66f to 26, 1f to 112, 0.94f to 12, 0.78f to 12, 0.9f to 12).forEach { (width, height) ->
                Box(Modifier.fillMaxWidth(width).height(height.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium))
            }
        }
    }
}

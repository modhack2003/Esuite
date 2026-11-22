package dev.esuite.app.browser

import android.annotation.SuppressLint
import android.net.http.SslError
import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.esuite.app.AppViewModel
import dev.esuite.app.core.*
import dev.esuite.app.ui.TechnicalKeyboard
import dev.esuite.app.ui.UrlKeyboard
import dev.esuite.app.ui.technicalTextStyle
import kotlinx.serialization.json.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(project: Project, identity: String, model: AppViewModel, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissEditor() { focus.clearFocus(); keyboard?.hide() }
    val policy = remember(project.id) { ScopePolicy(project.origin) }
    // Pending editors live in the ViewModel: BrowserScreen is destroyed on every module switch.
    val pending by model.drafts.values.collectAsState()
    // DraftKey.of adds the identity dimension for the editors that declare it, so the cookie and
    // script editors keep one identity's text out of the other's isolated profile.
    fun draftText(editor: Editor, fallback: String = "") = pending[DraftKey.of(project.id, editor, identity)] ?: fallback
    fun write(editor: Editor, value: String) = model.drafts.set(DraftKey.of(project.id, editor, identity), value)
    val address = draftText(Editor.BrowserAddress, project.origin + "/")
    val latestAddress by rememberUpdatedState(address)
    var webView by remember(project.id, identity) { mutableStateOf<WebView?>(null) }
    var rendererFailed by remember(project.id, identity) { mutableStateOf(false) }
    var panel by remember(project.id, identity) { mutableStateOf("Page") }
    var output by remember(project.id, identity) { mutableStateOf("Press Go to load the selected target. Inspection is local to this WebView.") }
    var console by remember(project.id, identity) { mutableStateOf(emptyList<String>()) }
    val script = draftText(Editor.BrowserScript, "document.title")
    val edit = draftText(Editor.BrowserEdit)
    val cookie = draftText(Editor.BrowserCookie)
    var reveal by remember(project.id, identity) { mutableStateOf(false) }
    var loadedAddress by remember(project.id, identity) { mutableStateOf<String?>(null) }
    var requestedAddress by remember(project.id, identity) { mutableStateOf<String?>(null) }
    var addressExpanded by remember(project.id, identity) { mutableStateOf(false) }
    var clearStorage by remember(project.id, identity) { mutableStateOf(false) }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val compact = with(LocalDensity.current) { windowHeight.toDp() < 450.dp || fontScale > 1.3f }
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val provider = remember { WebViewCompat.getCurrentWebViewPackage(context)?.let { "${it.packageName} ${it.versionName}" } ?: "No WebView provider" }
    if (!supported) {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Update Android System WebView to use separate browser profiles. API Lab sessions remain available.", Modifier.testTag("browser_unsupported"))
            Text(provider, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    fun evaluate(js: String) {
        dismissEditor()
        if (!policy.allows((webView?.url ?: "").substringBefore('#'))) { output = "Load an in-scope page first"; return }
        webView?.evaluateJavascript(js) { result -> output = runCatching { val e = Json.parseToJsonElement(result); if (e is JsonPrimitive && e.isString) e.content else e.toString() }.getOrDefault(result).let { text -> if (text.length > 24000) "TRUNCATED: showing first 24000 of ${text.length} characters.\n" + text.take(24000) else text } }
    }
    fun cookieManager(): CookieManager? = webView?.let { if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) WebViewCompat.getProfile(it).cookieManager else null }
    fun readCookies() {
        val url = runCatching { policy.requireAllowed(address.substringBefore('#')).toString() }.getOrElse { output = "Enter an in-scope URL"; return }
        val manager = cookieManager() ?: return
        val rows = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) CookieManagerCompat.getCookieInfo(manager, url)
            else manager.getCookie(url)?.split(';')?.map { it.trim() }.orEmpty()
        output = (if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) "Cookie attributes unavailable on this WebView.\n\n" else "") + if (rows.isEmpty()) "No cookies for this URL" else rows.joinToString("\n\n") { row ->
            if (reveal) row else row.split(';').mapIndexed { i, value -> if (i == 0) value.substringBefore('=') + "=[HIDDEN]" else value }.joinToString(";")
        }
    }
    fun go() {
        dismissEditor()
        output = "Loading…"
        runCatching { policy.requireAllowed(address.substringBefore('#')) }.onSuccess {
            val target = webView ?: run { output = "Restart the browser renderer to load a page"; return }
            requestedAddress = address
            addressExpanded = false
            target.loadUrl(it.newBuilder().encodedFragment(address.substringAfter('#', "").ifEmpty { null }).build().toString())
        }.onFailure { output = it.message ?: "Invalid URL" }
    }
    DisposableEffect(project.id, identity) { onDispose { webView?.apply { stopLoading(); cookieManager()?.flush(); webChromeClient = null; destroy() } } }
    Column(modifier.fillMaxSize().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (panel == "Page") {
            if (!compact || addressExpanded) OutlinedTextField(address, { write(Editor.BrowserAddress, it) },
                label = { Text("Target HTTPS URL") }, keyboardOptions = UrlKeyboard, textStyle = technicalTextStyle(),
                modifier = Modifier.fillMaxWidth().testTag("browser_url"), singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (compact) TextButton(onClick = { addressExpanded = !addressExpanded; if (!addressExpanded) dismissEditor() }) {
                    Text(if (addressExpanded) "Hide address" else "Address")
                }
                Button(modifier = Modifier.testTag("browser_go"), onClick = ::go) { Text("Go") }
                OutlinedButton(onClick = { webView?.stopLoading(); if (output == "Loading…") output = "Page load stopped" }) { Text("Stop") }
                OutlinedButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }) { Text("Back") }
            }
        }
        if (rendererFailed) OutlinedButton(onClick = { rendererFailed = false; output = "Press Go to reload the target" }) { Text("Restart browser renderer") }
        else key(project.id, identity) {
            AndroidView(modifier = Modifier.fillMaxWidth().clipToBounds().testTag("browser_page").then(if (panel == "Page") Modifier.weight(1f) else Modifier.height(0.dp)), factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.WHITE)
                    clipToOutline = true
                    outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) WebViewCompat.setProfile(this, "esuite_${project.id}_$identity")
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    settings.allowFileAccess = false; settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW; settings.safeBrowsingEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) WebViewCompat.getProfile(this).cookieManager.setAcceptThirdPartyCookies(this, false)
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean { console = (console + "${message.messageLevel()}: ${message.message().take(2000)}").takeLast(50); return true }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val blocked = request.isForMainFrame && !policy.allows(request.url.toString().substringBefore('#'))
                            if (blocked) output = "Blocked navigation outside project scope"
                            return blocked
                        }
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            // POST navigations do not invoke shouldOverrideUrlLoading.
                            if (request.isForMainFrame && !policy.allows(request.url.toString().substringBefore('#'))) {
                                view.post { output = "Blocked navigation outside project scope" }
                                return WebResourceResponse("text/plain", "UTF-8", 403, "Outside project scope", emptyMap(), ByteArrayInputStream("Blocked navigation outside project scope".toByteArray()))
                            }
                            if (policy.allows(request.url.toString().substringBefore('#'))) model.observeBrowser(project, identity, RequestDraft(request.method, request.url.toString(), request.requestHeaders.entries.map { Header(it.key, it.value) }))
                            return null
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            if (!policy.allows(url.substringBefore('#'))) { view.stopLoading(); output = "Blocked navigation outside project scope" }
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            if (!policy.allows(url.substringBefore('#'))) return
                            if (latestAddress == requestedAddress || latestAddress == loadedAddress) write(Editor.BrowserAddress, url)
                            loadedAddress = url
                            requestedAddress = null
                            if (output == "Loading…") output = "Page loaded"
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            (view.parent as? android.view.ViewGroup)?.removeView(view)
                            view.destroy(); webView = null; loadedAddress = null; requestedAddress = null; rendererFailed = true
                            output = "WebView renderer stopped. Use Restart browser renderer, then Go. Profile data is retained."
                            return true
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); output = "TLS validation failed. Connection cancelled." }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) output = "Navigation failed: ${error.description}" }
                    }
                    webView = this
                }
            }, update = { view -> view.visibility = if (panel == "Page") android.view.View.VISIBLE else android.view.View.INVISIBLE })
        }
        Row(Modifier.fillMaxWidth().selectableGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Page", "Elements", "Cookies", "Storage", "Console").forEach { tab ->
                val selected = panel == tab
                Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                    .testTag("browser_tab_${tab.lowercase()}")
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background, MaterialTheme.shapes.small)
                    .selectable(selected, role = Role.Tab, onClick = { dismissEditor(); panel = tab; output = "" })
                    .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(tab, style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val panelScroll = key(panel) { rememberScrollState() }
        Column(Modifier.fillMaxWidth().testTag("browser_inspector").then(if (panel == "Page") Modifier.heightIn(max = if (compact) 36.dp else 64.dp) else Modifier.weight(1f))
            .verticalScroll(panelScroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (panel != "Page") {
            val scopeLabel = if (panel == "Cookies") "Cookie lookup URL · ${address.substringBefore('?').substringBefore('#')}"
                else loadedAddress?.let { "Loaded page · ${it.substringBefore('?').substringBefore('#')}" } ?: "Load a page to use $panel"
            Text(scopeLabel, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (panel) {
            "Elements" -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.testTag("browser_select"), onClick = { evaluate(InspectorScripts.select); panel = "Page" }) { Text("Select element") }
                    OutlinedButton(modifier = Modifier.testTag("browser_read_selection"), onClick = { evaluate(InspectorScripts.selection) }) { Text("Read selection") }
                }
                OutlinedTextField(edit, { write(Editor.BrowserEdit, it) }, maxLines = 5, label = { Text("HTML or CSS edit (local page only)") }, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("browser_edit"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(modifier = Modifier.testTag("browser_apply_html"), onClick = { evaluate(InspectorScripts.html.replace("__ESUITE_VALUE__", JsonPrimitive(edit).toString())) }) { Text("Apply HTML") }
                    TextButton(modifier = Modifier.testTag("browser_apply_css"), onClick = { evaluate(InspectorScripts.css.replace("__ESUITE_VALUE__", JsonPrimitive(edit).toString())) }) { Text("Apply CSS") }
                }
            }
            "Cookies" -> {
                Row { Text("Reveal cookie values", Modifier.weight(1f)); Switch(reveal, modifier = Modifier.testTag("browser_reveal"), onCheckedChange = { reveal = it; output = "Refresh to apply visibility choice" }) }
                OutlinedButton(modifier = Modifier.testTag("browser_read_cookies"), onClick = { dismissEditor(); readCookies() }) { Text("Read native cookies") }
                OutlinedTextField(cookie, { write(Editor.BrowserCookie, it) }, maxLines = 5, label = { Text("Set-Cookie value including attributes") }, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("browser_cookie_input"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(modifier = Modifier.testTag("browser_set_cookie"), onClick = { dismissEditor(); runCatching { policy.requireAllowed(address.substringBefore('#')) }.onSuccess { cookieManager()?.setCookie(it.toString(), cookie) { ok -> cookieManager()?.flush(); output = if (ok) "Cookie stored" else "Cookie rejected" } }.onFailure { output = "Invalid cookie origin" } }) { Text("Set") }
                    TextButton(onClick = { cookieManager()?.removeAllCookies { cookieManager()?.flush(); output = "Cookies cleared for this isolated profile" } }) { Text("Clear profile") }
                    TextButton(modifier = Modifier.testTag("browser_transfer"), onClick = {
                        runCatching { policy.requireAllowed(address.substringBefore('#')) }.onSuccess { url ->
                            val manager = cookieManager() ?: return@onSuccess
                            val cookies = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) CookieManagerCompat.getCookieInfo(manager, url.toString())
                                else manager.getCookie(url.toString())?.split(';')?.map { it.trim() + "; Path=/; Secure" }.orEmpty()
                            output = if (model.transferBrowserCookies(url.toString(), cookies)) "API identity $identity's memory-only cookie jar replaced. Switch to API Lab to review. Without metadata support, only the current URL's cookies are transferred with host/root-path defaults." else "Transfer failed; see workspace status."
                        }.onFailure { output = "Invalid cookie origin" }
                    }) { Text("To API jar") }
                }
            }
            "Storage" -> {
                OutlinedButton(modifier = Modifier.testTag("browser_read_storage"), onClick = { evaluate(InspectorScripts.storage) }) { Text("Read storage (reveals values)") }
                OutlinedButton(onClick = { clearStorage = true }, enabled = loadedAddress != null) { Text("Clear page storage") }
            }
            "Console" -> {
                OutlinedTextField(script, { write(Editor.BrowserScript, it) }, maxLines = 5, label = { Text("JavaScript in current page") }, keyboardOptions = TechnicalKeyboard, textStyle = technicalTextStyle(), modifier = Modifier.fillMaxWidth().testTag("browser_script"))
                OutlinedButton(modifier = Modifier.testTag("browser_evaluate"), onClick = { evaluate(script) }) { Text("Evaluate") }
                Text(console.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            SelectionContainer { Text(output, Modifier.testTag("browser_output").fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, style = technicalTextStyle()) }
        }
        }
    }
    if (clearStorage) AlertDialog(onDismissRequest = { clearStorage = false }, title = { Text("Clear page storage?") },
        text = { Text("This removes local and session storage for the loaded page in identity $identity's browser profile.") },
        confirmButton = { TextButton(onClick = {
            clearStorage = false
            evaluate("(() => {localStorage.clear();sessionStorage.clear();return 'Page storage cleared';})()")
        }) { Text("Clear storage") } }, dismissButton = { TextButton(onClick = { clearStorage = false }) { Text("Cancel") } })
}

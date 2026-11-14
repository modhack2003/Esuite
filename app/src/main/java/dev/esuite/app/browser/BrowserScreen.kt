package dev.esuite.app.browser

import android.annotation.SuppressLint
import android.net.http.SslError
import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.esuite.app.AppViewModel
import dev.esuite.app.core.*
import kotlinx.serialization.json.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(project: Project, identity: String, model: AppViewModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissEditor() { focus.clearFocus(); keyboard?.hide() }
    val policy = remember(project.id) { ScopePolicy(project.origin) }
    var address by remember(project.id, identity) { mutableStateOf(project.origin + "/") }
    var webView by remember(project.id, identity) { mutableStateOf<WebView?>(null) }
    var rendererFailed by remember(project.id, identity) { mutableStateOf(false) }
    var panel by remember(project.id, identity) { mutableStateOf("Elements") }
    var output by remember(project.id, identity) { mutableStateOf("Press Go to load the selected target. Inspection is local to this WebView.") }
    var console by remember(project.id, identity) { mutableStateOf(emptyList<String>()) }
    var script by remember(project.id, identity) { mutableStateOf("document.title") }
    var edit by remember(project.id, identity) { mutableStateOf("") }
    var reveal by remember(project.id, identity) { mutableStateOf(false) }
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val provider = remember { WebViewCompat.getCurrentWebViewPackage(context)?.let { "${it.packageName} ${it.versionName}" } ?: "No WebView provider" }
    if (!supported) {
        Text("Separate WebView profiles unavailable. Update Android System WebView to enable this browser. API Lab identity isolation is independent.", Modifier.testTag("browser_unsupported"))
        Text(provider, style = MaterialTheme.typography.bodySmall)
        return
    }
    fun evaluate(js: String) {
        dismissEditor()
        if (!policy.allows((webView?.url ?: "").substringBefore('#'))) { output = "Load an in-scope page first"; return }
        webView?.evaluateJavascript(js) { result -> output = runCatching { val e = Json.parseToJsonElement(result); if (e is JsonPrimitive && e.isString) e.content else e.toString() }.getOrDefault(result).take(24000) }
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
    DisposableEffect(project.id, identity) { onDispose { webView?.apply { stopLoading(); cookieManager()?.flush(); webChromeClient = null; destroy() } } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Live browser · identity $identity", style = MaterialTheme.typography.titleMedium)
        Text(provider, style = MaterialTheme.typography.bodySmall)
        Text("Exact-origin navigation. Third-party subresources may load but are not recorded. TLS errors are never ignored.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(address, { address = it }, label = { Text("Target URL") }, modifier = Modifier.fillMaxWidth().testTag("browser_url"), singleLine = true, supportingText = { Text("HTTPS · system certificate validation") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.testTag("browser_go"), onClick = { dismissEditor(); output = "Loading…"; runCatching { policy.requireAllowed(address.substringBefore('#')) }.onSuccess { webView?.loadUrl(it.newBuilder().encodedFragment(address.substringAfter('#', "").ifEmpty { null }).build().toString()) }.onFailure { output = it.message ?: "Invalid URL" } }) { Text("Go") }
            OutlinedButton(onClick = { webView?.stopLoading() }) { Text("Stop") }
            OutlinedButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }) { Text("Back") }
        }
        if (rendererFailed) OutlinedButton(onClick = { rendererFailed = false; output = "Press Go to reload the target" }) { Text("Restart browser renderer") }
        else key(project.id, identity) {
            AndroidView(modifier = Modifier.fillMaxWidth().height(340.dp), factory = { context ->
                WebView(context).apply {
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
                        override fun onPageFinished(view: WebView, url: String) { if (policy.allows(url.substringBefore('#'))) { address = url; if (output == "Loading…") output = "Page loaded" } }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            (view.parent as? android.view.ViewGroup)?.removeView(view)
                            view.destroy(); webView = null; rendererFailed = true
                            output = "WebView renderer stopped. Use Restart browser renderer, then Go. Profile data is retained."
                            return true
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); output = "TLS validation failed. Connection cancelled." }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) output = "Navigation failed: ${error.description}" }
                    }
                    webView = this
                }
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Elements", "Cookies", "Storage", "Console").forEach { tab -> TextButton(modifier = Modifier.weight(1f).testTag("browser_tab_${tab.lowercase()}"), contentPadding = PaddingValues(horizontal = 4.dp), onClick = { dismissEditor(); panel = tab; output = "" }) { Text(tab, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) } }
        }
        when (panel) {
            "Elements" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.testTag("browser_select"), onClick = { evaluate(InspectorScripts.select) }) { Text("Select element") }
                    OutlinedButton(modifier = Modifier.testTag("browser_read_selection"), onClick = { evaluate(InspectorScripts.selection) }) { Text("Read selection") }
                }
                OutlinedTextField(edit, { edit = it }, maxLines = 5, label = { Text("HTML or CSS edit (local page only)") }, modifier = Modifier.fillMaxWidth().testTag("browser_edit"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(modifier = Modifier.testTag("browser_apply_html"), onClick = { evaluate(InspectorScripts.html.replace("__ESUITE_VALUE__", JsonPrimitive(edit).toString())) }) { Text("Apply HTML") }
                    TextButton(modifier = Modifier.testTag("browser_apply_css"), onClick = { evaluate(InspectorScripts.css.replace("__ESUITE_VALUE__", JsonPrimitive(edit).toString())) }) { Text("Apply CSS") }
                }
            }
            "Cookies" -> {
                Row { Text("Reveal cookie values", Modifier.weight(1f)); Switch(reveal, modifier = Modifier.testTag("browser_reveal"), onCheckedChange = { reveal = it; output = "Refresh to apply visibility choice" }) }
                OutlinedButton(modifier = Modifier.testTag("browser_read_cookies"), onClick = { dismissEditor(); readCookies() }) { Text("Read native cookies") }
                OutlinedTextField(edit, { edit = it }, maxLines = 5, label = { Text("Set-Cookie value including attributes") }, modifier = Modifier.fillMaxWidth().testTag("browser_cookie_input"))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(modifier = Modifier.testTag("browser_set_cookie"), onClick = { dismissEditor(); runCatching { policy.requireAllowed(address.substringBefore('#')) }.onSuccess { cookieManager()?.setCookie(it.toString(), edit) { ok -> cookieManager()?.flush(); output = if (ok) "Cookie stored" else "Cookie rejected" } }.onFailure { output = "Invalid cookie origin" } }) { Text("Set") }
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
                OutlinedButton(onClick = { evaluate("(() => {localStorage.clear();sessionStorage.clear();return 'Page storage cleared';})()") }) { Text("Clear page storage") }
            }
            "Console" -> {
                OutlinedTextField(script, { script = it }, maxLines = 5, label = { Text("JavaScript in current page") }, modifier = Modifier.fillMaxWidth().testTag("browser_script"))
                OutlinedButton(modifier = Modifier.testTag("browser_evaluate"), onClick = { evaluate(script) }) { Text("Evaluate") }
                Text(console.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            SelectionContainer { Text(output, Modifier.testTag("browser_output").fillMaxWidth(), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

package dev.esuite.app

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test-only HTTPS origin. Chromium owns the synthetic cookie throughout both navigations. */
@SdkSuppress(minSdkVersion = 35, maxSdkVersion = 35)
class BrowserSessionFixtureTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun control(id: String) = requireNotNull(device.wait(Until.findObject(By.res(id)), 10000)) { "Missing $id" }

    private fun reveal(id: String, container: String? = null) {
        val selector = By.res(id)
        if (!device.wait(Until.hasObject(selector), 800)) {
            val scroll = if (container != null) control(container) else requireNotNull(device.findObject(By.scrollable(true)))
            scroll.scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.hasObject(selector))
        }
    }

    private fun openSessionDraft() {
        control("browser_tools").click()
        control("browser_panel_picker").click()
        reveal("browser_panel_choice_cookies", "browser_panel_choices")
        control("browser_panel_choice_cookies").click()
        reveal("browser_use_session")
        control("browser_use_session").click()
        reveal("api_session_open")
    }

    @Test fun signInOpenProtectedPageAndSignOutInTheSameBrowser() {
        val protectedLoads = java.util.concurrent.atomic.AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            control("browser_url")
            device.wait(Until.gone(By.res("app_launch")), 10000)
            scenario.onActivity { activity ->
                val web = requireNotNull(findWebView(activity.window.decorView))
                val original = web.webViewClient
                web.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) = original.onPageStarted(view, url, icon)
                    override fun onPageCommitVisible(view: WebView, url: String) = original.onPageCommitVisible(view, url)
                    override fun onPageFinished(view: WebView, url: String) = original.onPageFinished(view, url)
                    override fun doUpdateVisitedHistory(view: WebView, url: String, reload: Boolean) =
                        original.doUpdateVisitedHistory(view, url, reload)
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                        val page = when {
                            request.url.host != "session-fixture.invalid" -> "Unexpected destination"
                            request.url.path == "/login" -> """
                                <button onclick="document.cookie='fixture=active; Path=/; Secure; SameSite=Strict'; location.href='/protected'">Fixture sign in</button>
                            """
                            request.url.path == "/protected" -> """
                                <main></main><script>
                                document.querySelector('main').textContent = document.cookie.split('; ').includes('fixture=active')
                                    ? 'Protected fixture content ${protectedLoads.incrementAndGet()}' : 'Sign-in required';
                                </script><button onclick="document.cookie='fixture=; Path=/; Max-Age=0; Secure'; location.reload()">Fixture sign out</button>
                            """
                            else -> ""
                        }
                        return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
                            ("<meta name='viewport' content='width=device-width'>" + page).byteInputStream())
                    }
                }
            }
            control("browser_url").text = "https://session-fixture.invalid/login"
            control("browser_go").click()
            requireNotNull(device.wait(Until.findObject(By.text("Fixture sign in")), 10000)).click()
            assertTrue(device.wait(Until.hasObject(By.text("Protected fixture content 1")), 10000))
            openSessionDraft()
            control("api_session_open").click()
            assertTrue(device.wait(Until.hasObject(By.text("Protected fixture content 2")), 10000))
            control("browser_close_dock").click()
            requireNotNull(device.wait(Until.findObject(By.text("Fixture sign out")), 10000)).click()
            assertTrue(device.wait(Until.hasObject(By.text("Sign-in required")), 10000))
            openSessionDraft()
            control("identity_B").click()
            reveal("api_session_open")
            control("api_session_open").click()
            assertTrue(device.wait(Until.hasObject(By.text("This Browser session is no longer available.")), 10000))
            assertTrue(protectedLoads.get() == 3)

        }
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }
}

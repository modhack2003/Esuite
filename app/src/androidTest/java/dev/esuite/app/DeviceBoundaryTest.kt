package dev.esuite.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.esuite.app.browser.InspectorScripts
import dev.esuite.app.core.*
import dev.esuite.app.storage.WorkspaceVault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DeviceBoundaryTest {
    @Test fun androidKeystoreWorkspaceRoundTripAndTamperFailure() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = WorkspaceVault(context)
        vault.erase()
        try {
            val project = Project(name = "Device fixture", origin = "https://example.test")
            val workspace = Workspace(projects = listOf(project), findings = listOf(Finding(projectId = project.id, title = "Fixture", detail = "secret-device-evidence")))
            vault.save(workspace)
            assertEquals(workspace, WorkspaceVault(context).load())
            val file = File(context.noBackupFilesDir, "workspace.esv")
            val backup = File(file.path + ".bak")
            assertTrue(file.renameTo(backup))
            assertEquals(workspace, WorkspaceVault(context).load()) // recover legacy AtomicFile backup instead of resetting
            val bytes = file.readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("secret-device-evidence"))
            bytes[20] = (bytes[20].toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertThrows(Exception::class.java) { WorkspaceVault(context).load() }
            assertArrayEquals(bytes, file.readBytes()) // opening corruption must not silently overwrite it
        } finally { vault.erase() }
    }

    @Test fun isolatedWebViewCookiesAndInspectorRunOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val available = AtomicReference(false)
        instrumentation.runOnMainSync { available.set(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) }
        assumeTrue("System WebView lacks MULTI_PROFILE; this capability must not be claimed", available.get())
        val ready = CountDownLatch(1); val a = AtomicReference<WebView>(); val b = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            a.set(WebView(context)); b.set(WebView(context))
            val id = UUID.randomUUID().toString()
            WebViewCompat.setProfile(a.get(), "test_${id}_A"); WebViewCompat.setProfile(b.get(), "test_${id}_B")
            a.get().settings.javaScriptEnabled = true
            a.get().webViewClient = object : WebViewClient() { override fun onPageFinished(view: WebView, url: String) { ready.countDown() } }
            a.get().loadDataWithBaseURL("https://example.test/", "<html><button id='profile'>Profile</button></html>", "text/html", "UTF-8", null)
        }
        try {
            assertTrue("WebView fixture did not load", ready.await(20, TimeUnit.SECONDS))
            val cookieSet = CountDownLatch(1)
            instrumentation.runOnMainSync {
                WebViewCompat.getProfile(a.get()).cookieManager.setCookie("https://example.test/", "session=secret_A; Path=/; Secure; HttpOnly; SameSite=Lax") { ok -> assertTrue(ok); cookieSet.countDown() }
            }
            assertTrue(cookieSet.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val first = WebViewCompat.getProfile(a.get()).cookieManager
                val second = WebViewCompat.getProfile(b.get()).cookieManager
                assertTrue(first.getCookie("https://example.test/").contains("secret_A"))
                assertFalse(second.getCookie("https://example.test/").orEmpty().contains("secret_A"))
                if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) assertTrue(CookieManagerCompat.getCookieInfo(first, "https://example.test/").any { it.contains("httponly", true) })
            }
            fun evaluate(script: String): String {
                val latch = CountDownLatch(1); val value = AtomicReference("")
                instrumentation.runOnMainSync { a.get().evaluateJavascript(script) { value.set(it); latch.countDown() } }
                assertTrue("JS result timed out", latch.await(10, TimeUnit.SECONDS))
                return Json.parseToJsonElement(value.get()).jsonPrimitive.content
            }
            evaluate(InspectorScripts.select)
            evaluate("document.getElementById('profile').click(); 'selected'")
            assertTrue(evaluate(InspectorScripts.selection).contains("BUTTON"))
            assertEquals("", evaluate("document.cookie")) // HttpOnly is native-readable, not page-script readable
        } finally { instrumentation.runOnMainSync { a.get()?.destroy(); b.get()?.destroy() } }
    }
}

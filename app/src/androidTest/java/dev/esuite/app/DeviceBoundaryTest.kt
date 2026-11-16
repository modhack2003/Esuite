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
    @Test fun keyLossDuringAnOpenSessionCannotReplaceExistingVault() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = WorkspaceVault(context)
        vault.erase()
        try {
            vault.save(Workspace())
            val file = File(context.noBackupFilesDir, "workspace.esv")
            val previous = file.readBytes()
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry("esuite_workspace_aes_v1")
            assertThrows(IllegalStateException::class.java) { vault.save(Workspace()) }
            assertArrayEquals(previous, file.readBytes())
            assertFalse(store.containsAlias("esuite_workspace_aes_v1"))
        } finally { vault.erase() }
    }
    @Test fun silentAtomicFinishFailureCannotAcknowledgeUnreplacedBytes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = android.util.AtomicFile(File(context.noBackupFilesDir, "atomic-commit-fixture"))
        try {
            val previous = byteArrayOf(1, 2, 3)
            dev.esuite.app.storage.VerifiedAtomicWrite.write(file, previous)
            assertThrows(java.io.IOException::class.java) {
                dev.esuite.app.storage.VerifiedAtomicWrite.write(file, byteArrayOf(4, 5, 6)) { stream -> stream.close() }
            }
            assertArrayEquals(previous, file.baseFile.readBytes())
        } finally { file.delete() }
    }

    @Test fun deployedVersionOneMigratesAndUnknownNewerVersionRetainsBytes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = WorkspaceVault(context)
        vault.erase()
        try {
            val project = Project(name = "Upgrade fixture", origin = "https://example.test")
            val old = Workspace(version = 1, projects = listOf(project),
                findings = listOf(Finding(projectId = project.id, title = "Old note", detail = "Preserve all evidence")),
                savedRequests = listOf(SavedRequest(projectId = project.id, name = "Old request", draft = RequestDraft(url = project.origin))),
                identityConfigs = listOf(IdentityConfig(project.id, "A", listOf(Header("X-Test", "synthetic")))))
            vault.save(old)
            assertEquals(old.copy(version = WorkspaceSchema.CURRENT_VERSION), WorkspaceVault(context).load())
            vault.save(old.copy(version = 999))
            val file = File(context.noBackupFilesDir, "workspace.esv")
            val before = file.readBytes()
            assertThrows(IllegalArgumentException::class.java) { WorkspaceVault(context).load() }
            assertArrayEquals(before, file.readBytes())
        } finally { vault.erase() }
    }

    @Test fun encryptedSaveReturnsTheActualPrunedSnapshot() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = WorkspaceVault(context)
        vault.erase()
        try {
            val project = Project(name = "Prune fixture", origin = "https://example.test")
            val body = "x".repeat(Limits.RESPONSE_BYTES)
            val snapshot = Workspace(projects = listOf(project), exchanges = List(80) {
                Exchange(projectId = project.id, identity = "A", request = RequestDraft(url = project.origin, body = body), response = StoredBody(body)) })
            val persisted = vault.save(snapshot)
            assertTrue(persisted.exchanges.size < snapshot.exchanges.size)
            assertEquals(persisted, WorkspaceVault(context).load())
        } finally { vault.erase() }
    }

    @Test fun retainedFindingEvidenceSurvivesHistoryClearingOnRealKeystoreSaves() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = WorkspaceVault(context)
        vault.erase()
        try {
            val project = Project(name = "Retention fixture", origin = "https://example.test")
            val captured = Exchange(projectId = project.id, identity = "A",
                request = RequestDraft("POST", project.origin + "/api", listOf(Header("X-Test", "synthetic")), "retained-request"),
                status = 200, responseHeaders = listOf(Header("Content-Type", "application/json")), response = StoredBody("retained-response"))
            val snapshot = EvidenceRetention.snapshot(captured)
            val finding = Finding(projectId = project.id, title = "Retention", detail = "evidence must outlive history",
                evidenceIds = listOf(captured.id), evidence = listOf(snapshot))
            val history = List(Limits.HISTORY - 1) { Exchange(projectId = project.id, identity = "A", request = RequestDraft(url = project.origin + "/$it")) }
            vault.save(Workspace(projects = listOf(project), exchanges = listOf(captured) + history, findings = listOf(finding)))
            // clearHistory() then a save: the disposable region goes, the durable snapshot stays.
            vault.save(Workspace(projects = listOf(project), exchanges = emptyList(), findings = listOf(finding)))
            val reloaded = WorkspaceVault(context).load()
            assertEquals(1, reloaded.findings.size)
            assertTrue(reloaded.exchanges.isEmpty())
            val retained = reloaded.findings.single().evidence.single()
            assertEquals(Json.encodeToString(Exchange.serializer(), snapshot), Json.encodeToString(Exchange.serializer(), retained))
            assertTrue(EvidenceRetention.missing(reloaded.findings.single()).isEmpty())
        } finally { vault.erase() }
    }

    @Test fun deployedVersionTwoBackfillsRetainedEvidenceOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = WorkspaceVault(context)
        vault.erase()
        try {
            val project = Project(name = "Backfill fixture", origin = "https://example.test")
            val live = Exchange(projectId = project.id, identity = "A",
                request = RequestDraft(url = project.origin + "/api", body = "backfill-request"),
                status = 200, response = StoredBody("backfill-response"))
            val finding = Finding(projectId = project.id, title = "Old note", detail = "Repair my evidence",
                evidenceIds = listOf(live.id, "gone-on-device"))
            vault.save(Workspace(version = 2, projects = listOf(project), exchanges = listOf(live), findings = listOf(finding)))
            val migrated = WorkspaceVault(context).load()
            assertEquals(WorkspaceSchema.CURRENT_VERSION, migrated.version)
            val repaired = migrated.findings.single()
            assertEquals(listOf(EvidenceRetention.snapshot(live)), repaired.evidence)
            assertEquals(listOf("gone-on-device"), EvidenceRetention.missing(repaired))
            assertEquals(finding.evidenceIds, repaired.evidenceIds)
        } finally { vault.erase() }
    }

    @Test fun retainedEvidenceSurvivesTheEncryptedSavePrune() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = WorkspaceVault(context)
        vault.erase()
        try {
            val project = Project(name = "Prune retention fixture", origin = "https://example.test")
            val body = "x".repeat(Limits.RESPONSE_BYTES)
            val captured = Exchange(projectId = project.id, identity = "A",
                request = RequestDraft(url = project.origin + "/api", body = "pruned-request"), status = 200, response = StoredBody("pruned-response"))
            val finding = Finding(projectId = project.id, title = "Prune", detail = "evidence must outlive pruning",
                evidenceIds = listOf(captured.id), evidence = listOf(EvidenceRetention.snapshot(captured)))
            val snapshot = Workspace(projects = listOf(project), findings = listOf(finding), exchanges = List(80) {
                Exchange(projectId = project.id, identity = "A", request = RequestDraft(url = project.origin, body = body), response = StoredBody(body)) })
            val persisted = vault.save(snapshot)
            assertTrue(persisted.exchanges.size < snapshot.exchanges.size)
            assertEquals(snapshot.findings, persisted.findings)
            assertEquals(persisted, WorkspaceVault(context).load())
        } finally { vault.erase() }
    }

    @Test fun payloadMarkersInitializeAndSubstituteOnAndroidIcu() {
        val draft = RequestDraft(url = "https://example.test/?a={{P1}}&b={{P2}}&c={{P3}}&d={{P4}}")
        val plan = dev.esuite.app.automation.RequestPlans.payloadsFromText(draft,
            listOf("a", "b", "c", "d"), dev.esuite.app.automation.PayloadMode.ONE_POSITION)
        assertEquals(4, plan.size)
        assertEquals("https://example.test/?a=a&b=b&c=c&d=d", plan.first().url)
    }
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

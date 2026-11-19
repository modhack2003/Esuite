package dev.esuite.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.CookieManagerCompat
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.esuite.app.browser.BrowserProfiles
import dev.esuite.app.browser.InspectorScripts
import dev.esuite.app.core.*
import dev.esuite.app.storage.WorkspaceVault
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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

    // QA-05: a result that lands after an A→B identity switch must stay recorded in Traffic but must
    // NOT be applied under identity B. The vault is pre-seeded with an HTTP MockWebServer origin so
    // the real AppViewModel.send() runs end to end without TLS. TWO separate policies have to allow
    // that: ScopePolicy, which permits http at the engine because the HTTPS-only gate lives in
    // createProject and not on a loaded project (as the JVM ApiEngineTest already relies on); and the
    // platform network security policy, which governs the socket itself and rejects cleartext before
    // one is opened. The shipped config forbids cleartext everywhere, so these cases depend on the
    // DEBUG-only 127.0.0.1 exception in app/src/debug/res/xml/network_security_config.xml
    // (testBuildType = "debug"). They cannot run against a release build, by design.
    private fun awaitState(model: AppViewModel, timeoutMs: Long = 20_000, condition: (AppState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition(model.state.value)) return
            Thread.sleep(25)
        }
        assertTrue("Timed out waiting for ViewModel state", condition(model.state.value))
    }

    private fun loadedViewModel(server: MockWebServer): Pair<AppViewModel, Project> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val application = context.applicationContext as android.app.Application
        val vault = WorkspaceVault(context); vault.erase()
        val project = Project(name = "Attribution fixture", origin = server.url("/").toString())
        vault.save(Workspace(projects = listOf(project)))
        val holder = AtomicReference<AppViewModel>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { holder.set(AppViewModel(application)) }
        val model = holder.get()
        awaitState(model) { it.ready && it.projectId == project.id }
        return model to project
    }

    @Test fun delayedResponseArrivingAfterIdentitySwitchIsRecordedButNotApplied() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val server = MockWebServer(); server.start()
        val (model, _) = loadedViewModel(server)
        try {
            instrumentation.runOnMainSync { model.draft(RequestDraft(url = server.url("/api").toString())) }
            server.enqueue(MockResponse().setResponseCode(200).setBody("late-A-response").setBodyDelay(1500, TimeUnit.MILLISECONDS))
            val before = model.state.value.workspace.exchanges.size
            instrumentation.runOnMainSync { model.send() }
            awaitState(model) { it.busy }
            instrumentation.runOnMainSync { model.identity("B") } // switch while the A request is still in flight
            awaitState(model) { !it.busy }
            val state = model.state.value
            assertEquals("B", state.identity)
            // The A response is still recorded in Traffic under identity A ...
            assertTrue(state.workspace.exchanges.size > before)
            assertTrue(state.workspace.exchanges.any { it.identity == "A" && it.status == 200 && it.response.value == "late-A-response" })
            // ... but it was never applied under identity B, and the message says so without claiming leakage.
            assertNull(state.selected)
            assertTrue(state.message.contains("recorded in Traffic and was not applied"))
            assertFalse(state.message.contains("cookie", true))
        } finally { model.engine.close(); server.shutdown(); WorkspaceVault(context).erase() }
    }

    @Test fun delayedErrorArrivingAfterIdentitySwitchIsRecordedButNotApplied() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val server = MockWebServer(); server.start()
        val (model, _) = loadedViewModel(server)
        try {
            instrumentation.runOnMainSync { model.draft(RequestDraft(url = server.url("/api").toString())) }
            server.enqueue(MockResponse().setBody("partial").setBodyDelay(1200, TimeUnit.MILLISECONDS).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            val before = model.state.value.workspace.exchanges.size
            instrumentation.runOnMainSync { model.send() }
            awaitState(model) { it.busy }
            instrumentation.runOnMainSync { model.identity("B") }
            awaitState(model) { !it.busy }
            val state = model.state.value
            assertEquals("B", state.identity)
            // The failed attempt is recorded under identity A as an error exchange ...
            assertTrue(state.workspace.exchanges.size > before)
            assertTrue(state.workspace.exchanges.any { it.identity == "A" && it.error != null })
            // ... and not applied under identity B.
            assertNull(state.selected)
            assertTrue(state.message.contains("recorded in Traffic and was not applied"))
        } finally { model.engine.close(); server.shutdown(); WorkspaceVault(context).erase() }
    }

    @Test fun cancellingAnInFlightRequestRecordsNothingAndKeepsCancellationSemantics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val server = MockWebServer(); server.start()
        val (model, _) = loadedViewModel(server)
        try {
            instrumentation.runOnMainSync { model.draft(RequestDraft(url = server.url("/api").toString())) }
            server.enqueue(MockResponse().setResponseCode(200).setBody("never-applied").setBodyDelay(3000, TimeUnit.MILLISECONDS))
            val before = model.state.value.workspace.exchanges.size
            instrumentation.runOnMainSync { model.send() }
            awaitState(model) { it.busy }
            instrumentation.runOnMainSync { model.cancelRequest() }
            awaitState(model) { !it.busy }
            val state = model.state.value
            // Cancellation happens before completion, so no exchange is recorded and selected is untouched.
            assertEquals(before, state.workspace.exchanges.size)
            assertNull(state.selected)
            assertEquals("Request cancelled", state.message)
        } finally { model.engine.close(); server.shutdown(); WorkspaceVault(context).erase() }
    }

    // QA-05's acceptance says "including tasks", so the runner gates need their own deterministic
    // case: the three above drive send() only, and ResultAttribution.accepts() is covered in
    // isolation on the JVM. runPayloads() is the runner chosen here because it needs no saved
    // requests. Both legs must still reach Traffic under the identity they ran as — recording is
    // unconditional in every path — while the superseded message replaces the completion text.
    @Test fun parameterTaskSupersededMidRunStillRecordsEveryLegAndReportsTheSwitch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val server = MockWebServer(); server.start()
        val (model, _) = loadedViewModel(server)
        try {
            // Built by string concatenation, not server.url(path): HttpUrl would percent-encode the
            // {{P1}} braces and RequestPlans' marker regex would no longer match.
            instrumentation.runOnMainSync { model.draft(RequestDraft(url = server.url("/api").toString() + "?p={{P1}}")) }
            repeat(2) { leg -> server.enqueue(MockResponse().setResponseCode(200).setBody("leg-$leg").setBodyDelay(1200, TimeUnit.MILLISECONDS)) }
            val before = model.state.value.workspace.exchanges.size
            instrumentation.runOnMainSync {
                model.runPayloads(listOf("first\nsecond"), dev.esuite.app.automation.PayloadMode.ONE_POSITION)
            }
            awaitState(model) { it.busy }
            instrumentation.runOnMainSync { model.identity("B") } // switch while the first leg is in flight
            awaitState(model, timeoutMs = 30_000) { !it.busy }
            val state = model.state.value
            assertEquals("B", state.identity)
            // Every leg ran as identity A and every leg is recorded; nothing was dropped.
            assertEquals(before + 2, state.workspace.exchanges.size)
            assertEquals(2, state.workspace.exchanges.count { it.identity == "A" && it.status == 200 && it.source == "Parameter runner" })
            // The superseded message replaced the completion text, and progress never advanced past
            // the switch, so no leg was applied under identity B.
            assertTrue(state.message.contains("recorded in Traffic and was not applied"))
            assertFalse(state.message.contains("Parameter task complete"))
            assertEquals("Task stopped / completed; captured results retained", state.taskProgress)
        } finally { model.engine.close(); server.shutdown(); WorkspaceVault(context).erase() }
    }

    // QA-08: the real ProfileStore contract against the installed provider. deleteProfile rejects a
    // profile the provider still holds loaded — destroying every WebView is not sufficient and there
    // is no API to unload one — so cleanup must REPORT the refusal rather than throw, and must never
    // reach the default profile or a profile ESuite did not create. Gated on MULTI_PROFILE exactly as
    // isolatedWebViewCookiesAndInspectorRunOnAndroid is; the capability is never claimed.
    private fun assumeMultiProfile() {
        val available = AtomicReference(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { available.set(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) }
        assumeTrue("System WebView lacks MULTI_PROFILE; this capability must not be claimed", available.get())
    }

    @Test fun profileLoadedIntoALiveWebViewIsReportedPendingInsteadOfThrowing() {
        assumeMultiProfile()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val projectId = UUID.randomUUID().toString()
        val name = ProfileNames.name(projectId, "A")
        val view = AtomicReference<WebView>()
        val cleanup = AtomicReference<BrowserProfiles.CleanupResult>()
        try {
            instrumentation.runOnMainSync {
                view.set(WebView(context))
                WebViewCompat.setProfile(view.get(), name) // loads the profile into memory
                cleanup.set(BrowserProfiles.clearProject(projectId))
            }
            val result = cleanup.get()
            assertFalse("MULTI_PROFILE was asserted above", result.unsupported)
            // Reported, not thrown; identity B was never created so it is not a candidate.
            assertEquals(listOf(name), result.pending)
            assertTrue(result.deleted.isEmpty())
            assertTrue("a pending profile must still be reported by the provider",
                result.describe().contains("loaded in memory") && result.describe().contains("restarts"))
            instrumentation.runOnMainSync { assertTrue(ProfileStore.getInstance().allProfileNames.contains(name)) }
        } finally {
            instrumentation.runOnMainSync { view.get()?.destroy(); runCatching { ProfileStore.getInstance().deleteProfile(name) } }
        }
    }

    @Test fun orphanSweepAccountsForADestroyedViewsProfileWithoutTouchingTheDefault() {
        assumeMultiProfile()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = ProfileNames.name(UUID.randomUUID().toString(), "A")
        val view = AtomicReference<WebView>()
        val cleanup = AtomicReference<BrowserProfiles.CleanupResult>()
        try {
            instrumentation.runOnMainSync { view.set(WebView(context)); WebViewCompat.setProfile(view.get(), name) }
            instrumentation.runOnMainSync { view.get().destroy() }
            // The restart sweep: no live project ids at all, so this profile is an orphan.
            instrumentation.runOnMainSync { cleanup.set(BrowserProfiles.sweepOrphans(emptySet())) }
            val result = cleanup.get()
            // Accounted for in exactly one bucket, never silently ignored. Whether a provider
            // releases the profile on destroy or only on process restart is a PROVIDER property, so
            // both outcomes are legal here and the store must agree with whichever was reported.
            assertTrue("the orphan must be reported as deleted or pending, not dropped",
                result.deleted.contains(name) != result.pending.contains(name))
            instrumentation.runOnMainSync {
                val remaining = ProfileStore.getInstance().allProfileNames
                if (result.deleted.contains(name)) assertFalse(remaining.contains(name)) else assertTrue(remaining.contains(name))
                assertTrue("the default profile must survive any sweep", remaining.contains(Profile.DEFAULT_PROFILE_NAME))
            }
            assertFalse(result.deleted.contains(Profile.DEFAULT_PROFILE_NAME) || result.pending.contains(Profile.DEFAULT_PROFILE_NAME))
        } finally {
            instrumentation.runOnMainSync { runCatching { ProfileStore.getInstance().deleteProfile(name) } }
        }
    }

    @Test fun clearAllEsuiteOwnedSkipsForeignAndDefaultProfiles() {
        assumeMultiProfile()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val foreign = "qa_not_esuite_profile"
        val owned = ProfileNames.name(UUID.randomUUID().toString(), "B")
        val cleanup = AtomicReference<BrowserProfiles.CleanupResult>()
        try {
            instrumentation.runOnMainSync {
                ProfileStore.getInstance().getOrCreateProfile(foreign)
                ProfileStore.getInstance().getOrCreateProfile(owned)
                cleanup.set(BrowserProfiles.clearAllEsuiteOwned())
            }
            val result = cleanup.get()
            val touched = result.deleted + result.pending
            assertTrue("only esuite_-prefixed profiles may be touched", touched.all { it.startsWith(ProfileNames.PREFIX) })
            assertTrue(touched.contains(owned))
            assertFalse(touched.contains(foreign))
            assertFalse(touched.contains(Profile.DEFAULT_PROFILE_NAME))
            instrumentation.runOnMainSync {
                val remaining = ProfileStore.getInstance().allProfileNames
                assertTrue("a foreign profile must survive", remaining.contains(foreign))
                assertTrue("the default profile must survive", remaining.contains(Profile.DEFAULT_PROFILE_NAME))
            }
        } finally {
            instrumentation.runOnMainSync {
                runCatching { ProfileStore.getInstance().deleteProfile(foreign) }
                runCatching { ProfileStore.getInstance().deleteProfile(owned) }
            }
        }
    }
}

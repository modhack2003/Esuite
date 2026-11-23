package dev.esuite.app.core

import dev.esuite.app.browser.BrowserRequestLog
import dev.esuite.app.browser.RequestEventKind
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class AssessmentAuditTest {
    @Test fun browserSnapshotAndMapKeepProvenanceAndExcludeClippedPaths() {
        val site = Project(id = "site", name = "Fixture", origin = "https://example.test")
        val prefix = "/" + "p".repeat(279)
        val log = BrowserRequestLog { 10 }
        log.page(site.origin.toHttpUrl())
        log.add("GET", (site.origin + prefix).toHttpUrl())
        log.add("GET", (site.origin + prefix + "/longer").toHttpUrl())
        log.add("", (site.origin + prefix).toHttpUrl(), kind = RequestEventKind.Navigation)
        val snapshot = requireNotNull(log.snapshot())
        log.page("https://another.test".toHttpUrl())
        assertNull(log.snapshot())
        assertEquals(site.origin, snapshot.origin)
        assertEquals(3, snapshot.rows.map { it.id }.distinct().size)
        assertEquals(1, snapshot.rows.count { it.pathClipped })

        val capture = Exchange(id = "capture", projectId = site.id, identity = "A",
            request = RequestDraft(url = site.origin + prefix + "?private=sentinel"), status = 200)
        val workspace = Workspace(exchanges = listOf(capture, capture.copy(id = "other", projectId = "other")))
        val rows = activityRecords(site, workspace, BrowserActivity(site.id, snapshot))
        assertEquals(4, rows.size)
        assertEquals(2, siteMap(rows).size)
        assertEquals(2, siteMap(rows).getValue(Endpoint(site.origin, prefix, "GET")).size)
        assertEquals(1, siteMap(rows).getValue(Endpoint(site.origin, prefix, null)).size)
        assertFalse(siteMap(rows).keys.any { "sentinel" in it.path })
        assertEquals(1, activityRecords(site, workspace, BrowserActivity("other", snapshot)).size)
    }

    @Test fun reviewUsesOneVersionedRuleOutcomeAndRetainsResponseProvenance() {
        val headers = listOf(Header("StRiCt-TrAnSpOrT-SeCuRiTy", "max-age=0"),
            Header("Set-Cookie", "first=private"), Header("set-cookie", "second=private; Secure; HttpOnly; SameSite=Lax"))
        val response = Exchange(projectId = "site", identity = "A", request = RequestDraft(url = "https://example.test/"),
            status = 200, responseHeaders = headers, response = StoredBody("partial", truncated = true))
        val quick = ResponseChecks.run(ResponseCheck.Quick, response)
        val standalone = ResponseChecks.run(ResponseCheck.Headers, response)
        val hsts = standalone.single { it.ruleId == "headers.strict-transport-security" }
        assertEquals(CheckStatus.Review, hsts.status)
        assertTrue(hsts in quick)
        assertEquals(2, quick.count { it.ruleId == "cookies.flags" })
        assertFalse(quick.any { "private" in it.detail })
        assertEquals("response.empty-headers", ResponseChecks.run(ResponseCheck.Quick, response.copy(responseHeaders = emptyList())).single().ruleId)
        assertEquals("response.evidence", ResponseChecks.run(ResponseCheck.Quick, response.copy(source = "Browser metadata")).single().ruleId)
        val other = response.copy(id = "other", identity = "B")
        val workspace = Workspace(exchanges = listOf(other, response))
        assertEquals(response.id, latestReviewableResponse(workspace, "site", "A")?.id)
        assertNull(latestReviewableResponse(workspace, "missing", "A"))
    }

    @Test fun previewAndRetainedBodiesRespectByteBounds() {
        val text = "x".repeat(ACTIVITY_PREVIEW_BYTES - 1) + "😀"
        assertEquals(text.dropLast(2), utf8Preview(text, ACTIVITY_PREVIEW_BYTES))
        val exchange = Exchange(projectId = "site", identity = "A", request = RequestDraft(body = "x".repeat(17000)), response = StoredBody("y".repeat(17000)))
        val retained = EvidenceRetention.snapshot(exchange)
        assertEquals(16384, retained.request.body.length)
        assertEquals(16384, retained.response.value.length)
        assertTrue(retained.response.truncated)
    }
}

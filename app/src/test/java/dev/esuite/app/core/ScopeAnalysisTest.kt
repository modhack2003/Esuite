package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test

class ScopeAnalysisTest {
    @Test fun exactOriginScopeRejectsHostSuffixesDowngradesUserInfoAndPorts() {
        val scope = ScopePolicy("https://example.test")
        assertTrue(scope.allows("https://example.test/api?a=1"))
        listOf("https://example.test.evil.test", "http://example.test", "https://example.test:8443/", "https://u:p@example.test", "file:///etc/passwd", "https://example.test/#x").forEach { assertFalse(it, scope.allows(it)) }
        assertThrows(IllegalArgumentException::class.java) { ScopePolicy("https://example.test/path") }
    }
    @Test fun parserRejectsHeaderInjectionAndOversizedRequests() {
        assertThrows(IllegalArgumentException::class.java) { DraftParser.validateHeaders(listOf(Header("X-Test", "a\r\nInjected: yes"))) }
        assertThrows(IllegalArgumentException::class.java) { DraftParser.validate(RequestDraft("POST", body = "a".repeat(Limits.REQUEST_BYTES + 1))) }
        assertThrows(IllegalArgumentException::class.java) { DraftParser.raw("GET https://evil.test/ HTTP/1.1\n\n", "https://example.test") }
        val d = DraftParser.raw("POST /api HTTP/1.1\r\nHost: example.test\r\nX-Test: yes\r\n\r\nhello", "https://example.test")
        assertEquals("https://example.test/api", d.url); assertEquals("hello", d.body); assertEquals(1, d.headers.size)
    }
    @Test fun observationsAndExportsDoNotOverclaimOrLeakKnownSecrets() {
        val e = Exchange(projectId = "p", identity = "A", request = RequestDraft(url = "https://example.test/?unusual_secret=yes", headers = listOf(Header("Authorization", "Bearer secret")), body = "private"), status = 200,
            responseHeaders = listOf(Header("Content-Type", "text/html"), Header("Set-Cookie", "session=secret; Path=/")), response = StoredBody("private"))
        assertTrue(PassiveAnalyzer.inspect(e).any { it.title == "Cookie missing HttpOnly" })
        val redacted = Redactor.exchange(e)
        assertEquals("https://example.test/", redacted.request.url)
        assertEquals("[REDACTED]", redacted.request.headers.first().value)
        assertEquals("[BODY OMITTED]", redacted.response.value)
        assertEquals("[REDACTED]", redacted.responseHeaders.last().value)
        assertTrue(ResponseComparer.compare(e, e.copy(identity = "B")).contains("Body: equal"))
        // A sanitized export must omit free text, paths, bodies and header values from capture
        // history AND from evidence retained on a finding.
        val project = Project(id = "p", name = "Export fixture", origin = "https://example.test")
        val evidence = e.copy(id = "ex-export", projectId = project.id,
            request = e.request.copy(url = "https://example.test/sensitive-path?unusual_secret=yes", headers = listOf(Header("X-Custom", "leaky-header-value"))),
            response = StoredBody("private-response"))
        val workspace = Workspace(projects = listOf(project), exchanges = listOf(evidence),
            findings = listOf(Finding(id = "f-export", projectId = project.id, title = "Secret observation title", detail = "secret free text",
                evidenceIds = listOf(evidence.id), evidence = listOf(EvidenceRetention.snapshot(evidence)))))
        val report = ReportExporter.sanitized(workspace, project.id)
        listOf("sensitive-path", "unusual_secret", "leaky-header-value", "private-response", "secret free text", "Secret observation title")
            .forEach { assertFalse("sanitized export leaked $it", report.contains(it)) }
        assertTrue(report.contains("[PATH OMITTED]") && report.contains("[BODY OMITTED]"))
    }
    @Test fun tokenStatisticsAndClaimsRemainUnverified() {
        assertTrue(TokenTools.sample(listOf("aaa", "aaa")).contains("Duplicate samples: 1"))
        assertTrue(TokenTools.jwtClaims("eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0In0.").startsWith("UNVERIFIED"))
    }
}

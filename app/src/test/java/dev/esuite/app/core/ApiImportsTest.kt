package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test

class ApiImportsTest {
    @Test fun curlImportParsesQuotesButNeverExecutesShellOrAcceptsUnknownFlags() {
        val d = ApiImports.curl("curl -X POST 'https://example.test/api' -H 'Content-Type: application/json' --data-raw '{\"x\":\"a b\"}'", "https://example.test")
        assertEquals("POST", d.method); assertEquals("{\"x\":\"a b\"}", d.body)
        assertThrows(IllegalArgumentException::class.java) { ApiImports.curl("curl -L https://example.test", "https://example.test") }
        assertThrows(IllegalArgumentException::class.java) { ApiImports.curl("curl https://example.test ; echo bad", "https://example.test") }
        assertThrows(IllegalArgumentException::class.java) { ApiImports.curl("curl https://outside.test", "https://example.test") }
    }
    @Test fun openApiPreservesBasePathAndRejectsOutOfScopeServers() {
        val p = Project(name = "test", origin = "https://example.test")
        val spec = """{"openapi":"3.0.3","servers":[{"url":"https://example.test/v1"}],"paths":{"/users":{"get":{}}}}"""
        assertEquals("https://example.test/v1/users", ApiImports.openApi(spec, p).single().draft.url)
        assertThrows(IllegalArgumentException::class.java) { ApiImports.openApi(spec.replace("https://example.test/v1", "https://outside.test/v1"), p) }
    }
    @Test fun cloudObservationsStayContextual() {
        val report = CloudConfigReview.review("""{"Statement":[{"Effect":"Allow","Action":"s3:*","Resource":"*"},{"Effect":"Deny","Action":"*","Resource":"*"}]}""")
        assertTrue(report.contains("Statement 0 allows wildcard")); assertFalse(report.contains("Statement 1")); assertTrue(report.contains("No provider account was contacted"))
    }
    @Test fun harImportsOnlyMatchingOriginWithoutTreatingItAsObservedTraffic() {
        val p = Project(name = "test", origin = "https://example.test")
        val input = """{"log":{"entries":[{"request":{"method":"GET","url":"https://example.test/api","headers":[]}},{"request":{"method":"GET","url":"https://outside.test/","headers":[]}}]}}"""
        val requests = ApiImports.har(input, p)
        assertEquals(1, requests.size); assertEquals("https://example.test/api", requests.single().draft.url)
    }
    @Test fun identityChangesStripKnownAndConfiguredAuthWithoutChangingBodies() {
        val d = RequestDraft(headers = listOf(Header("Cookie", "A"), Header("X-Api-Key", "A"), Header("X-Custom-Login", "A"), Header("Accept", "application/json")), body = "review-me")
        val result = CredentialPolicy.strip(d, listOf(IdentityConfig("p", "A", listOf(Header("X-Custom-Login", "A")))))
        assertEquals(listOf(Header("Accept", "application/json")), result.headers); assertEquals("review-me", result.body)
    }
}

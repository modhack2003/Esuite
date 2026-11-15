package dev.esuite.app.core

import dev.esuite.app.automation.*
import org.junit.Assert.*
import org.junit.Test

class PlansImportsTest {
    @Test fun fourTextListsPreservePositionsAcrossUrlHeadersAndBody() {
        val template = RequestDraft(url = "https://example.test/{{P1}}", headers = listOf(Header("X-Test", "{{P3}}")), body = "{{P2}}:{{P4}}")
        val inputs = listOf("a\nb", "c\nd", "e\nf", "g\nh")
        assertEquals(8, RequestPlans.payloadsFromText(template, inputs, PayloadMode.ONE_POSITION).size)
        assertEquals(16, RequestPlans.payloadsFromText(template, inputs, PayloadMode.COMBINATIONS).size)
        val paired = RequestPlans.payloadsFromText(template, inputs, PayloadMode.PAIRED)
        assertEquals("https://example.test/b", paired.last().url)
        assertEquals("d:h", paired.last().body)
        assertEquals("f", paired.last().headers.single().value)
        val broadcast = RequestPlans.payloadsFromText(template, inputs, PayloadMode.BROADCAST)
        assertEquals("b:b", broadcast.last().body)
        assertEquals("b", broadcast.last().headers.single().value)
    }
    @Test fun sparseTextListsRejectMissingValuesAndOversizedPlans() {
        val template = RequestDraft(url = "https://example.test/{{P4}}")
        assertEquals("https://example.test/z", RequestPlans.payloadsFromText(template, listOf("", "", "", "z"), PayloadMode.PAIRED).single().url)
        assertEquals("https://example.test/a", RequestPlans.payloadsFromText(template, listOf("a", "", "", ""), PayloadMode.BROADCAST).single().url)
        assertThrows(IllegalArgumentException::class.java) { RequestPlans.payloadsFromText(template, listOf("a", "", "", ""), PayloadMode.PAIRED) }
        assertThrows(IllegalArgumentException::class.java) { RequestPlans.payloadsFromText(template, listOf("", "", "", "z"), PayloadMode.BROADCAST) }
        assertThrows(IllegalArgumentException::class.java) { RequestPlans.payloadsFromText(template, listOf("", "", "", List(51) { "z" }.joinToString("\n")), PayloadMode.PAIRED) }
        val all = RequestDraft(url = "https://example.test/{{P1}}/{{P2}}/{{P3}}/{{P4}}")
        assertThrows(IllegalArgumentException::class.java) { RequestPlans.payloadsFromText(all, List(4) { "a\nb\nc" }, PayloadMode.COMBINATIONS) }
    }
    @Test fun payloadModesBoundCountsAndSubstituteWithoutRecursiveExpansion() {
        val template = RequestDraft(url = "https://example.test/?a={{P1}}&b={{P2}}")
        val lists = listOf(listOf("1", "2"), listOf("x", "y", "z"))
        assertEquals(5, RequestPlans.payloads(template, lists, PayloadMode.ONE_POSITION).size)
        assertEquals(2, RequestPlans.payloads(template, lists, PayloadMode.BROADCAST).size)
        assertEquals(2, RequestPlans.payloads(template, lists, PayloadMode.PAIRED).size)
        assertEquals(6, RequestPlans.payloads(template, lists, PayloadMode.COMBINATIONS).size)
        assertThrows(IllegalArgumentException::class.java) { RequestPlans.payloads(template, listOf(List(10) { "a" }, List(10) { "b" }), PayloadMode.COMBINATIONS) }
        assertEquals("https://example.test/?a={{P2}}&b=x", RequestPlans.payloads(template, listOf(listOf("{{P2}}"), listOf("x")), PayloadMode.PAIRED).first().url)
    }
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
    @Test fun macroJsonPointerAndCloudObservationsStayContextual() {
        assertEquals("abc", RequestPlans.extractToken("""{"auth":{"access_token":"abc"}}""", "/auth/access_token"))
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

package dev.esuite.app.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApiImportsTest {
    private val origin = "https://example.test"

    @Test fun rawHttpKeepsTheBodyUntouched() {
        repeat(25) { index ->
            val body = "fixture $index\r\nnext\nlast\r\n\r\n"
            listOf("\r\n", "\n").forEach { separator ->
                val message = "POST /fixture HTTP/1.1${separator}Host: example.test$separator$separator$body"
                val draft = DraftParser.raw(message, origin)
                assertEquals(body, draft.body)
                assertTrue(draft.headers.isEmpty())
            }
        }
    }

    @Test fun curlPreservesSupportedRepeatedDataAndLiteralAtSigns() {
        repeat(25) { index ->
            val repeated = ApiImports.curl("curl --data 'a=$index' -d 'b=two' $origin", origin)
            assertEquals("POST", repeated.method)
            assertEquals("a=$index&b=two", repeated.body)
            val raw = ApiImports.curl("curl --data-raw '@item$index' $origin", origin)
            assertEquals("@item$index", raw.body)
            assertThrows(IllegalArgumentException::class.java) {
                ApiImports.curl("curl --data-binary '@item$index' $origin", origin)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiImports.curl("curl --data-binary first --data second $origin", origin)
        }
    }

    @Test fun curlRejectsEveryCombinationOfMultipleUrls() {
        repeat(25) { index ->
            val first = "$origin/first-$index"
            val second = "$origin/second-$index"
            listOf("--url $first --url $second", "$first --url $second", "--url $first $second", "$first $second")
                .forEach { args ->
                    val failure = assertThrows(IllegalArgumentException::class.java) { ApiImports.curl("curl $args", origin) }
                    assertEquals("Only one URL is supported", failure.message)
                }
        }
    }

    @Test fun documentReaderDeliversValidLargeOpenApiAndHarInputsToTheirParsers() = runBlocking {
        val project = Project(name = "Fixture", origin = origin)
        repeat(25) { index ->
            val padding = "x".repeat(Editor.ToolsInput.valueChars + index)
            val openApi = """{"openapi":"3.0.3","paths":{"/open":{"get":{"description":"$padding"}}}}"""
            val har = """{"_comment":"$padding","log":{"entries":[{"request":{"method":"GET","url":"$origin/har"}}]}}"""
            val spec = openApi.byteInputStream().use { readCollectionDocument(it) }
            val capture = har.byteInputStream().use { readCollectionDocument(it) }
            assertEquals("$origin/open", ApiImports.openApi(spec, project).single().draft.url)
            assertEquals("$origin/har", ApiImports.har(capture, project).single().draft.url)
        }
    }

    @Test fun documentReaderHonorsExactByteLimitAndRejectsMalformedUtf8() {
        runBlocking {
            val bytes = ByteArray(ApiImports.COLLECTION_BYTES) { 'x'.code.toByte() }
            assertEquals(bytes.size, bytes.inputStream().use { readCollectionDocument(it) }.length)
            assertEquals("{}", "\uFEFF{}".byteInputStream().use { readCollectionDocument(it) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ByteArray(ApiImports.COLLECTION_BYTES + 1).inputStream().use { readCollectionDocument(it) } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { byteArrayOf(0xc3.toByte()).inputStream().use { readCollectionDocument(it) } }
        }
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

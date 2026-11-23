package dev.esuite.app.browser

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class BrowserAddressTest {
    @Test fun distinguishesPlainDomainsAndSearchTerms() {
        assertEquals("https://example.test/path", BrowserAddress.resolve("example.test/path").toString())
        assertEquals("https://example.test:9443", BrowserAddress.origin(BrowserAddress.resolve("https://example.test:9443/path?q=1#ref")))
        val query = BrowserAddress.resolve("test α & β")
        assertEquals("duckduckgo.com", query.host)
        assertEquals("test α & β", query.queryParameter("q"))
    }

    @Test fun rejectsMaliciousOrUnsupportedAddressFormsWithoutTurningThemIntoSearches() {
        for (address in listOf("http://example.test", "https://name:secret@example.test", "javascript:alert(1)",
            "file:///etc/passwd", "https://example.test\\outside.test", "https://example.test\noutside.test")) {
            assertThrows(address, IllegalArgumentException::class.java) { BrowserAddress.resolve(address) }
        }
    }

    @Test fun requestDetailsAreBoundedAndUseOnlyTheCurrentOriginAndPath() {
        val first = "https://example.test/path?q=private#fragment".toHttpUrl()
        val log = BrowserRequestLog()
        log.page(first)
        log.add("GET", first)
        log.add("GET", "https://example.test:8443/another-port".toHttpUrl())
        log.add("GET", "https://outside.test/subresource".toHttpUrl())
        assertEquals(listOf("/path"), log.events.value.map { it.path })
        repeat(62) { log.add("GET", first.newBuilder().encodedPath("/item/$it").build()) }
        assertEquals(60, log.events.value.size)
        assertEquals("/item/61", log.events.value.first().path)
        log.add("GET", first, true, RequestEventKind.HttpError, 404)
        assertEquals(404, log.events.value.first().code)
        assertTrue(log.events.value.first().mainFrame)
        assertEquals(RequestEventKind.HttpError, log.events.value.first().kind)
        log.clear()
        assertTrue(log.events.value.isEmpty())
        log.add("GET", first)
        log.page("https://outside.test/".toHttpUrl())
        assertTrue(log.events.value.isEmpty())
    }

    @Test fun cookieInspectionDisclosesNamesAndKnownFlagsWithoutValues() {
        assertEquals(listOf("session · Secure · HttpOnly · SameSite=Lax"),
            cookieMetadata(listOf("session=private; Path=/; Domain=example.test; Secure; HttpOnly; SameSite=Lax"), true))
        assertEquals(listOf("theme", "test"), cookieMetadata(listOf("theme=private", "test=another"), false))
    }
}

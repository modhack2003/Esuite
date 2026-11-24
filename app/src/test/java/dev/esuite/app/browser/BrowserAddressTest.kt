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
        assertEquals(listOf("/path"), log.state.value.rows.map { it.path })
        repeat(302) { log.add("GET", first.newBuilder().encodedPath("/item/$it").build()) }
        assertEquals(300, log.state.value.rows.size)
        assertEquals("/item/301", log.state.value.rows.first().path)
        log.add("GET", first, true, RequestEventKind.HttpError, 404)
        assertEquals(404, log.state.value.rows.first().code)
        assertTrue(log.state.value.rows.first().mainFrame)
        assertEquals(RequestEventKind.HttpError, log.state.value.rows.first().kind)
        log.clear()
        assertTrue(log.state.value.rows.isEmpty())
        log.add("GET", first)
        log.page("https://outside.test/".toHttpUrl())
        assertEquals("https://outside.test", log.state.value.origin)
        assertTrue(log.state.value.rows.isEmpty())
    }

    @Test fun cookieInspectionDisclosesNamesAndKnownFlagsWithoutValues() {
        assertEquals(listOf("session · Secure · HttpOnly · SameSite=Lax"),
            cookieMetadata(listOf("session=private; Path=/; Domain=example.test; Secure; HttpOnly; SameSite=Lax"), true))
        assertEquals(listOf("theme", "test"), cookieMetadata(listOf("theme=private", "test=another"), false))
    }

    @Test fun networkControlsKeepRepeatedObservationsAndDiscardObsoleteSamples() {
        var now = 1100L
        val first = "https://example.test/home".toHttpUrl()
        val next = "https://example.test/next".toHttpUrl()
        val log = BrowserRequestLog { now }
        val scope = BrowserAddress.origin(first)
        val timing = ResourceTiming(1, "/repeat", "fetch", null, 20.0, 10.0, null, "", null, null, null, null, null, null, null)
        val batch = TimingBatch(1000.0, listOf(timing, timing.copy(id = 2)))
        log.page(first)
        val initial = requireNotNull(log.token())
        log.addTimings(initial, scope, batch)
        log.addTimings(initial, scope, batch)
        assertEquals(2, log.state.value.rows.size)

        log.setRecording(false)
        log.add("GET", first)
        log.addTimings(initial, scope, batch.copy(timeOrigin = 1200.0))
        assertNull(log.token())
        assertEquals(2, log.state.value.rows.size)
        now = 1300L
        log.setRecording(true)
        log.addTimings(requireNotNull(log.token()), scope, batch.copy(timeOrigin = 1250.0))
        assertEquals(2, log.state.value.rows.size)
        log.setPreserve(true)
        log.page(next)
        val current = requireNotNull(log.token())
        log.addTimings(current, scope, batch.copy(timeOrigin = 1300.0))
        assertEquals(4, log.state.value.rows.size)

        now = 1400L
        log.clear()
        log.addTimings(current, scope, batch.copy(timeOrigin = 1500.0))
        log.addTimings(requireNotNull(log.token()), scope, batch.copy(timeOrigin = 1300.0))
        assertTrue(log.state.value.rows.isEmpty())
        log.add("GET", next, kind = RequestEventKind.LoadError, code = -2)
        assertTrue(log.state.value.rows.single().failed)
        assertNull(log.state.value.rows.single().status)
        log.page("https://outside.test/".toHttpUrl())
        log.addTimings(requireNotNull(log.token()), scope, batch.copy(timeOrigin = 1500.0))
        assertTrue(log.state.value.rows.isEmpty())
    }
}

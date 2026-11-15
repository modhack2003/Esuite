package dev.esuite.app.transport

import dev.esuite.app.core.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class ApiEngineTest {
    @Test fun browserTransferPreservesMetadataAndDoesNotCrossIdentity() {
        val sessions = IdentitySessions(); val target = "https://example.test/api".toHttpUrl()
        val count = sessions.jar("p", "A").replaceFromBrowser(target, listOf("session=native_A; Domain=example.test; Path=/api; Secure; HttpOnly"))
        assertEquals(1, count)
        val cookie = sessions.jar("p", "A").loadForRequest(target).single()
        assertTrue(cookie.secure); assertTrue(cookie.httpOnly); assertEquals("/api", cookie.path)
        assertTrue(sessions.jar("p", "B").loadForRequest(target).isEmpty())
        assertTrue(sessions.jar("p", "A").loadForRequest("https://example.test/other".toHttpUrl()).isEmpty())
    }
    @Test fun replayCapturesHeadersBodyAndStatus() = runBlocking {
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"))
            val project = Project(name = "test", origin = server.url("/").toString())
            val result = engine.execute(project, "A", RequestDraft("POST", server.url("/api").toString(), listOf(Header("Content-Type", "application/json")), "{\"a\":1}"))
            assertEquals(201, result.status); assertEquals("{\"ok\":true}", result.response.value)
            assertEquals("{\"a\":1}", server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8())
            assertFalse(result.response.truncated)
        } finally { engine.close(); server.shutdown() }
    }
    @Test fun cookiesAreIsolatedByAccountAndProjectAndRedirectsAreNotFollowed() = runBlocking {
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            val p = Project(name = "one", origin = server.url("/").toString())
            val d = RequestDraft(url = server.url("/").toString())
            server.enqueue(MockResponse().addHeader("Set-Cookie", "session=A; Path=/; HttpOnly").setBody("ok"))
            engine.execute(p, "A", d); server.takeRequest(1, TimeUnit.SECONDS)
            server.enqueue(MockResponse().setBody("ok")); engine.execute(p, "A", d)
            assertEquals("session=A", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Cookie"))
            server.enqueue(MockResponse().setBody("ok")); engine.execute(p, "B", d)
            assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Cookie"))
            server.enqueue(MockResponse().setBody("ok")); engine.execute(p.copy(id = "other"), "A", d)
            assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Cookie"))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://outside.test/"))
            assertEquals(302, engine.execute(p, "A", d).status)
            assertEquals(5, server.requestCount)
        } finally { engine.close(); server.shutdown() }
    }
    @Test fun explicitCookieWinsAndResponseCapsAreHonest() = runBlocking {
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            val p = Project(name = "test", origin = server.url("/").toString()); val d = RequestDraft(url = server.url("/").toString())
            server.enqueue(MockResponse().addHeader("Set-Cookie", "session=jar; Path=/")); engine.execute(p, "A", d); server.takeRequest(1, TimeUnit.SECONDS)
            server.enqueue(MockResponse().setBody("a".repeat(Limits.RESPONSE_BYTES + 10)))
            val result = engine.execute(p, "A", d.copy(headers = listOf(Header("Cookie", "manual=yes"))))
            assertEquals("manual=yes", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Cookie"))
            assertTrue(result.response.truncated); assertEquals(Limits.RESPONSE_BYTES, result.response.value.length)
        } finally { engine.close(); server.shutdown() }
    }
    @Test fun cancellationAndBinaryResponses() = runBlocking {
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            val p = Project(name = "test", origin = server.url("/").toString()); val d = RequestDraft(url = server.url("/").toString())
            server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(okio.Buffer().write(byteArrayOf(0, 1, -1))))
            val result = engine.execute(p, "A", d); assertEquals("base64", result.response.encoding); assertEquals("AAH/", result.response.value)
            server.enqueue(MockResponse().setBody("wait").setBodyDelay(5, TimeUnit.SECONDS))
            val job = launch { engine.execute(p, "A", d) }; delay(100); job.cancelAndJoin(); assertTrue(job.isCancelled)
        } finally { engine.close(); server.shutdown() }
    }

    // --- Defect 1 (Property 1: Bug Condition) exploration tests --------------------------------
    // These surface the UTF-8/multi-byte boundary-corruption bug: when the RESPONSE_BYTES cap
    // lands inside a multi-byte character, the textual path decodes via String(bytes, charset),
    // which injects a U+FFFD replacement character that the server never sent.
    //
    // EXPECTED ON UNFIXED CODE: these FAIL (stored body ends in \uFFFD). The failure confirms the
    // bug. The fix (task 5) is a lenient CharsetDecoder gated on textual && truncated; do NOT
    // implement it here. **Validates: Requirements 2.1, 2.2**

    @Test fun truncatedUtf8MultiByteDropsPartialTailWithoutReplacementChar() = runBlocking {
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            val p = Project(name = "test", origin = server.url("/").toString()); val d = RequestDraft(url = server.url("/").toString())
            val cap = Limits.RESPONSE_BYTES
            val emoji = "\uD83D\uDE00".toByteArray(Charsets.UTF_8) // 😀 U+1F600 => F0 9F 98 80 (4 bytes)
            // Generalize across split offsets: leave k in {1,2,3} leading emoji bytes inside the cap
            // so the stored prefix ends on an incomplete multi-byte sequence in every case.
            for (k in 1..3) {
                val body = okio.Buffer()
                body.write(ByteArray(cap - k) { 'a'.code.toByte() })
                body.write(emoji)
                body.write(ByteArray(16) { 'a'.code.toByte() })
                server.enqueue(MockResponse().setHeader("Content-Type", "text/plain; charset=UTF-8").setBody(body))
                val result = engine.execute(p, "A", d)
                server.takeRequest(1, TimeUnit.SECONDS)
                assertTrue("k=$k: a body larger than the cap must be flagged truncated", result.response.truncated)
                assertEquals("utf8", result.response.encoding)
                assertFalse("k=$k: stored body must not contain a U+FFFD replacement char (the cap split a multi-byte char)", result.response.value.contains('\uFFFD'))
                assertEquals("k=$k: stored body must end on the last complete character", 'a', result.response.value.last())
            }
        } finally { engine.close(); server.shutdown() }
    }

    @Test fun truncatedShiftJisMultiByteDropsPartialTailWithoutReplacementChar() = runBlocking {
        Assume.assumeTrue("Shift_JIS charset unavailable on this JVM", Charset.isSupported("Shift_JIS"))
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            val p = Project(name = "test", origin = server.url("/").toString()); val d = RequestDraft(url = server.url("/").toString())
            val cap = Limits.RESPONSE_BYTES
            val sjis = Charset.forName("Shift_JIS")
            val ch = "\u3042".toByteArray(sjis) // 'あ' => 0x82 0xA0 (2 bytes in Shift_JIS)
            val body = okio.Buffer()
            body.write(ByteArray(cap - 1) { 'a'.code.toByte() }) // last stored byte is the lone lead byte 0x82
            body.write(ch)
            body.write(ByteArray(16) { 'a'.code.toByte() })
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=Shift_JIS").setBody(body))
            val result = engine.execute(p, "A", d)
            server.takeRequest(1, TimeUnit.SECONDS)
            assertTrue("a body larger than the cap must be flagged truncated", result.response.truncated)
            assertFalse("stored body must not contain a U+FFFD replacement char for a split Shift_JIS char", result.response.value.contains('\uFFFD'))
            assertEquals("stored body must end on the last complete character", 'a', result.response.value.last())
        } finally { engine.close(); server.shutdown() }
    }

    // --- Property 4 (Preservation): non-bug response inputs unchanged ---------------------------
    // These pin the behavior the Defect 1 fix MUST NOT change. The fix (task 5) touches ONLY the
    // `textual && truncated` branch, so these two cases — a textual body fully within the cap, and a
    // non-textual body over the cap — must stay byte-for-byte identical before and after the fix.
    // EXPECTED ON UNFIXED CODE: both PASS (this is the baseline to preserve). The existing
    // explicitCookieWinsAndResponseCapsAreHonest covers the ASCII over-cap textual edge and is left
    // intact. **Validates: Requirements 3.1, 3.2**

    @Test fun withinCapTextualMultiByteBodyDecodesByteForByteLikeStringBytesCharset() = runBlocking {
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            val p = Project(name = "test", origin = server.url("/").toString()); val d = RequestDraft(url = server.url("/").toString())
            // A multi-byte UTF-8 payload (emoji + Japanese) comfortably within the 256 KiB cap, so the
            // NON-truncated textual path runs and the decode must equal String(bytes, charset) exactly —
            // the lenient decoder of the fix is gated on `truncated` and must never touch this path.
            val text = ("Hello \uD83D\uDE00 \u3053\u3093\u306B\u3061\u306F ").repeat(2000)
            val bytes = text.toByteArray(Charsets.UTF_8)
            assertTrue("fixture must stay within the cap so it is not truncated", bytes.size < Limits.RESPONSE_BYTES)
            server.enqueue(MockResponse().setHeader("Content-Type", "text/plain; charset=UTF-8").setBody(okio.Buffer().write(bytes)))
            val result = engine.execute(p, "A", d)
            server.takeRequest(1, TimeUnit.SECONDS)
            assertFalse("a body within the cap must not be flagged truncated", result.response.truncated)
            assertEquals("utf8", result.response.encoding)
            assertFalse("a fully-formed body must carry no replacement char", result.response.value.contains('\uFFFD'))
            assertEquals("within-cap textual body must decode exactly as String(bytes, charset)", String(bytes, Charsets.UTF_8), result.response.value)
        } finally { engine.close(); server.shutdown() }
    }

    @Test fun nonTextualBodyOverCapStaysBase64WithCapAndTruncatedFlag() = runBlocking {
        val server = MockWebServer(); server.start(); val engine = ApiEngine()
        try {
            val p = Project(name = "test", origin = server.url("/").toString()); val d = RequestDraft(url = server.url("/").toString())
            val cap = Limits.RESPONSE_BYTES
            // Non-textual bytes larger than the cap: the base64 path and the cap/truncated flag are
            // outside the Defect 1 fix and must stay identical.
            val raw = ByteArray(cap + 4096) { (it % 256 - 128).toByte() }
            server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(okio.Buffer().write(raw)))
            val result = engine.execute(p, "A", d)
            server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("a non-textual body stays base64", "base64", result.response.encoding)
            assertTrue("a body larger than the cap must be flagged truncated", result.response.truncated)
            val decoded = java.util.Base64.getDecoder().decode(result.response.value)
            assertEquals("the stored body must be capped at RESPONSE_BYTES before base64", cap, decoded.size)
            assertArrayEquals("the stored bytes must be the first RESPONSE_BYTES bytes, unchanged", raw.copyOf(cap), decoded)
        } finally { engine.close(); server.shutdown() }
    }
}

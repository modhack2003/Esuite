package dev.esuite.app.transport

import dev.esuite.app.core.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
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
}

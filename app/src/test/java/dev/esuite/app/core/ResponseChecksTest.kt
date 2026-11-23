package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test

class ResponseChecksTest {
    @Test fun pastedHeadersStopAtTheBodyAndKeepDuplicateCookies() {
        val result = ResponseChecks.run(ResponseCheck.Cookies, """
            HTTP/1.1 200 OK
            sEt-CoOkIe: session=fixture-sensitive; SameSite=None; HttpOnly
            Set-Cookie: preference=another-value; Secure; HttpOnly; SameSite=Strict

            Set-Cookie: not-a-header=body-value
        """.trimIndent().replace("\n", "\r\n"))
        assertEquals(listOf("session", "preference"), result.map(CheckItem::name))
        assertEquals(listOf(CheckStatus.Review, CheckStatus.Present), result.map(CheckItem::status))
        assertTrue(result.first().detail.contains("SameSite=None requires Secure"))
        assertFalse(result.toString().contains("fixture-sensitive"))
    }

    @Test fun sharedCachingAndOriginVariationAreReviewedAsPolicy() {
        val cache = ResponseChecks.run(ResponseCheck.Cache, listOf(Header("cache-control", "s-maxage=180, max-age=20")))
        assertEquals(CheckStatus.Review, cache.single().status)
        val privateCache = ResponseChecks.run(ResponseCheck.Cache, "Cache-Control: private, max-age=120")
        assertEquals(CheckStatus.Present, privateCache.single().status)
        val cors = ResponseChecks.run(ResponseCheck.Cors, "Access-Control-Allow-Origin: *\nAccess-Control-Allow-Credentials: true")
        assertEquals(CheckStatus.Review, cors.single().status)
        assertTrue(cors.single().detail.contains("reject"))
    }

    @Test fun reportOnlyPolicyDoesNotCountAsEnforcementAndMalformedInputIsRejected() {
        val result = ResponseChecks.run(ResponseCheck.Headers, "Content-Type: text/html; charset=utf-8\nContent-Security-Policy-Report-Only: default-src 'self'\nX-Content-Type-Options: nosniff")
        assertEquals(CheckStatus.Review, result.first { it.name == "content-security-policy" }.status)
        assertEquals(CheckStatus.Present, result.first { it.name == "x-content-type-options" }.status)
        val disabledHsts = ResponseChecks.run(ResponseCheck.Headers, "Strict-Transport-Security: max-age=0; includeSubDomains")
        assertEquals(CheckStatus.Review, disabledHsts.first { it.name == "strict-transport-security" }.status)
        assertThrows(IllegalArgumentException::class.java) { ResponseChecks.run(ResponseCheck.Headers, "malformed headers") }
        assertThrows(IllegalArgumentException::class.java) { ResponseChecks.run(ResponseCheck.Headers, "x".repeat(65_537)) }
    }
}

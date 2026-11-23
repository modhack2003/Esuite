package dev.esuite.app.core

import org.junit.Assert.*
import org.junit.Test

class ResponseChecksTest {
    @Test fun malformedCookiePairsDoNotPassTheFlagReview() {
        repeat(25) { index ->
            val flags = "Secure; HttpOnly; SameSite=Strict"
            val malformed = ResponseChecks.run(ResponseCheck.Cookies, "Set-Cookie: entry$index; $flags").single()
            assertEquals(CheckStatus.Review, malformed.status)
            assertTrue(malformed.detail.contains("name-value"))
            val removedValue = ResponseChecks.run(ResponseCheck.Cookies, "Set-Cookie: entry$index=; $flags").single()
            assertEquals(CheckStatus.Present, removedValue.status)
        }
    }

    @Test fun hstsRequiresUnsignedDigitsAndAllowsQuotedDigits() {
        repeat(25) { index ->
            val age = index + 1
            fun review(value: String) = ResponseChecks.run(ResponseCheck.Headers,
                "Strict-Transport-Security: max-age=$value").first()
            assertEquals(CheckStatus.Review, review("+$age").status)
            assertEquals(CheckStatus.Review, review("-$age").status)
            assertEquals(CheckStatus.Present, review("\"$age\"").status)
        }
    }

    @Test fun qualifiedPrivateIsFieldSpecificAndQuotedCommasStayInsideTheFieldList() {
        repeat(25) { index ->
            val qualified = ResponseChecks.run(ResponseCheck.Cache,
                "Cache-Control: max-age=$index, private=\"X-Note, X-Detail\"").single()
            assertEquals(CheckStatus.Review, qualified.status)
            assertTrue(qualified.detail.contains("named fields"))
            val quoted = ResponseChecks.run(ResponseCheck.Cache,
                "Cache-Control: max-age=$index, private=\"X-Note, no-store\"").single()
            assertEquals(CheckStatus.Review, quoted.status)
        }
    }

    @Test fun explicitCorsOriginMustBeSerializedBeforeVariationIsReviewed() {
        repeat(25) { index ->
            val origin = "https://site$index.test:8443"
            fun review(value: String) = ResponseChecks.run(ResponseCheck.Cors,
                "Access-Control-Allow-Origin: $value\nVary: Origin").single()
            assertEquals(CheckStatus.Present, review(origin).status)
            listOf("/path", "/", "?q=one", "#part").forEach { suffix ->
                assertEquals(CheckStatus.Review, review(origin + suffix).status)
            }
        }
    }

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

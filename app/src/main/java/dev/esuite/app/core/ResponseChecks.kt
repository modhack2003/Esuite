package dev.esuite.app.core

import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class ResponseCheck(val label: String, val summary: String) {
    Quick("Quick response review", "Review headers, cookie flags, cache policy, and CORS without sending requests."),
    Headers("Security headers", "Review browser hardening headers on an observed response."),
    Cookies("Cookie flags", "Check Secure, HttpOnly and SameSite from Set-Cookie lines."),
    Cache("Cache policy", "Review whether shared caches may store the response."),
    Cors("CORS policy", "Review declared origins, credentials and cache variation."),
}

internal enum class CheckStatus(val label: String) { Present("Present"), Review("Review"), Unavailable("Unavailable") }
internal data class CheckItem(val name: String, val status: CheckStatus, val detail: String, val ruleId: String = "")

internal const val RESPONSE_RULE_VERSION = 2
internal const val MAX_REVIEW_EXCHANGES = 20

internal fun Exchange.hasReviewableResponse(): Boolean =
    status != null && error == null && source != "Browser metadata"

internal fun Workspace.containsEvidence(exchange: Exchange): Boolean =
    exchange in exchanges || findings.any { it.projectId == exchange.projectId && exchange in it.evidence }

internal fun latestReviewableResponse(state: Workspace, siteId: String?, identity: String): Exchange? =
    state.exchanges.firstOrNull { it.projectId == siteId && it.identity == identity && it.hasReviewableResponse() }


/** Runs only against headers already supplied by the user. The parser never reads the response body. */
internal object ResponseChecks {
    private const val MAX_HEADER_TEXT = 65_536
    private val fieldName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    private val statusLine = Regex("HTTP/[0-9.]+\\s+[1-5][0-9]{2}(?:\\s+.*)?", RegexOption.IGNORE_CASE)
    private val ageDigits = Regex("[0-9]+")
    private val cookieValue = Regex("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]*")
    private val cookieName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}")

    fun run(check: ResponseCheck, input: String): List<CheckItem> = run(check, parseHeaders(input))

    fun run(check: ResponseCheck, exchange: Exchange): List<CheckItem> =
        if (!exchange.hasReviewableResponse()) listOf(CheckItem("Response evidence", CheckStatus.Unavailable,
            "The observed record has no captured response headers to review.", "response.evidence"))
        else if (exchange.responseHeaders.isEmpty()) listOf(CheckItem("Response headers", CheckStatus.Unavailable,
            "The HTTP response was captured with zero response header fields.", "response.empty-headers"))
        else run(check, exchange.responseHeaders)

    fun run(check: ResponseCheck, supplied: List<Header>): List<CheckItem> {
        if (check == ResponseCheck.Quick) return ResponseCheck.entries.filterNot { it == ResponseCheck.Quick }.flatMap { run(it, supplied) }

        val headers = supplied.groupBy({ it.name.lowercase(Locale.ROOT) }, Header::value)
        fun values(name: String) = headers[name].orEmpty()
        fun present(name: String, good: String, missing: String): CheckItem = if (values(name).any(String::isNotBlank)) {
            CheckItem(name, CheckStatus.Present, good)
        } else CheckItem(name, CheckStatus.Review, missing)

        return when (check) {
            ResponseCheck.Quick -> error("Use the grouped review")
            ResponseCheck.Headers -> buildList {
                val hsts = values("strict-transport-security").firstOrNull().orEmpty()
                val age = hsts.split(';').map(String::trim).firstOrNull { it.substringBefore('=').trim().equals("max-age", true) }
                    ?.substringAfter('=', "")?.trim()?.removeSurrounding("\"")?.takeIf(ageDigits::matches)?.toLongOrNull()
                add(when {
                    hsts.isBlank() -> CheckItem("strict-transport-security", CheckStatus.Review, "No HSTS header in the supplied response. Review for HTTPS deployments.")
                    age == 0L -> CheckItem("strict-transport-security", CheckStatus.Review, "HSTS max-age is zero and clears the host’s cached HSTS policy.")
                    age == null -> CheckItem("strict-transport-security", CheckStatus.Review, "The HSTS header has no valid max-age directive.")
                    else -> CheckItem("strict-transport-security", CheckStatus.Present, "HSTS max-age is $age seconds. Review host coverage for this deployment.")
                })
                if (values("content-type").any { it.substringBefore(';').trim().equals("text/html", true) } || "content-security-policy" in headers) {
                    add(present("content-security-policy", "An enforced policy is declared. Review permitted sources for this page.", "No enforced content security policy in the supplied HTML response."))
                } else add(CheckItem("content-security-policy", CheckStatus.Unavailable, "Supply an HTML response to review its content security policy."))
                val noSniff = values("x-content-type-options").any { it.equals("nosniff", true) }
                add(CheckItem("x-content-type-options", if (noSniff) CheckStatus.Present else CheckStatus.Review,
                    if (noSniff) "The response declares nosniff." else "The response does not declare nosniff."))
                add(present("referrer-policy", "A referrer policy is declared.", "The supplied response has no Referrer-Policy header."))
            }
            ResponseCheck.Cookies -> values("set-cookie").mapIndexed { index, cookie ->
                val parts = cookie.split(';', limit = 40)
                val pair = parts.first().trim()
                val name = pair.substringBefore('=').trim().takeIf(cookieName::matches)
                val value = pair.substringAfter('=', "").trim().removeSurrounding("\"")
                if (name == null || '=' !in pair || !cookieValue.matches(value)) {
                    return@mapIndexed CheckItem("Cookie ${index + 1}", CheckStatus.Review, "The line does not begin with a valid cookie name-value pair.")
                }
                val flags = parts.drop(1).associate { it.substringBefore('=').trim().lowercase(Locale.ROOT) to it.substringAfter('=', "").trim() }
                val missing = buildList {
                    if ("secure" !in flags) add("Secure absent")
                    if ("httponly" !in flags) add("HttpOnly absent")
                    val sameSite = flags["samesite"]?.lowercase(Locale.ROOT)
                    if (sameSite !in setOf("lax", "strict", "none")) add("No recognized SameSite value")
                    if (sameSite == "none" && "secure" !in flags) add("SameSite=None requires Secure")
                }
                CheckItem(name, if (missing.isEmpty()) CheckStatus.Present else CheckStatus.Review,
                    missing.joinToString(" · ").ifEmpty { "Secure, HttpOnly and a recognized SameSite value are declared." })
            }.ifEmpty { listOf(CheckItem("Set-Cookie", CheckStatus.Unavailable, "The supplied headers contain no Set-Cookie lines.")) }
            ResponseCheck.Cache -> {
                val fields = values("cache-control").flatMap(::splitCacheDirectives)
                val directives = fields.groupBy { it.substringBefore('=').trim().lowercase(Locale.ROOT) }
                listOf(when {
                    "no-store" in directives -> CheckItem("Cache-Control", CheckStatus.Present, "The response declares no-store.")
                    directives["private"].orEmpty().any { '=' !in it } ->
                        CheckItem("Cache-Control", CheckStatus.Present, "The response restricts storage by shared caches.")
                    "private" in directives ->
                        CheckItem("Cache-Control", CheckStatus.Review, "Qualified private restricts the named fields. Review whether the remaining response can be stored by shared caches.")
                    "public" in directives || "s-maxage" in directives -> CheckItem("Cache-Control", CheckStatus.Review, "The response permits shared caching. Verify that its contents are suitable for reuse across users.")
                    "no-cache" in directives -> CheckItem("Cache-Control", CheckStatus.Review, "Stored responses require revalidation before reuse.")
                    else -> CheckItem("Cache-Control", CheckStatus.Unavailable, "No explicit shared-cache or storage restriction is present in the supplied headers.")
                })
            }
            ResponseCheck.Cors -> {
                val origins = values("access-control-allow-origin").filter(String::isNotBlank)
                val credentials = values("access-control-allow-credentials").any { it.equals("true", true) }
                val vary = values("vary").flatMap { it.split(',') }.any { it.trim().equals("origin", true) || it.trim() == "*" }
                listOf(when {
                    origins.isEmpty() -> CheckItem("Allowed origin", CheckStatus.Unavailable, "This response supplies no Access-Control-Allow-Origin header.")
                    origins.size > 1 || origins.any { ',' in it } -> CheckItem("Allowed origin", CheckStatus.Review, "The response declares multiple origins. Browsers expect a single allowed origin or a wildcard.")
                    origins.single() == "*" && credentials -> CheckItem("Credentials", CheckStatus.Review, "Browsers reject credentialed reads that use the wildcard origin.")
                    origins.single() == "*" -> CheckItem("Allowed origin", CheckStatus.Review, "The response allows cross-origin reads for requests without credentials. Review which data the response exposes.")
                    origins.single() == "null" -> CheckItem("Allowed origin", CheckStatus.Review, "The response allows the opaque null origin. Verify the intended clients.")
                    !isSerializedOrigin(origins.single()) -> CheckItem("Allowed origin", CheckStatus.Review, "The value is not a valid serialized HTTP or HTTPS origin. Use a scheme and host with an optional non-default port.")
                    !vary -> CheckItem("Cache variation", CheckStatus.Review, "Verify cache variation if allowed origins are selected dynamically. No Vary: Origin is declared.")
                    else -> CheckItem("Cache variation", CheckStatus.Present, "The response declares a specific allowed origin and varies by Origin.")
                })
            }
        }.map { item ->
            val field = if (check == ResponseCheck.Cookies) "flags" else item.name.lowercase(Locale.ROOT).replace(' ', '-')
            item.copy(ruleId = "${check.name.lowercase(Locale.ROOT)}.$field")
        }
    }

    private fun isSerializedOrigin(value: String): Boolean {
        val parsed = value.toHttpUrlOrNull() ?: return false
        return value == parsed.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
    }

    private fun splitCacheDirectives(value: String): List<String> = buildList {
        var quoted = false
        var escaped = false
        var start = 0
        value.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                quoted && char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    add(value.substring(start, index))
                    start = index + 1
                }
            }
        }
        add(value.substring(start))
    }

    private fun parseHeaders(input: String): List<Header> {
        require(input.length <= MAX_HEADER_TEXT) { "Use at most 65,536 characters of response headers." }
        val lines = input.trimStart().lineSequence().iterator()
        val parsed = mutableListOf<Header>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) break
            if (parsed.isEmpty() && statusLine.matches(line)) continue
            val separator = line.indexOf(':')
            val name = line.substringBefore(':').trim()
            require(separator > 0 && fieldName.matches(name)) { "Paste response headers in Name: value format." }
            require(parsed.size < 100) { "Use at most 100 response header lines." }
            parsed += Header(name, line.substring(separator + 1).trim())
        }
        require(parsed.isNotEmpty()) { "Paste response headers to run a check." }
        return parsed
    }
}

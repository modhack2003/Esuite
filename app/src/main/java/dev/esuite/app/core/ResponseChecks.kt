package dev.esuite.app.core

import java.util.Locale

internal enum class ResponseCheck(val label: String, val summary: String) {
    Headers("Security headers", "Review browser hardening headers on an observed response."),
    Cookies("Cookie flags", "Check Secure, HttpOnly and SameSite from Set-Cookie lines."),
    Cache("Cache policy", "Review whether shared caches may store the response."),
    Cors("CORS policy", "Review declared origins, credentials and cache variation."),
}

internal enum class CheckStatus(val label: String) { Present("Present"), Review("Review"), Unavailable("Unavailable") }
internal data class CheckItem(val name: String, val status: CheckStatus, val detail: String)

/** Runs only against headers already supplied by the user. The parser never reads the response body. */
internal object ResponseChecks {
    private const val MAX_HEADER_TEXT = 65_536
    private val fieldName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    private val statusLine = Regex("HTTP/[0-9.]+\\s+[1-5][0-9]{2}(?:\\s+.*)?", RegexOption.IGNORE_CASE)
    private val cookieName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}")

    fun run(check: ResponseCheck, input: String): List<CheckItem> = run(check, parseHeaders(input))

    fun run(check: ResponseCheck, supplied: List<Header>): List<CheckItem> {
        val headers = supplied.groupBy({ it.name.lowercase(Locale.ROOT) }, Header::value)
        fun values(name: String) = headers[name].orEmpty()
        fun present(name: String, good: String, missing: String): CheckItem = if (values(name).any(String::isNotBlank)) {
            CheckItem(name, CheckStatus.Present, good)
        } else CheckItem(name, CheckStatus.Review, missing)

        return when (check) {
            ResponseCheck.Headers -> buildList {
                val hsts = values("strict-transport-security").firstOrNull().orEmpty()
                val age = hsts.split(';').map(String::trim).firstOrNull { it.substringBefore('=').trim().equals("max-age", true) }
                    ?.substringAfter('=', "")?.trim()?.removeSurrounding("\"")?.toLongOrNull()
                add(when {
                    hsts.isBlank() -> CheckItem("strict-transport-security", CheckStatus.Review, "No HSTS header in the supplied response. Review for HTTPS deployments.")
                    age == 0L -> CheckItem("strict-transport-security", CheckStatus.Review, "HSTS max-age is zero and clears the host’s cached HSTS policy.")
                    age == null || age < 0 -> CheckItem("strict-transport-security", CheckStatus.Review, "The HSTS header has no valid max-age directive.")
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
                val name = parts.first().substringBefore('=').trim().takeIf(cookieName::matches) ?: "Cookie ${index + 1}"
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
                val directives = values("cache-control").flatMap { it.split(',') }.map { it.substringBefore('=').trim().lowercase(Locale.ROOT) }.toSet()
                listOf(when {
                    "no-store" in directives -> CheckItem("Cache-Control", CheckStatus.Present, "The response declares no-store.")
                    "private" in directives -> CheckItem("Cache-Control", CheckStatus.Present, "The response restricts storage by shared caches.")
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
                    !vary -> CheckItem("Cache variation", CheckStatus.Review, "Verify cache variation if allowed origins are selected dynamically. No Vary: Origin is declared.")
                    else -> CheckItem("Cache variation", CheckStatus.Present, "The response declares a specific allowed origin and varies by Origin.")
                })
            }
        }
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

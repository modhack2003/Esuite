package dev.esuite.app.browser

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object BrowserAddress {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val namedScheme = Regex("^(?i:https?|file|javascript|data|intent|content|about|ftp|tel|mailto):")
    private val searchProvider = "https://duckduckgo.com/".toHttpUrl()

    fun resolve(input: String): HttpUrl {
        val value = input.trim()
        require(value.isNotEmpty() && value.length <= 8192 && value.none(Char::isISOControl)) { "Enter an address or search terms." }
        val explicitAddress = scheme.containsMatchIn(value) || namedScheme.containsMatchIn(value)
        val host = value.substringBefore('/').substringBefore('?').substringBefore('#')
        if (explicitAddress || (!value.any(Char::isWhitespace) && ('.' in host || ':' in host || host == "localhost"))) {
            val candidate = if (explicitAddress) value else "https://$value"
            return requireNotNull(https(candidate)) { "Enter an HTTPS address without embedded credentials." }
        }
        return searchProvider.newBuilder().addQueryParameter("q", value).build()
    }

    fun https(value: String): HttpUrl? {
        if (value.length > 8192 || value.any(Char::isISOControl) || value.contains('\\')) return null
        if (!value.startsWith("https://", ignoreCase = true)) return null
        return value.toHttpUrlOrNull()?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
    }

    fun origin(url: HttpUrl): String =
        url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
}

/** Recent requests expose same-origin method and path metadata in memory for one page. */
internal class BrowserRequestLog {
    private var origin: String? = null
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun page(url: HttpUrl?, preserveExisting: Boolean = false) {
        val next = url?.let(BrowserAddress::origin)
        if (next != origin || !preserveExisting) entries.clear()
        origin = next
    }

    @Synchronized
    fun add(method: String, url: HttpUrl) {
        if (BrowserAddress.origin(url) != origin) return
        if (entries.size == 60) entries.removeFirst()
        entries.addLast("${method.take(12)} ${url.encodedPath.take(280)}")
    }

    @Synchronized
    fun snapshot(): List<String> = entries.reversed()
}

internal fun cookieMetadata(rows: List<String>, hasAttributes: Boolean): List<String> =
    rows.take(60).mapNotNull { row ->
        val parts = row.split(';')
        val name = parts.first().substringBefore('=').trim().take(160)
        if (name.isEmpty()) return@mapNotNull null
        if (!hasAttributes) return@mapNotNull name
        val attributes = parts.drop(1).mapNotNull { attribute ->
            val trimmed = attribute.trim()
            when (trimmed.substringBefore('=').lowercase()) {
                "secure" -> "Secure"
                "httponly" -> "HttpOnly"
                "samesite" -> trimmed.substringAfter('=', "").takeIf {
                    it.equals("strict", true) || it.equals("lax", true) || it.equals("none", true)
                }?.let { "SameSite=$it" }
                else -> null
            }
        }
        if (attributes.isEmpty()) name else "$name · ${attributes.joinToString(" · ")}"
    }

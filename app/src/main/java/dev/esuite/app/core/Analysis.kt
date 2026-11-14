package dev.esuite.app.core

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Base64
import kotlin.math.ln

data class Observation(val title: String, val evidence: String)
object PassiveAnalyzer {
    fun inspect(exchange: Exchange): List<Observation> {
        if (exchange.status == null) return emptyList()
        val out = mutableListOf<Observation>()
        val headers = exchange.responseHeaders
        fun value(name: String) = headers.filter { it.name.equals(name, true) }.joinToString(",") { it.value }
        if (exchange.request.url.startsWith("https://") && value("Strict-Transport-Security").isBlank()) out += Observation("HSTS header absent", "HTTPS response has no Strict-Transport-Security header. Assess deployment context.")
        if (value("Content-Type").contains("text/html", true) && value("Content-Security-Policy").isBlank()) out += Observation("CSP header absent", "HTML response has no Content-Security-Policy header. This is a hardening observation, not confirmed XSS.")
        headers.filter { it.name.equals("Set-Cookie", true) }.forEach { h ->
            val parts = h.value.split(';').map { it.trim() }; val name = parts.first().substringBefore('=')
            if (parts.none { it.equals("Secure", true) }) out += Observation("Cookie missing Secure", "Cookie '$name' does not set Secure in this response.")
            if (parts.none { it.equals("HttpOnly", true) }) out += Observation("Cookie missing HttpOnly", "Cookie '$name' is script-readable unless other restrictions apply. Some cookies intentionally require script access.")
        }
        if (value("Access-Control-Allow-Origin") == "*" && value("Access-Control-Allow-Credentials").equals("true", true)) out += Observation("Conflicting CORS configuration", "Wildcard origin with credentials is rejected by browsers; this is not proof of credentialed cross-origin access.")
        return out
    }
}

object ResponseComparer {
    private val json = Json
    private fun normalize(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.toSortedMap().mapValues { normalize(it.value) })
        is JsonArray -> JsonArray(e.map(::normalize))
        else -> e
    }
    fun compare(a: Exchange, b: Exchange): String {
        fun normalized(s: String) = runCatching { normalize(json.parseToJsonElement(s)).toString() }.getOrDefault(s)
        val equal = normalized(a.response.value) == normalized(b.response.value)
        return "${a.identity}: ${a.status ?: "error"} · ${a.durationMs} ms\n${b.identity}: ${b.status ?: "error"} · ${b.durationMs} ms\nBody: ${if (equal) "equal" else "different"}\nCaptured bytes complete: ${!a.response.truncated && !b.response.truncated}\nA difference alone does not confirm an authorization vulnerability."
    }
}

object TokenTools {
    fun jwtClaims(input: String): String {
        val parts = input.trim().split('.'); require(parts.size == 3) { "Expected a three-part JWT" }
        fun decode(s: String) = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)).toString()
        return "UNVERIFIED — decoding is not signature verification.\nHeader: ${decode(parts[0])}\nClaims: ${decode(parts[1])}"
    }
    fun sample(tokens: List<String>): String {
        require(tokens.size in 2..4096 && tokens.all { it.length <= 8192 }) { "Provide 2–4096 bounded token samples" }
        val counts = tokens.flatMap { it.toList() }.groupingBy { it }.eachCount(); val n = counts.values.sum().toDouble()
        val entropy = counts.values.sumOf { val p = it / n; -p * ln(p) / ln(2.0) }
        return "Samples: ${tokens.size}\nUnique: ${tokens.distinct().size}\nDuplicate samples: ${tokens.size - tokens.distinct().size}\nCharacter entropy: ${"%.3f".format(java.util.Locale.ROOT, entropy)} bits\nDescriptive sample statistics only; not proof of cryptographic unpredictability."
    }
}

object Redactor {
    private val sensitive = Regex("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[-_]?key|token|password|secret|session)")
    fun headers(headers: List<Header>) = headers.map { if (sensitive.containsMatchIn(it.name)) it.copy(value = "[REDACTED]") else it }
    /** Default exports omit all bodies and all query values: unknown secret names cannot be reliably guessed. */
    fun exchange(e: Exchange): Exchange {
        val url = runCatching {
            val parsed = e.request.url.toHttpUrl()
            parsed.newBuilder().query(null).fragment(null).build().toString()
        }.getOrDefault("[URL OMITTED]")
        return e.copy(request = e.request.copy(url = url, headers = headers(e.request.headers), body = "[BODY OMITTED]"),
            responseHeaders = headers(e.responseHeaders), response = StoredBody("[BODY OMITTED]"), error = e.error?.let { "[ERROR DETAIL OMITTED]" })
    }
}

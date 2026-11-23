package dev.esuite.app.core

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Base64
import java.util.Locale
import kotlin.math.ln

object ResponseComparer {
    private val json = Json
    private fun normalize(element: JsonElement, depth: Int = 0): JsonElement {
        require(depth <= 128)
        return when (element) {
            is JsonObject -> JsonObject(element.toSortedMap().mapValues { normalize(it.value, depth + 1) })
            is JsonArray -> JsonArray(element.map { normalize(it, depth + 1) })
            else -> element
        }
    }

    fun compare(a: Exchange, b: Exchange): String = buildString {
        appendLine("${a.identity}: ${a.status ?: "error"} · ${a.durationMs} ms")
        appendLine("${b.identity}: ${b.status ?: "error"} · ${b.durationMs} ms")
        if (!a.hasReviewableResponse() || !b.hasReviewableResponse()) {
            appendLine("Body and headers: unavailable for one or both responses.")
        } else {
            val complete = !a.response.truncated && !b.response.truncated
            appendLine("Body: ${compareBodies(a.response, b.response, complete)}")
            appendLine("Captured bytes complete: $complete")
            appendHeaders(a, b)
        }
        append("A difference alone does not confirm an authorization vulnerability.")
    }

    private fun compareBodies(a: StoredBody, b: StoredBody, complete: Boolean): String {
        if (a.encoding != b.encoding) return "unavailable across different capture encodings"
        fun normalized(value: String) =
            runCatching { normalize(json.parseToJsonElement(value)).toString() }.getOrDefault(value)
        val equal = if (complete && a.encoding == "utf8") normalized(a.value) == normalized(b.value) else a.value == b.value
        return when {
            !complete && equal -> "captured previews equal"
            !complete -> "captured previews differ"
            equal -> "equal"
            else -> "different"
        }
    }

    private fun StringBuilder.appendHeaders(a: Exchange, b: Exchange) {
        fun fields(exchange: Exchange) =
            exchange.responseHeaders.groupBy({ it.name.lowercase(Locale.ROOT) }, Header::value)
        val first = fields(a)
        val second = fields(b)
        val shared = first.keys intersect second.keys
        val changed = shared.filter { first[it] != second[it] }
        val onlyFirst = first.keys - second.keys
        val onlySecond = second.keys - first.keys
        if (changed.isEmpty() && onlyFirst.isEmpty() && onlySecond.isEmpty()) {
            appendLine("Header fields: equal")
            return
        }
        fun appendNames(label: String, names: Collection<String>) {
            if (names.isEmpty()) return
            appendLine("$label (${names.size}): ${names.sorted().take(12).joinToString(", ")}${if (names.size > 12) ", …" else ""}")
        }
        appendNames("Changed header fields", changed)
        appendNames("Only ${a.identity}", onlyFirst)
        appendNames("Only ${b.identity}", onlySecond)
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

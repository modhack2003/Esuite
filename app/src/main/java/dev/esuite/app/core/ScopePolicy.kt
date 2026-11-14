package dev.esuite.app.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ScopePolicy(origin: String) {
    val target: HttpUrl = parse(origin)
    val canonicalOrigin: String = target.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
    init { require(target.encodedPath == "/" && target.query == null) { "Project scope must be an origin, without a path or query" } }
    fun requireAllowed(url: String): HttpUrl = parse(url).also {
        require(it.scheme == target.scheme && it.host == target.host && it.port == target.port) { "URL is outside the selected project origin" }
    }
    fun allows(url: String): Boolean = runCatching { requireAllowed(url); true }.getOrDefault(false)
    companion object {
        private fun parse(value: String): HttpUrl {
            require(value.length <= 8192 && !value.contains('\r') && !value.contains('\n')) { "Invalid URL" }
            val parsed = value.toHttpUrl()
            require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.fragment == null) { "URL credentials and fragments are not supported" }
            return parsed
        }
    }
}

object DraftParser {
    private val token = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    fun headers(text: String): List<Header> = text.lineSequence().filter { it.isNotBlank() }.map { line ->
        val index = line.indexOf(':'); require(index > 0) { "Headers must use Name: value" }
        Header(line.substring(0, index).trim(), line.substring(index + 1).trim())
    }.toList().also(::validateHeaders)
    fun validateHeaders(headers: List<Header>) {
        require(headers.size <= 100) { "Too many headers" }
        headers.forEach { require(token.matches(it.name) && !it.value.contains('\r') && !it.value.contains('\n') && it.value.length <= 8192) { "Invalid header name or value" } }
    }
    fun validate(draft: RequestDraft) {
        require(token.matches(draft.method)) { "Invalid HTTP method" }
        validateHeaders(draft.headers)
        require(draft.body.toByteArray(Charsets.UTF_8).size <= Limits.REQUEST_BYTES) { "Request body exceeds 256 KiB" }
        require(draft.method.uppercase() !in setOf("GET", "HEAD") || draft.body.isEmpty()) { "GET/HEAD request bodies are not supported by this transport" }
    }
    /** Import the actual HTTP message, with the project origin supplying relative request targets. */
    fun raw(text: String, origin: String): RequestDraft {
        val parts = text.replace("\r\n", "\n").split("\n\n", limit = 2)
        val lines = parts[0].lines(); val first = lines.first().split(' ', limit = 3)
        require(first.size == 3 && first[2] in setOf("HTTP/1.0", "HTTP/1.1")) { "Expected METHOD target HTTP/1.1" }
        val policy = ScopePolicy(origin)
        val url = if (first[1].startsWith('/')) policy.target.resolve(first[1])!!.toString() else first[1]
        policy.requireAllowed(url)
        return RequestDraft(first[0].uppercase(), url, headers(lines.drop(1).joinToString("\n")).filterNot { it.name.equals("Host", true) || it.name.equals("Content-Length", true) }, parts.getOrElse(1) { "" }).also(::validate)
    }
}

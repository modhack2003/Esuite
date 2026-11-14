package dev.esuite.app.core

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl

object ApiImports {
    private fun words(input: String): List<String> {
        require(input.length <= Limits.REQUEST_BYTES) { "Import too large" }
        val out = mutableListOf<String>(); val token = StringBuilder(); var quote: Char? = null; var escape = false; var started = false
        input.forEach { c ->
            when {
                escape -> { token.append(c); escape = false; started = true }
                c == '\\' && quote != '\'' -> { escape = true; started = true }
                quote != null -> if (c == quote) quote = null else token.append(c)
                c == '\'' || c == '"' -> { quote = c; started = true }
                c.isWhitespace() -> if (started) { out += token.toString(); token.clear(); started = false }
                c in "|;&<>`" -> error("Shell operators are not supported; no command is executed")
                else -> { token.append(c); started = true }
            }
        }
        require(quote == null && !escape) { "Unclosed quote or escape" }
        if (started) out += token.toString()
        return out
    }
    fun curl(input: String, origin: String): RequestDraft {
        val args = words(input); require(args.firstOrNull() == "curl") { "Expected a cURL command" }
        var method = "GET"; var explicitMethod = false; var url = ""; var body = ""; val headers = mutableListOf<Header>(); var i = 1
        while (i < args.size) {
            val arg = args[i++]
            fun next(): String { require(i < args.size) { "Missing value for $arg" }; return args[i++] }
            when (arg) {
                "-X", "--request" -> { method = next().uppercase(); explicitMethod = true }
                "-H", "--header" -> headers += DraftParser.headers(next())
                "-d", "--data", "--data-raw", "--data-binary" -> { body = next(); require(!body.startsWith('@')) { "File-backed cURL bodies are not imported" }; if (!explicitMethod) method = "POST" }
                "--url" -> url = next()
                "-b", "--cookie" -> { val value = next(); require(value.contains('=')) { "Only literal cookies are supported" }; headers += Header("Cookie", value) }
                else -> { require(arg.startsWith("https://") || arg.startsWith("http://")) { "Unsupported cURL option: $arg" }; require(url.isEmpty()) { "Only one URL is supported" }; url = arg }
            }
        }
        ScopePolicy(origin).requireAllowed(url)
        return RequestDraft(method, url, headers, body).also(DraftParser::validate)
    }
    fun openApi(input: String, project: Project): List<SavedRequest> {
        require(input.toByteArray().size <= 2 * 1024 * 1024) { "Specification exceeds 2 MiB" }
        val root = Json.parseToJsonElement(input).jsonObject
        require(root["openapi"]?.jsonPrimitive?.content?.startsWith("3.") == true) { "Expected OpenAPI 3 JSON (YAML is not supported yet)" }
        val policy = ScopePolicy(project.origin)
        val server = root["servers"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
            policy.target.resolve(it)?.also { u -> policy.requireAllowed(u.toString()) }
        } ?: policy.target
        val output = mutableListOf<SavedRequest>()
        root["paths"]?.jsonObject?.forEach { (path, methods) ->
            require(path.startsWith('/')) { "OpenAPI paths must start with /" }
            methods.jsonObject.forEach { (method, _) ->
                if (method.lowercase() in setOf("get", "post", "put", "patch", "delete", "head", "options")) {
                    val url = server.newBuilder().encodedPath(server.encodedPath.trimEnd('/') + path).build().toString()
                    policy.requireAllowed(url)
                    output += SavedRequest(projectId = project.id, name = "${method.uppercase()} $path", draft = RequestDraft(method.uppercase(), url))
                    require(output.size <= 100) { "Specification exceeds 100 operation import limit" }
                }
            }
        }
        return output
    }
    fun har(input: String, project: Project): List<SavedRequest> {
        require(input.toByteArray().size <= 2 * 1024 * 1024) { "HAR exceeds 2 MiB" }
        val entries = Json.parseToJsonElement(input).jsonObject["log"]?.jsonObject?.get("entries")?.jsonArray ?: error("Expected HAR log.entries")
        require(entries.size <= 200) { "HAR entry limit exceeded (200)" }
        val scope = ScopePolicy(project.origin)
        return entries.mapNotNull { entry ->
            val request = entry.jsonObject["request"]?.jsonObject ?: return@mapNotNull null
            val url = request["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!scope.allows(url)) return@mapNotNull null
            val headers = request["headers"]?.jsonArray?.map { Header(it.jsonObject.getValue("name").jsonPrimitive.content, it.jsonObject.getValue("value").jsonPrimitive.content) }.orEmpty()
            val post = request["postData"]?.jsonObject
            require(post?.get("params") == null) { "Multipart/form HAR reconstruction is not supported; use a textual request entry" }
            val draft = RequestDraft(request.getValue("method").jsonPrimitive.content.uppercase(), url, headers, post?.get("text")?.jsonPrimitive?.content.orEmpty()).also(DraftParser::validate)
            SavedRequest(projectId = project.id, name = "HAR ${draft.method} ${url.toHttpUrl().encodedPath}", draft = draft)
        }.also { require(it.size <= 100) { "HAR contains more than 100 in-scope requests" } }
    }
}

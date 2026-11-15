package dev.esuite.app.transport

import dev.esuite.app.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiEngine(val sessions: IdentitySessions = IdentitySessions()) {
    private val base = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS).build()

    suspend fun execute(project: Project, identity: String, draft: RequestDraft, source: String = "API Lab"): Exchange {
        DraftParser.validate(draft)
        val url = ScopePolicy(project.origin).requireAllowed(draft.url)
        val jar = sessions.jar(project.id, identity)
        val explicitCookie = draft.headers.any { it.name.equals("Cookie", true) }
        val perCallJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = jar.saveFromResponse(url, cookies)
            override fun loadForRequest(url: HttpUrl) = if (explicitCookie) emptyList() else jar.loadForRequest(url)
        }
        val observed = AtomicReference<Request>()
        val client = base.newBuilder().cookieJar(perCallJar).addNetworkInterceptor { chain ->
            observed.set(chain.request()); chain.proceed(chain.request())
        }.build()
        val request = Request.Builder().url(url).apply {
            draft.headers.filterNot { it.name.lowercase() in setOf("host", "content-length", "transfer-encoding", "connection", "proxy-authorization", "proxy-connection") }
                .forEach { addHeader(it.name, it.value) }
            val type = draft.headers.lastOrNull { it.name.equals("Content-Type", true) }?.value?.toMediaTypeOrNull()
            method(draft.method.uppercase(), if (draft.method.uppercase() in setOf("GET", "HEAD")) null else draft.body.toRequestBody(type))
        }.build()
        val start = System.nanoTime()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val exchange = response.use {
                            val buffer = Buffer(); val body = it.body
                            if (body != null) {
                                val sourceBody = body.source()
                                while (buffer.size <= Limits.RESPONSE_BYTES) {
                                    if (sourceBody.read(buffer, minOf(8192L, Limits.RESPONSE_BYTES + 1L - buffer.size)) == -1L) break
                                }
                            }
                            val truncated = buffer.size > Limits.RESPONSE_BYTES
                            val bytes = buffer.readByteArray(minOf(buffer.size, Limits.RESPONSE_BYTES.toLong()))
                            val media = body?.contentType(); val textual = media == null || media.type == "text" || listOf("json", "xml", "javascript", "x-www-form-urlencoded").any { key -> media.subtype.contains(key) }
                            val sent = observed.get() ?: request
                            Exchange(projectId = project.id, identity = identity, source = source,
                                request = draft.copy(headers = sent.headers.map { h -> Header(h.first, h.second) }), status = it.code,
                                responseHeaders = it.headers.map { h -> Header(h.first, h.second) },
                                response = StoredBody(if (textual) CapturedText.decode(bytes, media?.charset(Charsets.UTF_8) ?: Charsets.UTF_8, truncated) else Base64.getEncoder().encodeToString(bytes), if (textual) "utf8" else "base64", truncated),
                                durationMs = (System.nanoTime() - start) / 1_000_000)
                        }
                        if (continuation.isActive) continuation.resume(exchange)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
    fun close() { base.dispatcher.cancelAll(); sessions.clearAll(); base.connectionPool.evictAll() }
}

package dev.esuite.app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

internal enum class RequestEventKind(val label: String) {
    Navigation("Navigation"), Request("Request"), HttpError("HTTP error"), LoadError("Load error"), Timing("Resource timing"),
}

@Serializable internal data class TimingBatch(val timeOrigin: Double, val rows: List<ResourceTiming>)

@Serializable internal data class ResourceTiming(
    val id: Int, val path: String, val kind: String, val status: Int?, val start: Double?, val duration: Double?,
    val transfer: Long?, val protocol: String, val dns: Double?, val connect: Double?, val tls: Double?,
    val wait: Double?, val download: Double?, val encoded: Long?, val decoded: Long?,
)

internal data class BrowserRequestEvent(
    val id: String, val kind: RequestEventKind, val path: String, val timestamp: Double,
    val method: String? = null, val mainFrame: Boolean = false, val code: Int? = null,
    val timing: ResourceTiming? = null, val mimeType: String? = null,
) {
    val status: Int? get() = when (kind) {
        RequestEventKind.HttpError -> code
        RequestEventKind.Timing -> timing?.status
        else -> null
    }
    val failed: Boolean get() = kind == RequestEventKind.LoadError || (status ?: 0) >= 400
    val resourceType: String get() = timing?.kind ?: if (mainFrame) "document" else "unknown"
}

internal data class BrowserNetworkState(
    val rows: List<BrowserRequestEvent> = emptyList(),
    val recording: Boolean = true,
    val preserve: Boolean = false,
)

/** WebView callback and Performance API observations have independent identities. */
internal class BrowserRequestLog(private val now: () -> Long = System::currentTimeMillis) {
    private var origin: String? = null
    private var sequence = 0L
    private var generation = 0L
    private var earliestStart = 0L
    private val records = MutableStateFlow(BrowserNetworkState())
    val state = records.asStateFlow()

    @Synchronized
    fun page(url: HttpUrl?, continuing: Boolean = false) {
        val next = url?.let(BrowserAddress::origin)
        if (next == null || next != origin || (!continuing && !records.value.preserve)) {
            records.value = records.value.copy(rows = emptyList())
        }
        if (!continuing || origin != next) {
            generation++
            earliestStart = 0L
        }
        origin = next
    }

    @Synchronized
    fun add(method: String, url: HttpUrl, mainFrame: Boolean = false, kind: RequestEventKind = RequestEventKind.Request,
        code: Int? = null, mimeType: String? = null) {
        if (BrowserAddress.origin(url) != origin || !records.value.recording) return
        val event = BrowserRequestEvent("callback:${++sequence}", kind, url.encodedPath.take(280), now().toDouble(),
            method.take(12).ifBlank { null }, mainFrame, code, mimeType = mimeType?.take(80))
        records.value = records.value.copy(rows = bounded(listOf(event) + records.value.rows))
    }

    @Synchronized
    fun token(): Long? = generation.takeIf { origin != null && records.value.recording }

    @Synchronized
    fun addTimings(token: Long, scope: String, batch: TimingBatch) {
        if (token != generation || origin != scope || !records.value.recording) return
        val new = batch.rows.mapNotNull { timing ->
            val started = batch.timeOrigin + (timing.start ?: return@mapNotNull null)
            if (!started.isFinite() || started < earliestStart) return@mapNotNull null
            BrowserRequestEvent("timing:${batch.timeOrigin}:${timing.id}", RequestEventKind.Timing, timing.path, started,
                mainFrame = timing.kind == "document", timing = timing)
        }
        if (new.isNotEmpty()) {
            records.value = records.value.copy(rows = bounded((new + records.value.rows).distinctBy { it.id }))
        }
    }

    @Synchronized
    fun setRecording(recording: Boolean) {
        if (records.value.recording == recording) return
        generation++
        earliestStart = now()
        records.value = records.value.copy(recording = recording)
    }

    @Synchronized
    fun setPreserve(preserve: Boolean) {
        records.value = records.value.copy(preserve = preserve)
    }

    @Synchronized
    fun clear() {
        generation++
        earliestStart = now()
        records.value = records.value.copy(rows = emptyList())
    }

    private fun bounded(rows: List<BrowserRequestEvent>): List<BrowserRequestEvent> {
        return rows.sortedByDescending { it.timestamp }.take(300)
    }
}

package dev.esuite.app.core

import dev.esuite.app.browser.BrowserLogSnapshot
import dev.esuite.app.browser.BrowserRequestEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

internal enum class ObservationSource(val label: String) {
    Exchange("HTTP exchange"), Callback("WebView callback"), Timing("Resource timing"),
}

internal data class ActivityRecord(
    val id: String,
    val origin: String,
    val path: String,
    val method: String?,
    val status: Int?,
    val timestamp: Long,
    val source: ObservationSource,
    val exchange: Exchange? = null,
    val browser: BrowserRequestEvent? = null,
) {
    val sourceLabel: String get() = if (exchange == null) source.label else "${source.label} · ${exchange.source}"
    val failed: Boolean get() = (status ?: 0) >= 400 || exchange?.error != null || browser?.failed == true
    val clipped: Boolean get() = browser?.pathClipped == true
    val pathLabel: String get() = if (clipped) "$path…" else path
}

internal data class Endpoint(val origin: String, val path: String, val method: String?)
internal data class BrowserActivity(val siteId: String, val log: BrowserLogSnapshot)

internal fun activityRecords(site: Project, workspace: Workspace, imported: BrowserActivity?): List<ActivityRecord> {
    val scope = ScopePolicy(site.origin)
    val exchanges = workspace.exchanges.mapNotNull { exchange ->
        if (exchange.projectId != site.id) return@mapNotNull null
        val target = runCatching { scope.requireAllowed(exchange.request.url) }.getOrNull() ?: return@mapNotNull null
        ActivityRecord("exchange:${exchange.id}", scope.canonicalOrigin, target.encodedPath,
            exchange.request.method.ifBlank { null }, exchange.status, exchange.timestamp, ObservationSource.Exchange, exchange = exchange)
    }
    val browser = imported?.takeIf { it.siteId == site.id && it.log.origin == scope.canonicalOrigin }?.log?.rows.orEmpty().map { row ->
        ActivityRecord("browser:${row.id}", scope.canonicalOrigin, row.path, row.method, row.status,
            row.timestamp.toLong(), if (row.timing == null) ObservationSource.Callback else ObservationSource.Timing, browser = row)
    }
    return (exchanges + browser).sortedByDescending(ActivityRecord::timestamp)
}

internal fun siteMap(records: List<ActivityRecord>): Map<Endpoint, List<ActivityRecord>> =
    records.filterNot(ActivityRecord::clipped).groupBy { Endpoint(it.origin, it.path, it.method) }

internal fun observationDateFormatter(locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", locale).withDecimalStyle(DecimalStyle.of(locale)).withZone(zone)

internal const val ACTIVITY_PREVIEW_BYTES = 8 * 1024

internal fun utf8Preview(text: String, limit: Int): String {
    if (text.length <= limit / 4) return text
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= limit) return text
    var end = limit
    while (end > 0 && (bytes[end].toInt() and 0xc0) == 0x80) end--
    return String(bytes, 0, end, Charsets.UTF_8)
}

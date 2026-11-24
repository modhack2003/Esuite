package dev.esuite.app.browser

import java.util.Locale

internal enum class ResourceType(val label: String, val key: String) {
    All("All", ""), Document("Doc", "document"), Fetch("Fetch / XHR", "fetch"), Script("JS", "script"),
    Style("CSS", "stylesheet"), Image("Img", "image"), Font("Font", "font"), Media("Media", "media"), Other("Other", "other"),
    ;

    fun includes(row: BrowserRequestEvent): Boolean = this == All || row.resourceType == key ||
        (this == Other && row.resourceType == "unknown")
}

internal enum class StatusFilter(val label: String) {
    All("All statuses"), Success("2xx"), Redirect("3xx"), Errors("Errors"), Unknown("Unavailable"),
    ;

    fun includes(row: BrowserRequestEvent): Boolean = when (this) {
        All -> true
        Success -> row.status in 200..299
        Redirect -> row.status in 300..399
        Errors -> row.failed
        Unknown -> row.status == null && !row.failed
    }
}

internal enum class NetworkOrder(val label: String) { Start("Start time"), Latest("Newest"), Slowest("Slowest"), Largest("Largest") }

internal enum class NetworkSource(val label: String) {
    All("All sources"), Callback("WebView callbacks"), Timing("Resource timing"),
    ;

    fun includes(row: BrowserRequestEvent): Boolean = when (this) {
        All -> true
        Callback -> row.timing == null
        Timing -> row.timing != null
    }
}

internal data class NetworkFilters(
    val query: String = "",
    val type: ResourceType = ResourceType.All,
    val status: StatusFilter = StatusFilter.All,
    val source: NetworkSource = NetworkSource.All,
    val method: String? = null,
    val order: NetworkOrder = NetworkOrder.Start,
    val waterfall: Boolean = true,
) {
    val active: Boolean get() = query.isNotBlank() || type != ResourceType.All || status != StatusFilter.All ||
        source != NetworkSource.All || method != null

    fun reset() = NetworkFilters(waterfall = waterfall)

    fun select(rows: List<BrowserRequestEvent>): List<BrowserRequestEvent> {
        val terms = query.split(whitespace).filter(String::isNotBlank).map(SearchTerm::parse)
        val matches = rows.filter { row ->
            type.includes(row) && status.includes(row) && source.includes(row) &&
                (method == null || row.method.orEmpty() == method) && terms.all { it.includes(row) }
        }
        return when (order) {
            NetworkOrder.Start -> matches.sortedBy(BrowserRequestEvent::timestamp)
            NetworkOrder.Latest -> matches.sortedByDescending(BrowserRequestEvent::timestamp)
            NetworkOrder.Slowest -> matches.sortedByDescending { it.timing?.duration ?: -1.0 }
            NetworkOrder.Largest -> matches.sortedByDescending { it.timing?.transfer ?: -1L }
        }
    }
}

private val whitespace = Regex("\\s+")

private data class SearchTerm(val field: String, val value: String, val excluded: Boolean) {
    fun includes(row: BrowserRequestEvent): Boolean {
        val matches = when (field) {
            "method" -> row.method.equals(value, ignoreCase = true)
            "status-code" -> row.status?.let { value == it.toString() || value == "${it / 100}xx" } == true
            "resource-type" -> row.resourceType.equals(value, ignoreCase = true)
            "source" -> value == if (row.timing == null) "callback" else "timing"
            "path" -> row.path.contains(value, ignoreCase = true)
            else -> listOf(row.path, row.method, row.kind.label, row.resourceType, row.status?.toString())
                .any { it?.contains(value, ignoreCase = true) == true }
        }
        return matches != excluded
    }

    companion object {
        private val fields = setOf("method", "status-code", "resource-type", "source", "path")

        fun parse(term: String): SearchTerm {
            val excluded = term.length > 1 && term.startsWith('-')
            val text = if (excluded) term.drop(1) else term
            val key = text.substringBefore(':').lowercase(Locale.ROOT)
            val value = text.substringAfter(':', "")
            return if (key in fields && value.isNotEmpty()) SearchTerm(key, value.lowercase(Locale.ROOT), excluded)
            else SearchTerm("", text, excluded)
        }
    }
}

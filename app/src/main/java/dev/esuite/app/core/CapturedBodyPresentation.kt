package dev.esuite.app.core

import java.util.Base64

internal fun StoredBody.canFormatJson(headers: List<Header>): Boolean {
    if (truncated || encoding != "utf8" || value.isBlank()) return false
    val mediaType = headers.firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
        ?.value?.substringBefore(';')?.trim().orEmpty()
    return mediaType.equals("application/json", ignoreCase = true) || mediaType.endsWith("+json", ignoreCase = true) ||
        value.firstOrNull { !it.isWhitespace() } in setOf('{', '[')
}

internal data class CapturedHexPage(val text: String, val start: Int, val end: Int, val total: Int)

internal const val HEX_PAGE_BYTES = 1024

/** Binary captures retain observed bytes. Text captures use the UTF-8 representation of decoded text. */
internal fun StoredBody.hexPage(page: Int): CapturedHexPage {
    val bytes = when (encoding) {
        "base64" -> Base64.getDecoder().decode(value)
        "utf8" -> value.toByteArray(Charsets.UTF_8)
        else -> throw IllegalArgumentException("This capture encoding cannot be displayed as hex.")
    }
    val lastPage = (bytes.size - 1).coerceAtLeast(0) / HEX_PAGE_BYTES
    val start = page.coerceIn(0, lastPage) * HEX_PAGE_BYTES
    val end = (start + HEX_PAGE_BYTES).coerceAtMost(bytes.size)
    val text = buildString {
        for (offset in start until end step 16) {
            if (offset != start) append('\n')
            append(offset.toString(16).padStart(8, '0'))
            append("  ")
            val line = Codec.encodeHex(bytes, offset, (offset + 16).coerceAtMost(end))
            for (index in line.indices step 2) {
                if (index != 0) append(' ')
                append(line, index, index + 2)
            }
        }
    }
    return CapturedHexPage(text, start, end, bytes.size)
}

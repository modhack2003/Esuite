package dev.esuite.app.transport

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class CapturedTextTest {
    @Test fun partialTailsAreDroppedWithoutRemovingInteriorMalformedData() {
        val mixed = byteArrayOf(65, 0xff.toByte(), 66, 0xf0.toByte(), 0x9f.toByte())
        assertEquals("A\uFFFDB", CapturedText.decode(mixed, Charsets.UTF_8, true))
        assertEquals(String(mixed, Charsets.UTF_8), CapturedText.decode(mixed, Charsets.UTF_8, false))
        assertEquals("\uFFFD", CapturedText.decode("\uFFFD".toByteArray(), Charsets.UTF_8, true))
    }
    @Test fun validSplitPrefixesAcrossSupportedCharsets() {
        for ((charset, text) in listOf(Charsets.UTF_8 to "A😀あB", Charsets.UTF_16BE to "A😀あB", Charset.forName("Shift_JIS") to "AあB")) {
            val bytes = text.toByteArray(charset)
            for (length in 0..bytes.size) {
                val decoded = CapturedText.decode(bytes.copyOf(length), charset, true)
                assertTrue("$charset split $length: $decoded", text.startsWith(decoded))
            }
        }
    }
}

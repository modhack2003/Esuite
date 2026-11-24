package dev.esuite.app.core

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedBodyPresentationTest {
    @Test fun onlyCompleteTextCapturesOfferJsonFormatting() {
        val jsonHeader = listOf(Header("content-type", "application/problem+json; charset=UTF-8"))
        assertTrue(StoredBody("null").canFormatJson(jsonHeader))
        assertTrue(StoredBody("\n {\"value\":1}").canFormatJson(emptyList()))
        assertFalse(StoredBody("{", truncated = true).canFormatJson(jsonHeader))
        assertFalse(StoredBody("e30=", encoding = "base64").canFormatJson(jsonHeader))
        assertFalse(StoredBody("normal text").canFormatJson(emptyList()))
    }

    @Test fun binaryPagesPreserveBytesAndClampTheirBounds() {
        val bytes = ByteArray(HEX_PAGE_BYTES + 2)
        bytes[HEX_PAGE_BYTES + 1] = 0xff.toByte()
        val body = StoredBody(Base64.getEncoder().encodeToString(bytes), encoding = "base64", truncated = true)
        val first = body.hexPage(-1)
        assertEquals(0, first.start)
        assertEquals(HEX_PAGE_BYTES, first.end)
        assertEquals(64, first.text.lines().size)
        assertEquals(CapturedHexPage("00000400  00 ff", HEX_PAGE_BYTES, bytes.size, bytes.size), body.hexPage(Int.MAX_VALUE))
    }

    @Test fun textHexUsesTheUtf8BytesOfTheDisplayedCharacters() {
        assertEquals(CapturedHexPage("00000000  78 20 f0 9f a7 aa", 0, 6, 6), StoredBody("x 🧪").hexPage(0))
    }
}

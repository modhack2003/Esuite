package dev.esuite.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CodecTest {
    @Test fun base64UrlAndHexRoundTripRealUtf8Bytes() {
        val value = "বাংলা 🔎"
        assertEquals(value, Codec.base64UrlDecode(Codec.base64UrlEncode(value)))
        assertEquals(value, Codec.hexDecode(Codec.hexEncode(value).uppercase()))
        assertEquals("002041", Codec.hexEncode("\u0000 A"))
        assertThrows(IllegalArgumentException::class.java) { Codec.hexDecode("F") }
        assertThrows(IllegalArgumentException::class.java) { Codec.hexDecode("ＦＦ") }
        assertThrows(IllegalArgumentException::class.java) { Codec.hexDecode("ff") }
    }

    @Test fun urlComponentsAndFormsKeepTheirDifferentPlusSemantics() {
        val value = "a+b c/🧪*~"
        val encoded = "a%2Bb%20c%2F%F0%9F%A7%AA%2A~"
        assertEquals(encoded, Codec.componentEncode(value))
        assertEquals(value, Codec.componentDecode(encoded))
        assertEquals("a+b", Codec.componentDecode("a+b"))
        assertEquals("a b", Codec.formDecode("a+b"))
        assertEquals("界".repeat(25_000), Codec.componentDecode("%E7%95%8C".repeat(25_000)))
        listOf("%", "%2", "%GG", "%C3", "%FE", "%00%FF").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { Codec.componentDecode(input) }
        }
    }

    @Test fun conversionPreservesByteSequencesThatCannotBeDecodedAsText() {
        assertEquals("AP+A", Codec.convertBytes("00 FF\n80", ByteEncoding.Hex, ByteEncoding.Base64))
        assertEquals("AP-A", Codec.convertBytes("AP+\nA", ByteEncoding.Base64, ByteEncoding.Base64Url))
        assertEquals("00ff80", Codec.convertBytes("AP-A", ByteEncoding.Base64Url, ByteEncoding.Hex))
        assertEquals("ff", Codec.convertBytes("/w==", ByteEncoding.Base64, ByteEncoding.Hex))
    }

    @Test fun byteConversionChecksEncodingAndInputLimits() {
        for (value in listOf("a", "0g", "０Ｆ")) {
            assertThrows(IllegalArgumentException::class.java) { Codec.convertBytes(value, ByteEncoding.Hex, ByteEncoding.Base64) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Codec.convertBytes("AP+A", ByteEncoding.Base64Url, ByteEncoding.Hex)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Codec.convertBytes(" ".repeat(Limits.REQUEST_BYTES + 1), ByteEncoding.Hex, ByteEncoding.Base64)
        }
    }

    @Test fun jsonFormattingIgnoresBracketsAndEscapedQuotesInsideStrings() {
        val compact = """{"nested":[1e-20,{"text":"\"[{]"}],"large":123456789012345678901}"""
        val pretty = Codec.formatJson(compact, compact = false)
        assertEquals(compact, Codec.formatJson(pretty, compact = true))
        assertThrows(IllegalArgumentException::class.java) {
            Codec.formatJson("[".repeat(129) + "]".repeat(129), compact = true)
        }
    }

    @Test fun unicodeAndEmptyBase64RoundTrip() {
        listOf("", "ESuite", "বাংলা 🔎", "a\u0000b").forEach { value -> assertEquals(value, Codec.base64Decode(Codec.base64Encode(value))) }
        assertEquals("RVN1aXRl", Codec.base64Encode("ESuite"))
    }
    @Test fun rejectsInvalidBase64AndMalformedUtf8() {
        assertThrows(Exception::class.java) { Codec.base64Decode("%%invalid%%") }
        assertThrows(Exception::class.java) { Codec.base64Decode("/w==") }
    }
    @Test fun formEncodingHandlesReservedCharactersAndSpaces() {
        assertEquals("a%2Bb+c%26d", Codec.formEncode("a+b c&d"))
        assertEquals("a+b c&d", Codec.formDecode("a%2Bb+c%26d"))
        assertThrows(IllegalArgumentException::class.java) { Codec.formDecode("%zz") }
    }
    @Test fun hashesMatchKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Codec.sha256(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Codec.sha256("abc"))
        assertEquals("ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
            "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f", Codec.sha512("abc"))
    }
}

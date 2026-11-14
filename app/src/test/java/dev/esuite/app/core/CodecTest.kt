package dev.esuite.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CodecTest {
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
    }
}

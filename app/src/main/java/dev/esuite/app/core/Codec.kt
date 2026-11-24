package dev.esuite.app.core

import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json

internal enum class ByteEncoding(val label: String) { Base64("Base64"), Base64Url("Base64URL"), Hex("Hex") }

/** Pure local transformations. No network, persistence or Android dependencies. */
object Codec {
    private const val hexDigits = "0123456789abcdef"

    fun base64Encode(input: String): String = Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
    fun base64Decode(input: String): String = utf8(Base64.getDecoder().decode(input))
    fun base64UrlEncode(input: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(input.toByteArray(Charsets.UTF_8))
    fun base64UrlDecode(input: String): String = utf8(Base64.getUrlDecoder().decode(input))
    fun hexEncode(input: String): String = input.toByteArray(Charsets.UTF_8).hex()
    fun hexDecode(input: String): String = utf8(hexBytes(input))

    internal fun convertBytes(input: String, from: ByteEncoding, to: ByteEncoding): String {
        require(input.length <= Limits.REQUEST_BYTES) { "Shorten the input before converting it." }
        val compact = input.filterNot(Char::isWhitespace)
        val bytes = when (from) {
            ByteEncoding.Base64 -> Base64.getDecoder().decode(compact)
            ByteEncoding.Base64Url -> Base64.getUrlDecoder().decode(compact)
            ByteEncoding.Hex -> hexBytes(compact)
        }
        return when (to) {
            ByteEncoding.Base64 -> Base64.getEncoder().encodeToString(bytes)
            ByteEncoding.Base64Url -> Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            ByteEncoding.Hex -> bytes.hex()
        }
    }

    /** Form URL encoding: spaces become '+'. This is not URL path normalization. */
    fun formEncode(input: String): String = URLEncoder.encode(input, StandardCharsets.UTF_8.name())
    fun formDecode(input: String): String = componentDecode(input.replace('+', ' '))
    fun componentEncode(input: String): String = formEncode(input).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
    fun componentDecode(input: String): String = buildString(input.length) {
        var offset = 0
        while (offset < input.length) {
            var next = input.indexOf('%', offset)
            if (next == -1) {
                append(input, offset, input.length)
                break
            }
            append(input, offset, next)
            val bytes = ByteArrayOutputStream()
            while (next < input.length && input[next] == '%') {
                require(next + 2 < input.length) { "Complete each percent escape with two hexadecimal digits." }
                bytes.write(hexByte(input, next + 1).toInt())
                next += 3
            }
            append(utf8(bytes.toByteArray()))
            offset = next
        }
    }

    fun sha256(input: String): String = digest(input, "SHA-256")
    fun sha512(input: String): String = digest(input, "SHA-512")
    fun formatJson(input: String, compact: Boolean): String {
        require(input.length <= Limits.REQUEST_BYTES) { "Shorten the JSON before formatting it." }
        val result = StringBuilder(input.length)
        var depth = 0
        var quoted = false
        var escaped = false
        var previous = ' '
        for (char in input) {
            if (quoted) {
                result.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> { quoted = false; previous = char }
                }
                continue
            }
            if (char in " \t\r\n") continue
            val closes = char == '}' || char == ']'
            val opens = char == '{' || char == '['
            val afterOpening = previous == '{' || previous == '['
            if (closes) {
                depth--
                require(depth >= 0) { "Check the document's closing braces and brackets." }
            }
            if (!compact && ((closes && !afterOpening) || (!closes && afterOpening) || previous == ',')) {
                result.append('\n')
                repeat(depth * 2) { result.append(' ') }
            }
            result.append(char)
            if (!compact && char == ':') result.append(' ')
            when {
                char == '"' -> quoted = true
                opens -> {
                    depth++
                    require(depth <= 128) { "JSON nesting exceeds 128 levels." }
                }
            }
            require(result.length <= ApiImports.COLLECTION_BYTES) { "Formatted JSON is too large. Try minifying it or use a smaller document." }
            previous = char
        }
        Json.parseToJsonElement(input)
        return result.toString()
    }

    private fun digest(input: String, algorithm: String): String =
        MessageDigest.getInstance(algorithm).digest(input.toByteArray(StandardCharsets.UTF_8)).hex()

    private fun hexBytes(input: String): ByteArray {
        require(input.length % 2 == 0) { "Use two hexadecimal digits per byte." }
        return ByteArray(input.length / 2) { index -> hexByte(input, index * 2) }
    }
    private fun ByteArray.hex(): String = buildString(size * 2) {
        for (byte in this@hex) {
            val value = byte.toInt() and 0xff
            append(hexDigits[value ushr 4])
            append(hexDigits[value and 0x0f])
        }
    }
    private fun utf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("The decoded bytes are not valid UTF-8.")
    }
    private fun hexByte(input: String, offset: Int): Byte {
        val high = hexDigits.indexOf(input[offset].lowercaseChar())
        val low = hexDigits.indexOf(input[offset + 1].lowercaseChar())
        require(high >= 0 && low >= 0) { "Use hexadecimal digits 0–9 and A–F." }
        return ((high shl 4) + low).toByte()
    }
}

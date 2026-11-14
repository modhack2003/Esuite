package dev.esuite.app.core

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** Pure local transformations. No network, persistence or Android dependencies. */
object Codec {
    fun base64Encode(input: String): String = Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
    fun base64Decode(input: String): String = StandardCharsets.UTF_8.newDecoder()
        .decode(ByteBuffer.wrap(Base64.getDecoder().decode(input))).toString()
    /** Form URL encoding: spaces become '+'. This is not URL path normalization. */
    fun formEncode(input: String): String = URLEncoder.encode(input, StandardCharsets.UTF_8.name())
    fun formDecode(input: String): String = URLDecoder.decode(input, StandardCharsets.UTF_8.name())
    fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

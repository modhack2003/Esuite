package dev.esuite.app.transport

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object CapturedText {
    fun decode(bytes: ByteArray, charset: Charset, truncated: Boolean): String {
        if (!truncated) return String(bytes, charset)
        // Non-final input leaves only the incomplete tail unconsumed. Interior errors keep
        // the existing replacement behavior; IGNORE would silently remove server data.
        val decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(maxOf(1, (bytes.size * decoder.maxCharsPerByte()).toInt() + 1))
        decoder.decode(input, output, false).throwIfError()
        output.flip()
        return output.toString()
    }
    private fun java.nio.charset.CoderResult.throwIfError() { if (isError) throwException() }
}

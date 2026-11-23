package dev.esuite.app.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

internal suspend fun readCollectionDocument(input: InputStream): String {
    val context = currentCoroutineContext()
    val buffer = ByteArray(8192)
    val output = ByteArrayOutputStream(buffer.size)
    while (true) {
        context.ensureActive()
        val allowance = ApiImports.COLLECTION_BYTES + 1 - output.size()
        val count = input.read(buffer, 0, minOf(buffer.size, allowance))
        if (count == -1) break
        require(output.size() + count <= ApiImports.COLLECTION_BYTES) { "The document exceeds the 2 MiB import limit." }
        output.write(buffer, 0, count)
    }
    context.ensureActive()
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\uFEFF")
    } catch (_: CharacterCodingException) {
        throw IllegalArgumentException("Use a JSON document saved as UTF-8.")
    }
}

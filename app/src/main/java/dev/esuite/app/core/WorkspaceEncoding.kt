package dev.esuite.app.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.OutputStream

/** Count UTF-8 bytes without first building a whole-vault UTF-16 String. */
@OptIn(ExperimentalSerializationApi::class)
object WorkspaceEncoding {
    private val json = Json { encodeDefaults = true }
    private class CapacityExceeded : IOException()

    fun sizeUpTo(workspace: Workspace, limit: Int): Int? {
        var count = 0
        val counter = object : OutputStream() {
            override fun write(value: Int) { if (count == limit) throw CapacityExceeded(); count++ }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (length > limit - count) throw CapacityExceeded()
                count += length
            }
        }
        return try { json.encodeToStream(workspace, counter); count } catch (_: CapacityExceeded) { null }
    }

    fun encodeExact(workspace: Workspace, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var position = 0
        val output = object : OutputStream() {
            override fun write(value: Int) { check(position < bytes.size); bytes[position++] = value.toByte() }
            override fun write(source: ByteArray, offset: Int, length: Int) {
                check(length <= bytes.size - position)
                source.copyInto(bytes, position, offset, offset + length)
                position += length
            }
        }
        json.encodeToStream(workspace, output)
        check(position == size) { "Workspace changed during encoding" }
        return bytes
    }
}

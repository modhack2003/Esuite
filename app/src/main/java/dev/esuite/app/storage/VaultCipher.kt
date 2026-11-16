package dev.esuite.app.storage

import dev.esuite.app.core.Limits
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated envelope. Each encryption obtains a fresh provider-generated nonce. */
object VaultCipher {
    private val magic = byteArrayOf(69, 83, 86, 49) // ESV1
    fun seal(plaintext: ByteArray, key: SecretKey): ByteArray {
        require(plaintext.size <= Limits.VAULT_BYTES) { "Workspace exceeds storage limit" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(magic)
        require(cipher.iv.size == 12) { "Unsupported GCM nonce length" }
        val envelope = ByteArray(plaintext.size + 32)
        magic.copyInto(envelope); cipher.iv.copyInto(envelope, 4)
        finishInChunks(cipher, plaintext, 0, plaintext.size, envelope, 16)
        return envelope
    }
    fun open(envelope: ByteArray, key: SecretKey): ByteArray {
        require(envelope.size in 32..Limits.VAULT_BYTES + 32 && envelope.copyOfRange(0, 4).contentEquals(magic)) { "Invalid workspace envelope" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(4, 16)))
        cipher.updateAAD(magic)
        val plaintext = ByteArray(envelope.size - 32)
        finishInChunks(cipher, envelope, 16, envelope.size - 16, plaintext, 0)
        return plaintext
    }

    private fun finishInChunks(cipher: Cipher, input: ByteArray, offset: Int, length: Int,
                               output: ByteArray, outputOffset: Int) {
        // Android 9's Keystore streamer drops middle output chunks for a single large update.
        // Keep each call below its 64 KiB chunk size; retain the existing ESV1/GCM envelope.
        var written = outputOffset
        fun append(bytes: ByteArray?) {
            if (bytes == null) return
            check(bytes.size <= output.size - written) { "Unexpected encrypted workspace length" }
            bytes.copyInto(output, written)
            written += bytes.size
        }
        var consumed = 0
        while (consumed < length) {
            val count = minOf(32 * 1024, length - consumed)
            append(cipher.update(input, offset + consumed, count))
            consumed += count
        }
        // No plaintext is returned until the authentication tag has been verified successfully.
        append(cipher.doFinal())
        check(written == output.size) { "Incomplete encrypted workspace output" }
    }
}

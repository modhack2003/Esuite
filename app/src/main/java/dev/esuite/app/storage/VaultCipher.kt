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
        return magic + cipher.iv + cipher.doFinal(plaintext)
    }
    fun open(envelope: ByteArray, key: SecretKey): ByteArray {
        require(envelope.size in 32..Limits.VAULT_BYTES + 32 && envelope.copyOfRange(0, 4).contentEquals(magic)) { "Invalid workspace envelope" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(4, 16)))
        cipher.updateAAD(magic)
        return cipher.doFinal(envelope.copyOfRange(16, envelope.size))
    }
}

package dev.esuite.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.esuite.app.core.*
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Call on Dispatchers.IO. No plaintext project files or plaintext fallback are created. */
class WorkspaceVault(context: Context) : WorkspaceStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "workspace.esv"))
    private val alias = "esuite_workspace_aes_v1"
    private fun key(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Workspace encryption key is missing or invalidated; no replacement key was generated" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized override fun load(): Workspace {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return Workspace()
        val envelope = file.openRead().use {
            require(it.channel.size() <= Limits.VAULT_BYTES + 32) { "Workspace file exceeds limit" }
            it.readBytes()
        }
        val decoded = VaultCipher.open(envelope, key(create = false))
        // Route decrypted bytes through the forward-only schema migration seam: migrate() parses the
        // JSON, reads the version, applies ordered migrations up to CURRENT_VERSION, and throws
        // "Unsupported workspace version" for newer/missing-path (corrupt/undecodable JSON also throws
        // inside migrate). load() performs no writes, so any throw leaves the file byte-identical.
        return WorkspaceSchema.migrate(decoded.toString(Charsets.UTF_8)).also {
            require(it.projects.size <= 20 && it.savedRequests.size <= 100 && it.findings.size <= Limits.FINDINGS && it.exchanges.size <= Limits.HISTORY) { "Workspace record limits exceeded" }
            it.projects.forEach { p -> require(ScopePolicy(p.origin).target.isHttps) { "Workspace contains an unsupported HTTP project" } }
        }
    }
    @Synchronized override fun save(workspace: Workspace): Workspace {
        val budget = WorkspacePersistence.byteBudgetForHeap(Runtime.getRuntime().maxMemory())
        val (bounded, bytes) = WorkspacePersistence.prepare(workspace, budget)
        val hasExistingVault = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        val envelope = VaultCipher.seal(bytes, key(create = !hasExistingVault))
        VerifiedAtomicWrite.write(file, envelope)
        return bounded
    }
    @Synchronized override fun erase() {
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}

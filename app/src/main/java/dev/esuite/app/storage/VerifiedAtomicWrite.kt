package dev.esuite.app.storage

import android.util.AtomicFile
import java.io.FileOutputStream
import java.io.IOException

/** AtomicFile can log a sync/rename failure without throwing; verify before acknowledging. */
internal object VerifiedAtomicWrite {
    fun write(file: AtomicFile, envelope: ByteArray, finish: (FileOutputStream) -> Unit = file::finishWrite) {
        val stream = file.startWrite()
        try {
            stream.write(envelope)
            stream.fd.sync()
        } catch (e: Exception) { file.failWrite(stream); throw e }
        // After finish may have committed, old Android failWrite can delete the new base.
        // Do not attempt rollback here. openRead applies each OS's backup recovery rules;
        // direct baseFile reads would miss an uncommitted .bak state on Android 9.
        try { finish(stream) } catch (e: Exception) { runCatching { stream.close() }; throw e }
        val verified = file.openRead().use { input ->
            input.channel.size() == envelope.size.toLong() && input.readBytes().contentEquals(envelope)
        }
        if (!verified) throw IOException("Encrypted commit could not be verified")
    }
}

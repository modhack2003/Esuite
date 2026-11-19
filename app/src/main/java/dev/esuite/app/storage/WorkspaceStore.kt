package dev.esuite.app.storage

import dev.esuite.app.core.Workspace

/** Synchronous storage boundary; callers serialize access on the storage dispatcher. */
interface WorkspaceStore {
    fun load(): Workspace
    fun save(workspace: Workspace): Workspace
    fun erase()
}

package dev.esuite.app.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** All parsing/encoding happens on the worker; applying a prepared result never encodes. */
class CollectionImporter(private val worker: CoroutineDispatcher = Dispatchers.Default) {
    suspend fun parse(parse: () -> List<SavedRequest>): List<SavedRequest> = withContext(worker) { parse() }

    suspend fun prepare(snapshot: Workspace, project: Project, requests: List<SavedRequest>): Prepared = withContext(worker) {
        require(snapshot.projects.any { it.id == project.id && it.origin == project.origin }) { "Import project no longer exists" }
        require(requests.all { it.projectId == project.id && ScopePolicy(project.origin).allows(it.draft.url) }) { "Import contains an out-of-scope request" }
        require(requests.size + snapshot.savedRequests.size <= 100) { "Collection limit is 100 requests. Remove saved items before importing more." }
        val next = snapshot.copy(savedRequests = requests + snapshot.savedRequests)
        WorkspaceBudget.requireDurableCapacity(next)
        Prepared(snapshot, next)
    }

    data class Prepared(val snapshot: Workspace, val candidate: Workspace) {
        fun apply(current: Workspace): Workspace? = if (WorkspacePersistence.sameDurable(snapshot, current))
            current.copy(savedRequests = candidate.savedRequests) else null
    }
}

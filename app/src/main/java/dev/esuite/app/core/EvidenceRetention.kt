package dev.esuite.app.core

/**
 * Bounded durable evidence snapshots for saved observations.
 *
 * A [Finding] keeps [Finding.evidenceIds] as the authoritative reference list and [Finding.evidence]
 * as immutable bounded copies of the referenced exchanges. The copies live in the durable region, so
 * none of the paths that dispose of [Workspace.exchanges] — history rollover past [Limits.HISTORY],
 * clearing a project's history, save-time exchange halving in [WorkspacePersistence.prepare] or
 * project deletion — can orphan retained evidence. A reference with no snapshot is an explicit
 * missing state: [missing] is exactly the set difference.
 *
 * Snapshots deliberately reuse [Exchange] so [Redactor.exchange], and replay all
 * work on a snapshot unchanged.
 */
object EvidenceRetention {
    /**
     * Bounded immutable copy of [exchange]: both bodies are capped at [Limits.EVIDENCE_BYTES] UTF-8
     * bytes. A cut response keeps the app's existing [StoredBody.truncated] label; a base64 body is
     * cut on a 4-character group so the retained value stays decodable.
     */
    fun snapshot(exchange: Exchange): Exchange {
        var response = utf8Preview(exchange.response.value, Limits.EVIDENCE_BYTES)
        val cut = response.length < exchange.response.value.length
        if (cut && exchange.response.encoding == "base64") response = response.take(response.length / 4 * 4)
        return exchange.copy(
            request = exchange.request.copy(body = utf8Preview(exchange.request.body, Limits.EVIDENCE_BYTES)),
            response = exchange.response.copy(value = response, truncated = exchange.response.truncated || cut),
        )
    }

    /** Referenced evidence ids with no retained snapshot, in reference order. */
    fun missing(finding: Finding): List<String> {
        val retained = finding.evidence.map { it.id }.toSet()
        return finding.evidenceIds.filterNot { it in retained }
    }

    /**
     * Snapshot every missing reference that still resolves to a live exchange, bounded by [budget]
     * durable bytes: [cost] reports what one snapshot adds, and snapshots are admitted in reference
     * order while the running total still fits. Unresolvable ids — and any snapshot the budget
     * cannot afford — are left alone so they keep reporting the explicit missing state.
     *
     * The budget is required rather than optional because this repair runs where no admission check
     * does ([WorkspaceSchema]'s `v2 -> v3` step), unlike `addFinding`, which admits a finding
     * together with its snapshot through [WorkspaceBudget.requireDurableCapacity].
     */
    fun backfill(findings: List<Finding>, exchanges: List<Exchange>, budget: Int, cost: (Exchange) -> Int): List<Finding> {
        if (findings.none { missing(it).isNotEmpty() }) return findings
        val available = exchanges.associateBy { it.id }
        var remaining = budget
        return findings.map { finding ->
            val restored = mutableListOf<Exchange>()
            for (id in missing(finding)) {
                val snapshot = available[id]?.let(::snapshot) ?: continue
                val size = cost(snapshot)
                if (size > remaining) continue
                remaining -= size
                restored += snapshot
            }
            if (restored.isEmpty()) finding else finding.copy(evidence = finding.evidence + restored)
        }
    }

}

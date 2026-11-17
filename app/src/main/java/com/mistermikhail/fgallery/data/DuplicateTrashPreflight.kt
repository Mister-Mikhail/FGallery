package com.mistermikhail.fgallery.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * All-or-nothing validation before a Stage 6 batch starts.
 * Does not move any file. The mutation layer must repeat validation immediately
 * before each individual move to catch changes after this preflight.
 */
internal suspend fun <T> preflightDuplicateTrash(
    items: List<T>,
    verify: suspend (T) -> Boolean,
    sourceIdentity: (T) -> String,
    physicalIdentity: (T) -> String? = { null },
): Boolean {
    currentCoroutineContext().ensureActive()
    if (items.isEmpty()) return false
    // A duplicated source would be moved twice, possibly after its original
    // URI has disappeared. Reject it before performing any byte reads or moves.
    val identities = items.map(sourceIdentity)
    if (identities.any(String::isBlank) || identities.toSet().size != items.size) return false
    // Different content URIs may alias one physical file. Where the caller can
    // resolve a trustworthy local path, reject repeated paths before any I/O.
    // Unknown SAF paths remain null and are never conflated with one another.
    // A path containing "." or ".." is the same source as its normalized
    // counterpart. Compare lexical identities before reading any bytes; this
    // does not follow symlinks or infer identities for opaque SAF documents.
    val rawPaths = items.mapNotNull(physicalIdentity)
    if (rawPaths.any(String::isBlank)) return false
    val knownPaths = rawPaths.map { java.io.File(it).normalize().path }
    if (knownPaths.toSet().size != knownPaths.size) return false
    for (item in items) {
        currentCoroutineContext().ensureActive()
        if (!verify(item)) return false
        // A verifier may be synchronous or catch an I/O cancellation internally.
        // Never return an authorized batch from a cancelled worker.
        currentCoroutineContext().ensureActive()
    }
    currentCoroutineContext().ensureActive()
    return true
}

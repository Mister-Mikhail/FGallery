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
    protectedSourceIdentities: Set<String> = emptySet(),
    protectedPhysicalIdentities: Set<String> = emptySet(),
): Boolean {
    currentCoroutineContext().ensureActive()
    if (items.isEmpty()) return false
    // A duplicated source would be moved twice, possibly after its original
    // URI has disappeared. Reject it before performing any byte reads or moves.
    val identities = items.map(sourceIdentity)
    if (identities.any(String::isBlank) || identities.toSet().size != items.size) return false
    // Never authorize a protected original, even when the caller accidentally
    // includes it in a manually selected batch.
    if (identities.any { it in protectedSourceIdentities }) return false
    // Different content URIs may alias one physical file. Where the caller can
    // resolve a trustworthy local path, reject repeated paths before any I/O.
    // Unknown SAF paths remain null and are never conflated with one another.
    // A path containing "." or ".." is the same source as its normalized
    // counterpart. Compare lexical identities before reading any bytes; this
    // follows existing filesystem symlinks but never infers opaque SAF identities.
    val rawPaths = items.mapNotNull(physicalIdentity)
    if (rawPaths.any(String::isBlank)) return false
    val knownPaths = rawPaths.map { java.io.File(it).canonicalPath }
    if (knownPaths.toSet().size != knownPaths.size) return false
    // Provider aliases may have different URIs but the same underlying path.
    // Check protected originals as well as duplicate selected copies.
    val protectedPaths = protectedPhysicalIdentities.map { path ->
        if (path.isBlank()) return false
        java.io.File(path).canonicalPath
    }.toSet()
    if (knownPaths.any { it in protectedPaths }) return false
    for (item in items) {
        currentCoroutineContext().ensureActive()
        val valid = try {
            verify(item)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) {
            // A SAF provider may revoke access during verification.
            // Ordinary I/O failure must never authorize a partial batch.
            return false
        }
        currentCoroutineContext().ensureActive()
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Duplicate preflight interrupted")
        }
        if (!valid) return false
        // A verifier may be synchronous or catch an I/O cancellation internally.
        // Never return an authorized batch from a cancelled worker.
        currentCoroutineContext().ensureActive()
    }
    currentCoroutineContext().ensureActive()
    return true
}

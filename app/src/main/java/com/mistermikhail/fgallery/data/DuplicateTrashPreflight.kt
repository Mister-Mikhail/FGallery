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
    val identities = try { items.map(sourceIdentity) } catch (cancelled: java.util.concurrent.CancellationException) {
        throw cancelled
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } catch (_: Exception) {
        return false
    }
    if (identities.any { it.isBlank() || '\u0000' in it } || identities.toSet().size != items.size) return false
    // Never authorize a protected original, even when the caller accidentally
    // includes it in a manually selected batch.
    // Snapshot the keeper URI set: a catalogue refresh during slow SHA reads
    // must invalidate consent rather than silently change which originals are protected.
    val initialProtectedSources = try { protectedSourceIdentities.toSet() } catch (_: Exception) { return false }
    if (initialProtectedSources.any { it.isBlank() || '\\u0000' in it }) return false
    if (identities.any { it in initialProtectedSources }) return false
    // Different content URIs may alias one physical file. Where the caller can
    // resolve a trustworthy local path, reject repeated paths before any I/O.
    // Unknown SAF paths remain null and are never conflated with one another.
    // A path containing "." or ".." is the same source as its normalized
    // counterpart. Compare lexical identities before reading any bytes; this
    // follows existing filesystem symlinks but never infers opaque SAF identities.
    val rawPaths = try { items.map(physicalIdentity) } catch (cancelled: java.util.concurrent.CancellationException) {
        throw cancelled
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } catch (_: Exception) {
        return false
    }
    if (rawPaths.any { it != null && (it.isBlank() || '\u0000' in it) }) return false
    val knownPaths = try { rawPaths.map { it?.let { path -> java.io.File(path).canonicalPath } } } catch (_: java.io.IOException) { return false } catch (_: SecurityException) { return false }
    if (knownPaths.filterNotNull().toSet().size != knownPaths.count { it != null }) return false
    // Provider aliases may have different URIs but the same underlying path.
    // Check protected originals as well as duplicate selected copies.
    val protectedPaths = try {
        protectedPhysicalIdentities.map { path ->
            if (path.isBlank() || '\u0000' in path) return false
            java.io.File(path).canonicalPath
        }.toSet()
    } catch (_: java.io.IOException) { return false } catch (_: SecurityException) { return false }
    if (knownPaths.any { it in protectedPaths }) return false
    // SAF providers can rebind a URI while another copy is being verified.
    // Recheck the identities after all reads, before returning a preflight
    // success; this is still not a substitute for per-file mutation checks.
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
    val finalIdentities = try { items.map(sourceIdentity) } catch (cancelled: java.util.concurrent.CancellationException) {
        throw cancelled
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } catch (_: Exception) {
        return false
    }
    if (finalIdentities != identities) return false
    val finalProtectedSources = try { protectedSourceIdentities.toSet() } catch (_: Exception) { return false }
    if (finalProtectedSources != initialProtectedSources || identities.any { it in finalProtectedSources }) return false
    val finalPaths = try { items.map(physicalIdentity) } catch (cancelled: java.util.concurrent.CancellationException) {
        throw cancelled
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } catch (_: Exception) {
        return false
    }
    if (finalPaths != rawPaths) return false
    // A symlink can be retargeted without changing its path string. Re-resolve
    // both selected paths and protected originals after verification, otherwise
    // an alias of the keeper may become actionable during a slow SAF read.
    val finalCanonicalPaths = try {
        finalPaths.map { it?.let { path -> java.io.File(path).canonicalPath } }
    } catch (_: java.io.IOException) { return false } catch (_: SecurityException) { return false }
    val finalProtectedPaths = try {
        protectedPhysicalIdentities.map { java.io.File(it).canonicalPath }.toSet()
    } catch (_: java.io.IOException) { return false } catch (_: SecurityException) { return false }
    if (finalCanonicalPaths != knownPaths || finalProtectedPaths != protectedPaths) return false
    if (finalCanonicalPaths.filterNotNull().toSet().size != finalCanonicalPaths.count { it != null } ||
        finalCanonicalPaths.any { it in finalProtectedPaths }) return false
    currentCoroutineContext().ensureActive()
    return true
}

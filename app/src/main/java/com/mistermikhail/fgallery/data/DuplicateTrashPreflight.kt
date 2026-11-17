package com.mistermikhail.fgallery.data

/**
 * All-or-nothing validation before a Stage 6 batch starts.
 * Does not move any file. The mutation layer must repeat validation immediately
 * before each individual move to catch changes after this preflight.
 */
internal suspend fun <T> preflightDuplicateTrash(
    items: List<T>,
    verify: suspend (T) -> Boolean,
    sourceIdentity: (T) -> String,
): Boolean {
    if (items.isEmpty()) return false
    // A duplicated source would be moved twice, possibly after its original
    // URI has disappeared. Reject it before performing any byte reads or moves.
    val identities = items.map(sourceIdentity)
    if (identities.any(String::isBlank) || identities.toSet().size != items.size) return false
    for (item in items) {
        if (!verify(item)) return false
    }
    return true
}

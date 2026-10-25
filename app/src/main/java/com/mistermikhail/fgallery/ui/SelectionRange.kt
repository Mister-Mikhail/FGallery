package com.mistermikhail.fgallery.ui

/** Inclusive selection between two long presses in the current on-screen sort order. */
internal fun inclusiveSelectionRange(
    orderedIds: List<Long>, anchor: Long?, target: Long, selected: Set<Long>,
): Set<Long> {
    val a = orderedIds.indexOf(anchor)
    val b = orderedIds.indexOf(target)
    if (a < 0 || b < 0 || selected.isEmpty()) return selected + target
    return selected + orderedIds.subList(minOf(a, b), maxOf(a, b) + 1)
}

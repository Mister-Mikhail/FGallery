package com.mistermikhail.fgallery.ui

/** Scrolling may change candidates without ending the currently visible preview's lifetime. */
internal class LivePreviewRotation {
    var activeId: Long? = null
        private set
    private var startedAt = 0L
    private var idleSince: Long? = null

    fun update(visible: List<Long>, scrolling: Boolean, now: Long): Long? {
        if (activeId !in visible) activeId = null
        if (scrolling) idleSince = null else if (idleSince == null) idleSince = now
        val current = activeId
        if (current != null) {
            if (!scrolling && now - startedAt >= 5_000L && visible.size > 1) {
                activeId = visible[(visible.indexOf(current) + 1) % visible.size]
                startedAt = now
            }
        } else if (!scrolling && visible.isNotEmpty() && now - (idleSince ?: now) >= 700L) {
            activeId = visible.first()
            startedAt = now
        }
        return activeId
    }
}

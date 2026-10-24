package com.mistermikhail.fgallery.data

/** Android 15+ limits a MediaStore write consent request to at most 2000 URIs. */
internal object MediaStoreWriteBatch {
    const val MAX_PERMISSION_ITEMS = 2000

    /** Caller retries only failed items, never the original selection after partial success. */
    fun <T> next(remaining: List<T>): List<T> = remaining.take(MAX_PERMISSION_ITEMS)
}

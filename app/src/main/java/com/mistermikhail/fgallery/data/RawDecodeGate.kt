package com.mistermikhail.fgallery.data

import kotlinx.coroutines.sync.Mutex

/**
 * One native RAW demosaic at a time, shared by album thumbnails and the
 * full-resolution viewer. A 100 MP file can allocate hundreds of megabytes
 * inside LibRaw even in half-size mode. The old independent thumbnail
 * semaphores allowed multiple native decoders to overlap and exhaust memory.
 *
 * JPEG extraction and regular image/video thumbnails do not use this lock.
 */
internal object RawDecodeGate {
    val mutex = Mutex()
}

package com.mistermikhail.fgallery.data

import java.io.File
import java.io.FileInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest

/**
 * A hash may only be attached to the exact indexed file generation. In
 * particular, a stale Room row must not receive a digest of bytes overwritten
 * by another app between MediaStore scans. MediaStore exposes second-granular
 * mtime; accept only its rounded-down value (never a newer timestamp).
 * Unknown timestamps are deliberately skipped, not guessed.
 */
internal fun fingerprintMetadataMatchesIndexed(
    indexedSize: Long,
    indexedModifiedMillis: Long,
    observedSize: Long,
    observedModifiedMillis: Long,
    precisionMillis: Long = 0L,
): Boolean = indexedSize > 0L && indexedSize == observedSize &&
    indexedModifiedMillis > 0L && observedModifiedMillis > 0L &&
    observedModifiedMillis <= indexedModifiedMillis &&
    indexedModifiedMillis - observedModifiedMillis <= precisionMillis

/**
 * Stage 6: bounded-I/O exact-copy prefilter. Never decode a RAW to hash it.
 * Quick hashes are candidates only: matching quick hashes are NOT proof of
 * equality. Full SHA-256 is required before any exact-copy proposal.
 *
 * Call from the idle background queue, not on the UI thread.
 */
internal object DuplicateFingerprints {
    private const val EDGE_BYTES = 64 * 1024
    private const val STREAM_BYTES = 64 * 1024

    fun quick(file: File, checkActive: () -> Unit = {}): String =
        FileInputStream(file).use { quick(it.channel, checkActive) }

    /**
     * A seekable descriptor lets SAF/MediaStore files use the same 128 KiB
     * prefilter as physical files, without copying a large RAW to disk.
     * Non-seekable provider pipes are deliberately skipped by the caller.
     */
    fun quick(channel: SeekableByteChannel, checkActive: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val length = channel.size()
        digest.update("FGALLERY-EDGE-V1".toByteArray(Charsets.US_ASCII))
        repeat(8) { shift -> digest.update((length ushr (shift * 8)).toByte()) }
        channel.position(0L)
        updateExact(channel, digest, minOf(length, EDGE_BYTES.toLong()).toInt(), checkActive)
        if (length > EDGE_BYTES) {
            channel.position(length - EDGE_BYTES)
            updateExact(channel, digest, EDGE_BYTES, checkActive)
        }
        // SAF providers may replace/truncate a document while the two edges
        // are read. Never cache a digest for a different observed length.
        checkActive()
        if (channel.size() != length) throw IOException("File size changed during fingerprinting")
        return digest.digest().toHexString()
    }

    fun full(file: File, checkActive: () -> Unit = {}): String =
        FileInputStream(file).use { full(it, checkActive) }

    /** Full streaming digest; verify expected length for provider streams. */
    fun full(
        input: InputStream,
        checkActive: () -> Unit = {},
        expectedBytes: Long? = null,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BYTES)
        var total = 0L
        while (true) {
            checkActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) throw IOException("Fingerprint stream made no progress")
            total += read
            if (expectedBytes != null && total > expectedBytes) {
                throw IOException("Provider file grew during fingerprinting")
            }
            digest.update(buffer, 0, read)
        }
        if (expectedBytes != null && total != expectedBytes) {
            throw EOFException("Provider file changed during fingerprinting")
        }
        return digest.digest().toHexString()
    }

    private fun updateExact(
        channel: SeekableByteChannel,
        digest: MessageDigest,
        count: Int,
        checkActive: () -> Unit,
    ) {
        val buffer = ByteArray(STREAM_BYTES)
        var remaining = count
        while (remaining > 0) {
            checkActive()
            val read = channel.read(ByteBuffer.wrap(buffer, 0, minOf(buffer.size, remaining)))
            if (read < 0) throw EOFException("File changed during fingerprinting")
            if (read == 0) throw IOException("Fingerprint channel made no progress")
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}

/**
 * Cycle through pending fingerprint candidates in bounded windows. A group
 * of inaccessible SAF documents must not permanently starve readable files
 * later in the SQL result. The cursor is advanced across idle passes; this
 * helper never reads or modifies media.
 */
internal fun <T> fingerprintCandidateWindow(
    candidates: List<T>, start: Int, maxAttempts: Int,
): List<T> {
    if (candidates.isEmpty() || maxAttempts <= 0) return emptyList()
    val offset = Math.floorMod(start, candidates.size)
    return List(minOf(candidates.size, maxAttempts)) {
        candidates[(offset + it) % candidates.size]
    }
}

/**
 * Exact copies only. RAW+JPEG of the same exposure have different bytes and
 * cannot enter a group. Perceptual matches/crops require a separate validated
 * pipeline and must never be inferred from a similar hash alone.
 */
internal fun exactDuplicateGroups(rows: List<CatalogMediaRow>): List<List<CatalogMediaRow>> =
    rows.asSequence()
        // A URI may appear twice during a partial SAF/MediaStore reconciliation.
        // Repeated catalogue rows are one physical item, never two copies.
        .distinctBy { it.uri }
        .filterNot { it.recycled }
        .filter { it.sizeBytes > 0L && !it.quickHash.isNullOrBlank() && !it.fullHash.isNullOrBlank() }
        .groupBy { Triple(it.sizeBytes, it.quickHash, it.fullHash) }
        .values.filter { it.size > 1 }
        .map { it.sortedWith(exactCopyKeepPriority) }
        .sortedByDescending { it.size }

/**
 * Preferred file to KEEP within a verified exact-byte group. This only orders
 * suggestions; it never deletes files. Image edits and visually similar photos
 * are NOT part of these groups.
 *
 * The catalogue does not yet record JPEG quantization tables or full EXIF
 * completeness, so those criteria must be added before automatic proposals
 * of which lossy JPEG to retain. URI is a stable final tie-breaker.
 */
internal val exactCopyKeepPriority: Comparator<CatalogMediaRow> =
    compareByDescending<CatalogMediaRow> { duplicateFormatPriority(it) }
        .thenByDescending { it.width.toLong() * it.height.toLong() }
        .thenByDescending { it.sizeBytes }
        .thenBy { it.dateTakenMillis.takeIf { date -> date > 0L } ?: Long.MAX_VALUE }
        .thenByDescending { !it.exifCamera.isNullOrBlank() }
        .thenBy { it.uri }

private fun duplicateFormatPriority(row: CatalogMediaRow): Int {
    val ext = row.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return when (ext) {
        "dng", "cr2", "cr3", "nef", "nrw", "arw", "sr2", "srf",
        "orf", "rw2", "raf", "pef", "raw", "3fr", "fff", "iiq" -> 4
        "tif", "tiff" -> 3
        "heic", "heif" -> 2
        "jpg", "jpeg" -> 1
        else -> 0
    }
}

/**
 * Stage 6 exact-copy work planner. Size equality is only a candidate;
 * the edge hash is a prefilter and a full digest is required for a match.
 * Never hash a singleton or an already completed fingerprint.
 */
internal fun quickHashCandidates(rows: List<CatalogMediaRow>): List<CatalogMediaRow> =
    rows.asSequence().distinctBy { it.uri }.filter { !it.recycled && it.sizeBytes > 0L }
        .groupBy { it.sizeBytes }.values.asSequence()
        .filter { it.size > 1 }.flatten()
        .filter { it.quickHash == null }
        .sortedWith(compareBy<CatalogMediaRow> { it.sizeBytes }.thenBy { it.uri })
        .toList()

internal fun fullHashCandidates(rows: List<CatalogMediaRow>): List<CatalogMediaRow> =
    rows.asSequence().distinctBy { it.uri }.filter { !it.recycled && it.sizeBytes > 0L && !it.quickHash.isNullOrBlank() }
        .groupBy { it.sizeBytes to it.quickHash }.values.asSequence()
        .filter { it.size > 1 }.flatten()
        .filter { it.fullHash == null }
        .sortedWith(compareBy<CatalogMediaRow> { it.sizeBytes }.thenBy { it.uri })
        .toList()

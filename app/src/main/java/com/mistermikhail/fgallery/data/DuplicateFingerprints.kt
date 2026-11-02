package com.mistermikhail.fgallery.data

import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

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

    fun quick(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { stream ->
            val length = stream.length()
            // Include file length and domain separation to avoid mixing this
            // sampling digest with a complete SHA-256.
            digest.update("FGALLERY-EDGE-V1".toByteArray(Charsets.US_ASCII))
            repeat(8) { shift -> digest.update((length ushr (shift * 8)).toByte()) }
            val head = minOf(length, EDGE_BYTES.toLong()).toInt()
            updateExact(stream, digest, head)
            if (length > EDGE_BYTES) {
                stream.seek(length - EDGE_BYTES)
                updateExact(stream, digest, EDGE_BYTES)
            }
        }
        return digest.digest().toHexString()
    }

    fun full(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(STREAM_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun updateExact(stream: RandomAccessFile, digest: MessageDigest, count: Int) {
        val buffer = ByteArray(STREAM_BYTES)
        var remaining = count
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) throw java.io.EOFException("File changed during fingerprinting")
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}

/**
 * Exact copies only. RAW+JPEG of the same exposure have different bytes and
 * cannot enter a group. Perceptual matches/crops require a separate validated
 * pipeline and must never be inferred from a similar hash alone.
 */
internal fun exactDuplicateGroups(rows: List<CatalogMediaRow>): List<List<CatalogMediaRow>> =
    rows.asSequence()
        .filterNot { it.recycled }
        .filter { it.sizeBytes > 0L && !it.quickHash.isNullOrBlank() && !it.fullHash.isNullOrBlank() }
        .groupBy { Triple(it.sizeBytes, it.quickHash, it.fullHash) }
        .values.filter { it.size > 1 }
        .map { it.sortedWith(compareBy<CatalogMediaRow> { it.dateTakenMillis }.thenBy { it.uri }) }
        .sortedByDescending { it.size }

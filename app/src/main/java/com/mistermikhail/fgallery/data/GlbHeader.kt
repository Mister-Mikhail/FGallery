package com.mistermikhail.fgallery.data

/**
 * Strict GLB v2 container preflight; this does not imply that model extensions,
 * mesh codecs, textures or GPU resources are supported by the renderer.
 */
object GlbHeader {
    private const val HEADER_SIZE = 12
    private const val MAGIC = 0x46546C67 // ASCII 'glTF' in little-endian
    private const val JSON_CHUNK = 0x4E4F534A // ASCII 'JSON' in little-endian

    fun isValidPrefix(headerAndFirstChunk: ByteArray, fileSize: Long): Boolean {
        if (fileSize < 20 || headerAndFirstChunk.size < 20) return false
        fun uint32(offset: Int): Long =
            (0..3).fold(0L) { acc, i ->
                acc or ((headerAndFirstChunk[offset + i].toLong() and 0xffL) shl (i * 8))
            }
        if (uint32(0) != MAGIC.toLong() || uint32(4) != 2L) return false
        val declaredLength = uint32(8)
        if (declaredLength != fileSize || declaredLength < 20L || declaredLength % 4L != 0L) return false
        val jsonLength = uint32(12)
        if (uint32(16) != JSON_CHUNK.toLong()) return false
        if (jsonLength == 0L || jsonLength % 4L != 0L) return false
        return jsonLength <= declaredLength - 20L
    }
}

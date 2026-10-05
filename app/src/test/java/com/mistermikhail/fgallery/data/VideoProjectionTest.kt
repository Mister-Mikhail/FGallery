package com.mistermikhail.fgallery.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class VideoProjectionTest {
    private fun box(type: String, payload: ByteArray) = ByteBuffer.allocate(payload.size + 8)
        .putInt(payload.size + 8).put(type.toByteArray(Charsets.US_ASCII)).put(payload).array()
    private fun detect(bytes: ByteArray): Boolean {
        val file = File.createTempFile("projection-", ".mp4")
        try { file.writeBytes(bytes); return file.inputStream().channel.use { VideoProjection.containsProjection(it) } }
        finally { file.delete() }
    }
    @Test fun sphericalV2MetadataIsFoundInsideTheVideoSampleEntry() {
        val entry = box("avc1", ByteArray(78) + box("sv3d", box("proj", ByteArray(4))))
        val samples = box("stsd", ByteArray(4) + byteArrayOf(0, 0, 0, 1) + entry)
        val nested = listOf("stbl", "minf", "mdia", "trak", "moov").fold(samples) { data, type -> box(type, data) }
        assertTrue(detect(box("mdat", ByteArray(4096)) + nested))
    }
    @Test fun legacySphericalMetadataAndFlatMetadataStayDistinct() {
        val xml = "<rdf:RDF><GSpherical:Spherical>true</GSpherical:Spherical></rdf:RDF>"
        assertTrue(detect(box("uuid", ByteArray(16) + xml.toByteArray())))
        assertFalse(detect(box("uuid", ByteArray(16) + xml.replace("true", "false").toByteArray())))
    }
    @Test fun ordinaryVideoAndNamesDoNotImply360Projection() {
        assertFalse(detect(box("moov", box("udta", "360 spherical equirect 2:1".toByteArray()))))
        assertFalse(detect(box("mdat", box("sv3d", ByteArray(4)))))
    }
    @Test fun malformedAndTruncatedBoxesFailWithoutThrowing() {
        assertFalse(detect(byteArrayOf(0, 0, 0, 1, 109, 111, 111, 118)))
        assertFalse(detect(ByteBuffer.allocate(8).putInt(-1).put("moov".toByteArray()).array()))
    }
}

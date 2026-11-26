package com.mistermikhail.fgallery.data

import com.mistermikhail.fgallery.data.models.StlMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StlMeshTest {
    @Test fun binaryHeaderMayStartWithSolid() {
        val binary = ByteBuffer.allocate(134).order(ByteOrder.LITTLE_ENDIAN)
        binary.put("solid binary header".toByteArray())
        binary.position(80)
        binary.putInt(1)
        floatArrayOf(0f, 0f, 0f, -4f, 1f, 0f, 6f, 1f, 0f, 2f, 3f, 0f).forEach { binary.putFloat(it) }
        binary.putShort(0)
        binary.flip()
        assertTriangle(StlMesh.parse(binary) {})
    }

    @Test fun asciiWhitespaceAndBounds() {
        assertTriangle(StlMesh.parse(ByteBuffer.wrap(ascii.toByteArray())) {})
    }

    @Test fun truncatedAndNonFiniteInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StlMesh.parse(ByteBuffer.wrap(ascii.substringBefore("endfacet").toByteArray())) {}
        }
        assertThrows(IllegalArgumentException::class.java) {
            StlMesh.parse(ByteBuffer.wrap(ascii.replace("-4", "NaN").toByteArray())) {}
        }
    }

    private fun assertTriangle(mesh: StlMesh) {
        assertEquals(1, mesh.triangleCount)
        assertArrayEquals(floatArrayOf(-4f, 1f, 0f), mesh.min, 0f)
        assertArrayEquals(floatArrayOf(6f, 3f, 0f), mesh.max, 0f)
        assertEquals(72, mesh.byteCount)
    }

    private val ascii = """
        solid example
          facet normal 0 0 1
            outer  loop
              vertex -4 1 0
              vertex 6 1 0
              vertex 2 3 0
            endloop
          endfacet
        endsolid example
    """.trimIndent()
}

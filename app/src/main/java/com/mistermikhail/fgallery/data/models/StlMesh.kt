package com.mistermikhail.fgallery.data.models

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

internal class StlMesh private constructor(
    private val vertices: FloatArray,
    val triangleCount: Int,
    val min: FloatArray,
    val max: FloatArray,
) {
    val byteCount: Int get() = triangleCount * 3 * 6 * Float.SIZE_BYTES

    // glTF uses interleaved position and normal attributes.
    fun writeVertices(output: java.io.OutputStream) {
        val chunk = ByteBuffer.allocate(24 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until triangleCount * 18) {
            chunk.putFloat(vertices[index])
            if (!chunk.hasRemaining()) {
                output.write(chunk.array())
                chunk.clear()
            }
        }
        output.write(chunk.array(), 0, chunk.position())
    }

    companion object {
        const val MAX_TRIANGLES = 250_000
        private const val HEADER_BYTES = 84
        private const val TRIANGLE_BYTES = 50

        fun parse(data: ByteBuffer, checkActive: () -> Unit): StlMesh {
            val input = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val count = if (input.remaining() >= HEADER_BYTES) input.getInt(80).toUInt().toLong() else 0
            val binary = count != 0L && HEADER_BYTES + TRIANGLE_BYTES * count == input.remaining().toLong()
            val mesh = if (binary) {
                require(count <= MAX_TRIANGLES) { "В STL слишком много треугольников" }
                val builder = Builder(count.toInt())
                input.position(HEADER_BYTES)
                val facet = FloatArray(12)
                repeat(count.toInt()) { index ->
                    if (index % 1024 == 0) checkActive()
                    for (component in facet.indices) facet[component] = input.float
                    input.short // Attribute count
                    builder.add(facet)
                }
                builder
            } else {
                val stream = object : java.io.InputStream() {
                    override fun read() = if (input.hasRemaining()) input.get().toInt() and 0xff else -1
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        if (length == 0) return 0
                        val available = minOf(input.remaining(), length)
                        if (available == 0) return -1
                        input.get(bytes, offset, available)
                        return available
                    }
                }
                BufferedReader(InputStreamReader(stream, Charsets.US_ASCII)).use { readAscii(it, checkActive) }
            }
            return mesh.finish()
        }

        private fun readAscii(reader: BufferedReader, checkActive: () -> Unit): Builder {
            val builder = Builder(1024)
            require(reader.stlLine().startsWith("solid", ignoreCase = true)) { "Некорректный STL" }
            val facet = FloatArray(12)
            val whitespace = Regex("\\s+")
            fun vector(line: String, prefix: String, offset: Int) {
                require(line.startsWith(prefix, ignoreCase = true)) { "Некорректная грань STL" }
                val values = line.substring(prefix.length).trim().split(whitespace)
                require(values.size == 3) { "Некорректная координата STL" }
                values.forEachIndexed { index, value -> facet[offset + index] = value.toFloat() }
            }
            while (true) {
                checkActive()
                val line = reader.stlLine().replace(whitespace, " ")
                if (line.startsWith("endsolid", ignoreCase = true)) break
                vector(line, "facet normal ", 0)
                require(reader.stlLine().replace(whitespace, " ").equals("outer loop", ignoreCase = true)) { "Некорректный STL" }
                repeat(3) { vector(reader.stlLine().replace(whitespace, " "), "vertex ", 3 + it * 3) }
                require(reader.stlLine().equals("endloop", ignoreCase = true)) { "Некорректный STL" }
                require(reader.stlLine().equals("endfacet", ignoreCase = true)) { "Некорректный STL" }
                builder.add(facet)
            }
            return builder
        }

        private fun BufferedReader.stlLine(): String {
            while (true) {
                val line = StringBuilder()
                var character = read()
                while (character != -1 && character != '\n'.code) {
                    require(line.length < 4096) { "Некорректная строка STL" }
                    line.append(character.toChar())
                    character = read()
                }
                if (line.isNotBlank()) return line.toString().trim()
                require(character != -1) { "STL обрывается до конца модели" }
            }
        }
    }

    private class Builder(capacity: Int) {
        private var vertices = FloatArray(capacity * 18)
        private var count = 0
        private val min = FloatArray(3) { Float.POSITIVE_INFINITY }
        private val max = FloatArray(3) { Float.NEGATIVE_INFINITY }

        fun add(facet: FloatArray) {
            require(count < MAX_TRIANGLES) { "В STL слишком много треугольников" }
            require(facet.all { it.isFinite() && kotlin.math.abs(it) <= 1e15f }) { "Некорректное число в STL" }
            if (count * 18 == vertices.size) {
                vertices = vertices.copyOf(minOf(vertices.size * 2, MAX_TRIANGLES * 18))
            }
            val ux = facet[6].toDouble() - facet[3]
            val uy = facet[7].toDouble() - facet[4]
            val uz = facet[8].toDouble() - facet[5]
            val vx = facet[9].toDouble() - facet[3]
            val vy = facet[10].toDouble() - facet[4]
            val vz = facet[11].toDouble() - facet[5]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            val magnitude = sqrt(nx * nx + ny * ny + nz * nz)
            if (magnitude == 0.0) return // Degenerate facet has no surface.
            var offset = count * 18
            for (vertex in 0..2) {
                for (axis in 0..2) {
                    val value = facet[3 + vertex * 3 + axis]
                    vertices[offset++] = value
                    min[axis] = minOf(min[axis], value)
                    max[axis] = maxOf(max[axis], value)
                }
                vertices[offset++] = (nx / magnitude).toFloat()
                vertices[offset++] = (ny / magnitude).toFloat()
                vertices[offset++] = (nz / magnitude).toFloat()
            }
            count++
        }

        fun finish(): StlMesh {
            require(count > 0) { "В STL нет видимых граней" }
            return StlMesh(vertices, count, min, max)
        }
    }
}

package com.mistermikhail.fgallery.data.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mistermikhail.fgallery.data.GlbHeader
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_MODEL_FILE_BYTES = 48 * 1024 * 1024

internal object GlbPreview {
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942
    private const val MAX_JSON_BYTES = 2 * 1024 * 1024
    private const val MAX_TEXTURE_PIXELS = 16L * 1024 * 1024
    private val unsupportedExtensions = setOf(
        "KHR_draco_mesh_compression", "EXT_meshopt_compression", "KHR_meshopt_compression",
        "KHR_texture_basisu", "EXT_texture_webp", "EXT_texture_avif", "MSFT_texture_dds",
    )
    private val supportedRequiredExtensions = setOf(
        "KHR_materials_unlit", "KHR_texture_transform", "KHR_mesh_quantization",
        "KHR_materials_clearcoat", "KHR_materials_emissive_strength", "KHR_materials_ior",
        "KHR_materials_sheen", "KHR_materials_specular", "KHR_materials_transmission",
        "KHR_materials_volume", "KHR_lights_punctual",
    )

    fun prepare(source: ByteBuffer, output: File, textureEdge: Int, checkActive: () -> Unit) {
        val (json, binary) = parse(source)
        val views = json.optJSONArray("bufferViews") ?: error("GLB не содержит геометрию")
        require(views.length() in 1..4096) { "Слишком много блоков данных GLB" }
        val buffers = json.getJSONArray("buffers")
        require(buffers.length() == 1 && !buffers.getJSONObject(0).has("uri")) { "GLB содержит внешние ресурсы" }
        val buffer = buffers.getJSONObject(0)
        val declaredSize = buffer.getInt("byteLength")
        require(declaredSize in (binary.remaining() - 3)..binary.remaining()) { "Некорректная длина данных GLB" }

        validateScene(json)
        val geometryViews = validateAccessors(json, views, binary, declaredSize)
        stripExtras(json)
        val images = json.optJSONArray("images") ?: JSONArray()
        val textureViews = mutableMapOf<Int, String>()
        require(images.length() <= 32) { "Слишком много текстур GLB" }
        for (index in 0 until images.length()) {
            val image = images.getJSONObject(index)
            require(!image.has("uri")) { "Поддерживаются только текстуры внутри бинарного блока GLB" }
            val view = image.getInt("bufferView")
            require(view in 0 until views.length() && view !in geometryViews) { "Некорректная текстура GLB" }
            val mime = image.getString("mimeType")
            require(mime == "image/png" || mime == "image/jpeg") { "Формат текстуры не поддерживается" }
            val previous = textureViews.put(view, mime)
            require(previous == null || previous == mime) { "Несовместимые форматы текстур GLB" }
        }

        val binaryFile = File.createTempFile("bin-", ".tmp", output.parentFile)
        var texturePixels = 0L
        try {
            val changedImages = mutableSetOf<Int>()
            binaryFile.outputStream().buffered().use { stream ->
                val packed = BoundedOutput(stream)
                for (index in 0 until views.length()) {
                    checkActive()
                    val view = views.getJSONObject(index)
                    val offset = view.optInt("byteOffset", 0)
                    val length = view.getInt("byteLength")
                    require(view.getInt("buffer") == 0 && length > 0 && offset >= 0 &&
                        offset.toLong() + length <= declaredSize) { "Некорректный блок данных GLB" }
                    val bytes = binary.duplicate().apply { position(offset); limit(offset + length) }.slice()
                    val newOffset = packed.size
                    val mime = textureViews[index]
                    if (mime == null) {
                        packed.copyFrom(bytes)
                    } else {
                        val image = ByteArray(length).also { bytes.get(it) }
                        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(image, 0, image.size, size)
                        require(size.outMimeType == mime && size.outWidth in 1..16_384 &&
                            size.outHeight in 1..16_384) { "Повреждённая или слишком большая текстура GLB" }
                        var sample = 1
                        while ((maxOf(size.outWidth, size.outHeight) + sample - 1) / sample > textureEdge) sample *= 2
                        val width = (size.outWidth + sample - 1) / sample
                        val height = (size.outHeight + sample - 1) / sample
                        texturePixels += width.toLong() * height
                        require(texturePixels <= MAX_TEXTURE_PIXELS) { "Текстуры GLB превышают лимит памяти" }
                        if (sample == 1) {
                            packed.write(image)
                        } else {
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPremultiplied = false
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size, options)
                                ?: error("Не удалось прочитать текстуру GLB")
                            try {
                                checkActive()
                                require(bitmap.width <= textureEdge && bitmap.height <= textureEdge)
                                // PNG preserves transparency and avoids lossy compression of material maps.
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, packed)) {
                                    "Не удалось подготовить текстуру"
                                }
                                changedImages += index
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    view.put("byteOffset", newOffset)
                    view.put("byteLength", packed.size - newOffset)
                    repeat(padding(packed.size)) { packed.write(0) }
                }
            }
            for (index in 0 until images.length()) {
                val image = images.getJSONObject(index)
                if (image.getInt("bufferView") in changedImages) image.put("mimeType", "image/png")
            }
            buffer.put("byteLength", binaryFile.length())
            write(output, json.toString(), binaryFile.length().toInt()) { stream ->
                binaryFile.inputStream().buffered().use { input ->
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        checkActive()
                        val count = input.read(chunk)
                        if (count < 0) break
                        stream.write(chunk, 0, count)
                    }
                }
            }
        } finally {
            binaryFile.delete()
        }
    }

    fun writeStl(output: File, mesh: StlMesh) {
        val min = mesh.min.joinToString(",")
        val max = mesh.max.joinToString(",")
        val count = mesh.triangleCount * 3
        val json = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"material":0}]}],
            "materials":[{"doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[0.59,0.66,0.75,1],
            "metallicFactor":0,"roughnessFactor":0.8}}],
            "buffers":[{"byteLength":${mesh.byteCount}}],
            "bufferViews":[{"buffer":0,"byteLength":${mesh.byteCount},"byteStride":24,"target":34962}],
            "accessors":[{"bufferView":0,"componentType":5126,"type":"VEC3","count":$count,"min":[$min],"max":[$max]},
            {"bufferView":0,"byteOffset":12,"componentType":5126,"type":"VEC3","count":$count}]}
        """.trimIndent()
        write(output, json, mesh.byteCount, mesh::writeVertices)
    }

    private fun parse(source: ByteBuffer): Pair<JSONObject, ByteBuffer> {
        val data = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val size = data.remaining()
        require(size in 20..MAX_MODEL_FILE_BYTES) { "Недопустимый размер GLB" }
        val prefix = ByteArray(20).also { data.get(it) }
        require(GlbHeader.isValidPrefix(prefix, size.toLong())) { "Поддерживается только целый файл GLB 2.0" }
        val jsonLength = data.getInt(12)
        require(jsonLength <= MAX_JSON_BYTES) { "Слишком сложное описание GLB" }
        val jsonText = ByteArray(jsonLength).also { data.get(it) }.toString(Charsets.UTF_8).trim()
        validateJsonDepth(jsonText)
        val json = JSONObject(jsonText)
        require(json.getJSONObject("asset").getString("version") == "2.0") { "Поддерживается только GLB 2.0" }
        require(data.remaining() >= 8) { "GLB не содержит бинарных данных" }
        val binaryLength = data.int
        require(data.int == BIN_CHUNK && binaryLength == data.remaining() && binaryLength % 4 == 0) {
            "Повреждённый бинарный блок GLB"
        }
        return json to data.slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun validateJsonDepth(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else {
                when (character) {
                    '"' -> quoted = true
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                }
                require(depth in 0..48) { "Слишком сложное описание GLB" }
            }
        }
        require(!quoted && depth == 0) { "Повреждённое описание GLB" }
    }

    private fun validateScene(json: JSONObject) {
        val used = json.optJSONArray("extensionsUsed") ?: JSONArray()
        val required = json.optJSONArray("extensionsRequired") ?: JSONArray()
        for (index in 0 until used.length()) {
            val extension = used.getString(index)
            require(extension !in unsupportedExtensions) { "Расширение GLB не поддерживается: $extension" }
        }
        for (index in 0 until required.length()) {
            val extension = required.getString(index)
            require(extension in supportedRequiredExtensions) { "Расширение GLB не поддерживается: $extension" }
        }
        val scenes = json.getJSONArray("scenes")
        val scene = scenes.getJSONObject(json.optInt("scene", 0))
        val nodes = json.getJSONArray("nodes")
        val meshCount = json.getJSONArray("meshes").length()
        require(nodes.length() in 1..2048 && meshCount in 1..2048) { "Слишком сложная сцена GLB" }

        val visited = BooleanArray(nodes.length())
        fun visit(index: Int, depth: Int) {
            require(index in visited.indices && !visited[index] && depth <= 64) { "Некорректная иерархия GLB" }
            visited[index] = true
            val node = nodes.getJSONObject(index)
            val children = node.optJSONArray("children") ?: JSONArray()
            for (child in 0 until children.length()) visit(children.getInt(child), depth + 1)
        }
        val roots = scene.getJSONArray("nodes")
        for (root in 0 until roots.length()) visit(roots.getInt(root), 0)
        // Filament otherwise includes all scenes in its aggregate world-space bounds.
        json.put("scenes", JSONArray().put(scene))
        json.put("scene", 0)
    }

    private fun stripExtras(value: Any) {
        when (value) {
            is JSONObject -> {
                value.remove("extras")
                value.keys().forEach { stripExtras(value.get(it)) }
            }
            is JSONArray -> for (index in 0 until value.length()) stripExtras(value.get(index))
        }
    }

    private fun validateAccessors(json: JSONObject, views: JSONArray, binary: ByteBuffer, size: Int): Set<Int> {
        val accessors = json.getJSONArray("accessors")
        require(accessors.length() in 1..4096) { "Слишком много атрибутов GLB" }
        val geometryViews = mutableSetOf<Int>()
        var count = 0L
        fun checkView(index: Int, offset: Int, count: Int, elementSize: Int, strided: Boolean = false) {
            require(index in 0 until views.length() && offset >= 0) { "Некорректный атрибут GLB" }
            val view = views.getJSONObject(index)
            val stride = if (strided) view.optInt("byteStride", elementSize) else elementSize
            require(stride in elementSize..252 && offset.toLong() + (count - 1L) * stride + elementSize <=
                view.getInt("byteLength")) { "Атрибут выходит за пределы блока GLB" }
            geometryViews += index
        }
        for (index in 0 until accessors.length()) {
            val accessor = accessors.getJSONObject(index)
            val elements = accessor.getInt("count")
            count += elements
            require(elements in 1..2_000_000 && count <= 6_000_000) { "Геометрия GLB превышает лимит" }
            val component = when (accessor.getInt("componentType")) {
                5120, 5121 -> 1
                5122, 5123 -> 2
                5125, 5126 -> 4
                else -> error("Тип атрибута GLB не поддерживается")
            }
            val elementSize = when (accessor.getString("type")) {
                "SCALAR" -> component
                "VEC2" -> component * 2
                "VEC3" -> component * 3
                "VEC4" -> component * 4
                "MAT2" -> 2 * (component * 2 + padding(component * 2))
                "MAT3" -> 3 * (component * 3 + padding(component * 3))
                "MAT4" -> component * 16
                else -> error("Тип атрибута GLB не поддерживается")
            }
            if (accessor.has("bufferView")) {
                checkView(accessor.getInt("bufferView"), accessor.optInt("byteOffset"), elements, elementSize, strided = true)
            }
            accessor.optJSONObject("sparse")?.let { sparse ->
                val sparseCount = sparse.getInt("count")
                require(sparseCount in 1..elements) { "Некорректный разреженный атрибут GLB" }
                val indices = sparse.getJSONObject("indices")
                val indexSize = when (indices.getInt("componentType")) {
                    5121 -> 1
                    5123 -> 2
                    5125 -> 4
                    else -> error("Некорректный индекс GLB")
                }
                val values = sparse.getJSONObject("values")
                val indexView = indices.getInt("bufferView")
                val byteOffset = indices.optInt("byteOffset")
                checkView(indexView, byteOffset, sparseCount, indexSize)
                checkView(values.getInt("bufferView"), values.optInt("byteOffset"), sparseCount, elementSize)
                val viewOffset = views.getJSONObject(indexView).optInt("byteOffset")
                val start = viewOffset.toLong() + byteOffset
                require(viewOffset >= 0 && start + sparseCount.toLong() * indexSize <= size)
                var previous = -1
                repeat(sparseCount) { index ->
                    val offset = start.toInt() + index * indexSize
                    val value = when (indexSize) {
                        1 -> binary.get(offset).toInt() and 0xff
                        2 -> binary.getShort(offset).toInt() and 0xffff
                        else -> binary.getInt(offset)
                    }
                    require(value > previous && value < elements) { "Некорректный индекс GLB" }
                    previous = value
                }
            }
        }
        return geometryViews
    }

    private fun write(destination: File, json: String, bytes: Int, binary: (OutputStream) -> Unit) {
        val text = json.toByteArray(Charsets.UTF_8)
        val jsonPadding = padding(text.size)
        val binPadding = padding(bytes)
        val total = 28L + text.size + jsonPadding + bytes + binPadding
        require(total <= MAX_MODEL_FILE_BYTES) { "Подготовленная модель превышает лимит" }
        val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46546C67).putInt(2).putInt(total.toInt()).putInt(text.size + jsonPadding).putInt(JSON_CHUNK)
        val binHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(bytes + binPadding).putInt(BIN_CHUNK)
        destination.outputStream().buffered().use { output ->
            output.write(header.array())
            output.write(text)
            repeat(jsonPadding) { output.write(0x20) }
            output.write(binHeader.array())
            binary(output)
            repeat(binPadding) { output.write(0) }
        }
    }

    private fun padding(length: Int): Int = (4 - length % 4) % 4

    private class BoundedOutput(out: OutputStream) : FilterOutputStream(out) {
        var size = 0
            private set

        private fun reserve(bytes: Int) {
            require(size.toLong() + bytes <= MAX_MODEL_FILE_BYTES) { "Подготовленная модель превышает лимит" }
            size += bytes
        }

        override fun write(value: Int) {
            reserve(1)
            out.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            reserve(length)
            out.write(bytes, offset, length)
        }

        fun copyFrom(source: ByteBuffer) {
            val buffer = ByteArray(minOf(64 * 1024, source.remaining()))
            while (source.hasRemaining()) {
                val length = minOf(source.remaining(), buffer.size)
                source.get(buffer, 0, length)
                write(buffer, 0, length)
            }
        }
    }
}

internal fun File.mapModel(): ByteBuffer = inputStream().use { input ->
    val size = input.channel.size()
    require(size in 1..MAX_MODEL_FILE_BYTES.toLong()) { "Допустимый размер модели — до 48 МиБ" }
    input.channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
}

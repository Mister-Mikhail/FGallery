package com.mistermikhail.fgallery.data

import java.util.Locale

internal enum class ModelFormat(val extension: String, val mimeType: String) {
    GLB("glb", "model/gltf-binary"),
    STL("stl", "model/stl");

    companion object {
        private val stlMimeTypes = setOf(STL.mimeType, "application/sla", "application/x-stl")
        val mimeTypes = arrayOf(GLB.mimeType, *stlMimeTypes.toTypedArray())

        fun from(name: String, mime: String?): ModelFormat? {
            val extension = name.trim().substringAfterLast('.', "").lowercase(Locale.ROOT)
            entries.firstOrNull { it.extension == extension }?.let { return it }
            return when (mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
                GLB.mimeType -> GLB
                in stlMimeTypes -> STL
                else -> null
            }
        }
    }
}

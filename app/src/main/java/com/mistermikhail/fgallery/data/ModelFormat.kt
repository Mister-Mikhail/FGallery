package com.mistermikhail.fgallery.data

import java.util.Locale

internal enum class ModelFormat(val extension: String, val mimeType: String) {
    GLB("glb", "model/gltf-binary"),
    STL("stl", "model/stl");

    companion object {
        private val glbMimeTypes = setOf(GLB.mimeType, "application/x-glb")
        private val stlMimeTypes = setOf(STL.mimeType, "application/sla", "application/x-stl",
            "model/x.stl-ascii", "model/x.stl-binary")
        val mimeTypes = (glbMimeTypes + stlMimeTypes).toTypedArray()

        fun from(name: String, mime: String?): ModelFormat? {
            val extension = name.trim().substringAfterLast('.', "").lowercase(Locale.ROOT)
            entries.firstOrNull { it.extension == extension }?.let { return it }
            return when (mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
                in glbMimeTypes -> GLB
                in stlMimeTypes -> STL
                else -> null
            }
        }
    }
}

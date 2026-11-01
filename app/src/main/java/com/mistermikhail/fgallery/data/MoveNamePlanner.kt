package com.mistermikhail.fgallery.data

import java.util.Locale

/**
 * Reserve destination names as one batch, ignoring case.
 * Matching RAW+JPEG stems share a numeric suffix; no source is mutated here.
 */
internal object MoveNamePlanner {
    private fun key(value: String): String = value.lowercase(Locale.ROOT)
    private fun stem(value: String): String = key(value.substringBeforeLast('.', value))

    private fun suffixed(name: String, suffix: Int): String {
        if (suffix == 1) return name
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)}_$suffix${name.substring(dot)}"
        else "${name}_$suffix"
    }

    fun plan(sourceNames: List<String>, existingNames: Collection<String>): List<String> {
        require(sourceNames.all { it.isNotBlank() && '/' !in it && '\\' !in it }) {
            "Недопустимое имя файла"
        }
        val reserved = existingNames.mapTo(mutableSetOf(), ::key)
        val output = MutableList(sourceNames.size) { "" }
        val grouped = sourceNames.indices.groupBy { stem(sourceNames[it]) }
        for (indices in grouped.values) {
            // Pair extensions for the same photographed frame, but preserve every
            // source independently if a batch contains duplicate filenames.
            val groups = mutableListOf<MutableList<Int>>()
            for (index in indices) {
                val group = groups.firstOrNull { g ->
                    g.none { key(sourceNames[it]) == key(sourceNames[index]) }
                } ?: mutableListOf<Int>().also(groups::add)
                group += index
            }
            for (group in groups) {
                var suffix = 1
                while (true) {
                    val candidates = group.map { suffixed(sourceNames[it], suffix) }
                    if (candidates.none { key(it) in reserved }) {
                        group.forEachIndexed { ordinal, index -> output[index] = candidates[ordinal] }
                        reserved += candidates.map(::key)
                        break
                    }
                    suffix = if (suffix == 1) 2 else suffix + 1
                    check(suffix <= 100_000) { "Невозможно подобрать свободное имя" }
                }
            }
        }
        return output
    }
}

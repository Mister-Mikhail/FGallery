package com.mistermikhail.fgallery.data

/**
 * A 64-bit difference hash of an already decoded 9x8 RGB thumbnail.
 * Hashes are only a cheap shortlist for potential resizes; they do not prove
 * image equality (a color edit can have an identical luminance dHash).
 * No original image, especially a camera RAW, is decoded by this helper.
 */
internal object VisualDuplicateFingerprints {
    const val SAMPLE_WIDTH = 9
    const val SAMPLE_HEIGHT = 8
    const val SAMPLE_PIXELS = SAMPLE_WIDTH * SAMPLE_HEIGHT

    fun differenceHash(argb: IntArray): String {
        require(argb.size == SAMPLE_PIXELS) { "Expected a 9x8 RGB sample" }
        var bits = 0L
        for (y in 0 until SAMPLE_HEIGHT) {
            for (x in 0 until SAMPLE_WIDTH - 1) {
                val left = luminance(argb[y * SAMPLE_WIDTH + x])
                val right = luminance(argb[y * SAMPLE_WIDTH + x + 1])
                bits = (bits shl 1) or (if (left > right) 1L else 0L)
            }
        }
        return java.lang.Long.toUnsignedString(bits, 16).padStart(16, '0')
    }

    fun hammingDistance(first: String, second: String): Int? {
        if (first.length != 16 || second.length != 16 ||
            !first.all(::isHexDigit) || !second.all(::isHexDigit)) return null
        return java.lang.Long.bitCount(
            java.lang.Long.parseUnsignedLong(first, 16) xor
                java.lang.Long.parseUnsignedLong(second, 16)
        )
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' ||
        c in 'a'..'f' || c in 'A'..'F'

    private fun luminance(argb: Int): Int {
        val red = (argb ushr 16) and 0xff
        val green = (argb ushr 8) and 0xff
        val blue = argb and 0xff
        return red * 299 + green * 587 + blue * 114
    }
}

/**
 * This predicate is deliberately named *candidate*. Do not expose its result
 * as a duplicate, and never delete based on it. Stage 6 still requires a
 * pixel-level verifier that rejects color grading, retouching and burst shots.
 * Crops need their own alignment search and are NOT matched by this method.
 */
internal fun potentialResizedDuplicate(a: CatalogMediaRow, b: CatalogMediaRow): Boolean {
    if (a.uri == b.uri || a.recycled || b.recycled ||
        a.kind != "IMAGE" || b.kind != "IMAGE" ||
        a.width < 64 || b.width < 64 || a.height < 64 || b.height < 64 ||
        a.name.endsWith(".gif", true) || b.name.endsWith(".gif", true) ||
        isCameraRawImage(a.name) || isCameraRawImage(b.name)) return false
    if (a.dateTakenMillis > 0L && b.dateTakenMillis > 0L &&
        kotlin.math.abs(a.dateTakenMillis - b.dateTakenMillis) > 2_000L) return false
    if (!a.exifCamera.isNullOrBlank() && !b.exifCamera.isNullOrBlank() &&
        !a.exifCamera.equals(b.exifCamera, ignoreCase = true)) return false
    // Use integer products, not rounded float aspect ratios.
    val lhs = a.width.toLong() * b.height
    val rhs = b.width.toLong() * a.height
    if (kotlin.math.abs(lhs - rhs) > maxOf(lhs, rhs) * 0.015) return false
    val first = a.perceptualHash ?: return false
    val second = b.perceptualHash ?: return false
    return (VisualDuplicateFingerprints.hammingDistance(first, second) ?: return false) <= 5
}

/** Camera RAW + JPEG from one shutter press is a pair, not a visual duplicate. */
internal fun isCameraRawImage(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in setOf(
        "dng", "cr2", "cr3", "nef", "nrw", "arw", "sr2", "srf",
        "orf", "rw2", "raf", "pef", "raw", "3fr", "fff", "iiq",
    )


/**
 * Conservative crop discovery without relying on dHash: cropping changes the
 * thumbnail gradients and aspect ratio. A shared camera filename stem and
 * capture timestamp shortlist candidates; only source-pixel verification can
 * confirm a crop. Never use this predicate to authorize trash.
 */
internal fun cropCandidateStem(name: String): String {
    val stem = name.substringBeforeLast('.').lowercase(java.util.Locale.ROOT)
    // Normalize terminal editor crop suffixes, including numbered variants.
    // Internal words and differently numbered camera frames remain distinct.
    return stem.replace(Regex("[-_](?:crop|cropped)(?:[-_]\\d+)?$"), "")
}

internal fun potentialCropDuplicate(a: CatalogMediaRow, b: CatalogMediaRow): Boolean {
    if (a.uri == b.uri || a.recycled || b.recycled ||
        a.kind != "IMAGE" || b.kind != "IMAGE" ||
        a.width < 64 || b.width < 64 || a.height < 64 || b.height < 64 ||
        a.name.endsWith(".gif", true) || b.name.endsWith(".gif", true) ||
        isCameraRawImage(a.name) || isCameraRawImage(b.name)) return false
    if (a.dateTakenMillis > 0L && b.dateTakenMillis > 0L &&
        kotlin.math.abs(a.dateTakenMillis - b.dateTakenMillis) > 2_000L) return false
    if (!a.exifCamera.isNullOrBlank() && !b.exifCamera.isNullOrBlank() &&
        !a.exifCamera.equals(b.exifCamera, ignoreCase = true)) return false
    val sharedStem = cropCandidateStem(a.name)
    if (sharedStem.length < 5 || sharedStem != cropCandidateStem(b.name)) return false
    val large = if (a.width.toLong() * a.height >= b.width.toLong() * b.height) a else b
    val small = if (large === a) b else a
    val largeArea = large.width.toLong() * large.height
    val smallArea = small.width.toLong() * small.height
    return large.width >= small.width && large.height >= small.height &&
        largeArea > smallArea && smallArea * 4 >= largeArea
}

internal fun potentialVisualDuplicate(a: CatalogMediaRow, b: CatalogMediaRow): Boolean =
    potentialResizedDuplicate(a, b) || potentialCropDuplicate(a, b)

/** Bounded review window. A page only compares items inside that window. */
internal fun visualCandidatePageCount(itemCount: Int, pageSize: Int = 250): Int {
    require(pageSize > 0)
    return maxOf(1, itemCount / pageSize + if (itemCount % pageSize == 0) 0 else 1)
}

internal fun <T> visualCandidatePage(items: List<T>, page: Int, pageSize: Int = 250): List<T> {
    require(pageSize > 0)
    if (page < 0 || page >= visualCandidatePageCount(items.size, pageSize)) return emptyList()
    return items.drop(page * pageSize).take(pageSize)
}

/**
 * Candidate discovery across page boundaries. Each displayed page owns its
 * earlier-indexed anchors, so a pair appears only once in the review.
 *
 * Six disjoint dHash slices provide a lossless shortlist for Hamming <= 5:
 * at least one slice must be identical. Filename stems independently
 * shortlist crops (which cannot rely on dHash).
 *
 * The indexes are metadata-only. Source pixels must still be verified by the
 * existing read-only verifier; no result from here authorizes deletion.
 */
internal fun visualCandidatesAcrossPages(
    items: List<CatalogMediaRow>,
    page: Int,
    pageSize: Int = 250,
    maxPairs: Int = 10,
): List<Pair<String, String>> {
    require(pageSize > 0 && maxPairs > 0)
    if (page < 0 || page >= visualCandidatePageCount(items.size, pageSize)) return emptyList()
    val result = mutableListOf<Pair<String, String>>()
    val start = page * pageSize
    visitVisualMatches(items, start until minOf(items.size.toLong(), start.toLong() + pageSize).toInt()) { i, j ->
        result.add(items[i].uri to items[j].uri)
        result.size < maxPairs
    }
    return result
}

/** Group connected matches across the catalogue without storing every pair in a burst. */
internal fun visualReviewGroups(rows: List<CatalogMediaRow>, checkActive: () -> Unit = {}): List<List<String>> {
    val items = rows.distinctBy { it.uri }
    val parent = IntArray(items.size) { it }
    fun root(index: Int): Int {
        var node = index
        while (parent[node] != node) {
            parent[node] = parent[parent[node]]
            node = parent[node]
        }
        return node
    }
    visitVisualMatches(items, items.indices, checkActive, { i, j -> root(i) != root(j) }) { i, j ->
        parent[root(j)] = root(i)
        true
    }
    return items.indices.groupBy(::root).values.filter { it.size > 1 }.map { group ->
        group.map { items[it] }.sortedWith(compareBy<CatalogMediaRow> { it.relativePath }
            .thenBy { it.name }.thenBy { it.uri }).map { it.uri }
    }.sortedWith(compareBy<List<String>> { it.first() })
}

private fun visitVisualMatches(
    items: List<CatalogMediaRow>,
    anchors: IntRange,
    checkActive: () -> Unit = {},
    needsComparison: (Int, Int) -> Boolean = { _, _ -> true },
    onMatch: (Int, Int) -> Boolean,
) {
    val hashSlices = HashMap<String, MutableList<Int>>()
    val cropStems = HashMap<String, MutableList<Int>>()
    fun hashKeys(hash: String?): List<String> {
        if (hash == null || hash.length != 16 || !hash.all {
                it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
            }) return emptyList()
        val normalized = hash.lowercase(java.util.Locale.ROOT)
        return (0 until 6).map { part ->
            val start = part * 16 / 6
            val end = (part + 1) * 16 / 6
            "$part:${normalized.substring(start, end)}"
        }
    }
    items.forEachIndexed { index, row ->
        if (row.kind != "IMAGE" || row.recycled || isCameraRawImage(row.name)) return@forEachIndexed
        val stem = cropCandidateStem(row.name)
        if (stem.length >= 5) cropStems.getOrPut(stem) { mutableListOf() }.add(index)
        hashKeys(row.perceptualHash).forEach { key ->
            hashSlices.getOrPut(key) { mutableListOf() }.add(index)
        }
    }
    for (i in anchors) {
        checkActive()
        val first = items[i]
        if (first.kind != "IMAGE" || first.recycled || isCameraRawImage(first.name)) continue
        val candidates = HashSet<Int>()
        cropStems[cropCandidateStem(first.name)]?.let(candidates::addAll)
        hashKeys(first.perceptualHash).forEach { key ->
            hashSlices[key]?.let(candidates::addAll)
        }
        for (j in candidates.sorted()) {
            if (j <= i || !needsComparison(i, j)) continue
            val second = items[j]
            // Copies already identified by full SHA-256 belong to the exact tab.
            val digest = first.fullHash
            if (digest?.length == 64 && first.sizeBytes == second.sizeBytes &&
                digest.equals(second.fullHash, ignoreCase = true)
            ) continue
            if (potentialVisualDuplicate(first, second)) {
                if (!onMatch(i, j)) return
            }
        }
    }
}

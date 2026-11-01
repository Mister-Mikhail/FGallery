package com.mistermikhail.fgallery.data

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Path.Companion.toPath

/** One Coil pipeline for static gallery media using v4 thumbnail tiers.
 * Both foreground cells and background warm-up call ThumbnailCache.ensure.
 * Animated GIF and muted live video remain separate visual layers.
 */
internal data class GalleryThumb(val media: MediaItem, val highQuality: Boolean)

internal class GalleryThumbFetcher(
    private val context: Context,
    private val model: GalleryThumb,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val file = ThumbnailCache.ensure(context, model.media, model.highQuality)
            ?: return null
        return SourceFetchResult(
            source = ImageSource(file = file.absolutePath.toPath(), fileSystem = options.fileSystem),
            mimeType = if (file.extension.equals("png", true)) "image/png" else "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<GalleryThumb> {
        override fun create(
            data: GalleryThumb, options: Options, imageLoader: ImageLoader,
        ): Fetcher = GalleryThumbFetcher(options.context, data, options)
    }
}

internal class GalleryThumbKeyer : Keyer<GalleryThumb> {
    override fun key(data: GalleryThumb, options: Options): String =
        "gallery/v4/${ThumbnailCache.key(data.media)}/${if (data.highQuality) "hq" else "micro"}"
}

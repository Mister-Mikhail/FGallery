package com.mistermikhail.fgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Small on-demand map centred at the capture point. Only tile URLs leave the device. */
internal object CaptureMapPreview {
    const val WIDTH = 320
    const val HEIGHT = 144
    private const val ZOOM = 14
    private const val TILE_SIZE = 256
    private const val MAX_TILE_BYTES = 512 * 1024
    private const val CACHE_BYTES = 16 * 1024 * 1024
    private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
    private val requests = Semaphore(2)

    suspend fun load(context: Context, coordinates: ExifGpsCoordinates): Bitmap? = withContext(Dispatchers.IO) {
        if (kotlin.math.abs(coordinates.latitude) > 85.05112878) return@withContext null
        val count = 1 shl ZOOM
        val world = count * TILE_SIZE
        val lat = coordinates.latitude * PI / 180
        val left = (coordinates.longitude + 180) / 360 * world - WIDTH / 2
        val top = (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2 * world - HEIGHT / 2
        if (top < 0 || top + HEIGHT >= world) return@withContext null
        val directory = File(context.cacheDir, "capture_map").apply { mkdirs() }
        val tiles = coroutineScope {
            (tileOf(top)..tileOf(top + HEIGHT - 1)).flatMap { y ->
                (tileOf(left)..tileOf(left + WIDTH - 1)).map { x ->
                    async {
                        val tile = requests.withPermit { loadTile(directory, Math.floorMod(x, count), y) }
                        Triple(x, y, tile)
                    }
                }
            }.awaitAll()
        }
        trimCache(directory)
        if (tiles.any { it.third == null }) return@withContext null
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { preview ->
            val canvas = Canvas(preview)
            for ((x, y, image) in tiles) {
                canvas.drawBitmap(checkNotNull(image), (x * TILE_SIZE - left).toFloat(),
                    (y * TILE_SIZE - top).toFloat(), null)
            }
        }
    }

    private fun tileOf(pixel: Double): Int = floor(pixel / TILE_SIZE).toInt()

    private fun loadTile(directory: File, x: Int, y: Int): Bitmap? {
        val tile = File(directory, "$ZOOM-$x-$y.png")
        val metadata = File(directory, "${tile.name}.headers")
        val cached = BitmapFactory.decodeFile(tile.path)
        val headers = Properties()
        runCatching { metadata.inputStream().use(headers::load) }
        val now = System.currentTimeMillis()
        if (cached != null && tile.lastModified() > now) return cached
        val fresh = runCatching {
            val connection = URL("https://tile.openstreetmap.org/$ZOOM/$x/$y.png").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 3_000
                connection.readTimeout = 4_000
                connection.setRequestProperty("User-Agent", "FGallery/0.4 (+https://github.com/Mister-Mikhail/FGallery)")
                if (cached != null) {
                    headers.getProperty("ETag")?.let { connection.setRequestProperty("If-None-Match", it) }
                    headers.getProperty("Last-Modified")?.let { connection.setRequestProperty("If-Modified-Since", it) }
                }
                val response = connection.responseCode
                if (response == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    tile.setLastModified(expiresAt(connection, now))
                    return@runCatching cached
                }
                if (response != HttpURLConnection.HTTP_OK) return@runCatching null
                val bytes = connection.inputStream.use { input ->
                    val buffer = ByteArray(MAX_TILE_BYTES + 1)
                    var length = 0
                    while (length < buffer.size) {
                        val read = input.read(buffer, length, buffer.size - length)
                        if (read < 0) break
                        length += read
                    }
                    if (length > MAX_TILE_BYTES) return@runCatching null
                    buffer.copyOf(length)
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                if (decoded.width != TILE_SIZE || decoded.height != TILE_SIZE) return@runCatching null
                if (!connection.getHeaderField("Cache-Control").orEmpty().contains("no-store", ignoreCase = true)) {
                    val temporary = File.createTempFile("tile-", ".tmp", directory)
                    try {
                        temporary.writeBytes(bytes)
                        Files.move(temporary.toPath(), tile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        tile.setLastModified(expiresAt(connection, now))
                        val validators = Properties()
                        listOf("ETag", "Last-Modified").forEach { header ->
                            connection.getHeaderField(header)?.let { validators.setProperty(header, it) }
                        }
                        metadata.outputStream().use { validators.store(it, null) }
                    } finally {
                        temporary.delete()
                    }
                }
                decoded
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        return fresh ?: cached
    }

    private fun expiresAt(connection: HttpURLConnection, now: Long): Long {
        val policy = connection.getHeaderField("Cache-Control").orEmpty()
        if (policy.contains("no-cache", ignoreCase = true)) return now
        val maxAge = Regex("""(?:^|,)\s*max-age=(\d+)""", RegexOption.IGNORE_CASE)
            .find(policy)?.groupValues?.get(1)?.toLongOrNull()
        if (maxAge != null) return now + maxAge.coerceAtMost(365 * 24 * 60 * 60) * 1000
        return connection.getHeaderFieldDate("Expires", 0).takeIf { it > 0 } ?: (now + WEEK_MS)
    }

    private fun trimCache(directory: File) {
        var bytes = 0L
        directory.listFiles { file -> file.extension == "png" }.orEmpty().sortedByDescending(File::lastModified)
            .forEach { file ->
                bytes += file.length()
                if (bytes > CACHE_BYTES) {
                    file.delete()
                    File(directory, "${file.name}.headers").delete()
                }
            }
    }
}

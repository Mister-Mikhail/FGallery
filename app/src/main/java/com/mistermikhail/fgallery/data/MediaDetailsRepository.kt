package com.mistermikhail.fgallery.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MediaDetailsRepository(
    private val context: Context,
) {
    suspend fun load(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val sections = mutableListOf<DetailSection>()

        sections += DetailSection(
            title = "Файл",
            fields = buildList {
                add(DetailField("Имя", item.name))
                add(DetailField("Тип файла", fileExtension(item.name, item.mimeType)))
                if (item.relativePath.isNotBlank()) {
                    add(DetailField("Папка", item.relativePath.trimEnd('/')))
                }
                if (item.sizeBytes > 0L) {
                    add(DetailField("Размер", formatBytes(item.sizeBytes)))
                }
                if (item.width > 0 && item.height > 0) {
                    add(DetailField("Разрешение", "${item.width} × ${item.height}"))
                }
                item.mimeType?.takeIf { it.isNotBlank() }?.let {
                    add(DetailField("Формат", it))
                }
                if (item.dateTakenMillis > 0L) {
                    add(DetailField("Дата", formatDate(item.dateTakenMillis)))
                }
                if (item.kind == MediaKind.VIDEO && item.durationMillis > 0L) {
                    add(DetailField("Длительность", formatDuration(item.durationMillis)))
                }
            },
        )

        when (item.kind) {
            MediaKind.VIDEO -> loadVideoDetails(item)?.let(sections::add)
            MediaKind.IMAGE,
            MediaKind.RAW,
            -> loadExifDetails(item)?.let(sections::add)
        }

        MediaDetails(sections = sections)
    }

    private fun loadExifDetails(item: MediaItem): DetailSection? {
        val exif = runCatching {
            context.contentResolver.openInputStream(item.uri)?.use(::ExifInterface)
        }.getOrNull() ?: return null

        val fields = buildList {
            exif.attribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
                add(DetailField("Дата съёмки", it))
            }

            val make = exif.attribute(ExifInterface.TAG_MAKE)
            val model = exif.attribute(ExifInterface.TAG_MODEL)
            listOfNotNull(make, model)
                .joinToString(" ")
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { add(DetailField("Камера", it)) }

            val lens = listOfNotNull(
                exif.attribute(ExifInterface.TAG_LENS_MAKE),
                exif.attribute(ExifInterface.TAG_LENS_MODEL),
            )
                .joinToString(" ")
                .trim()
            if (lens.isNotBlank()) {
                add(DetailField("Объектив", lens))
            }

            exif.doubleAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let {
                add(DetailField("Фокусное расстояние", formatDecimal(it, " мм")))
            }

            exif.doubleAttribute(ExifInterface.TAG_F_NUMBER)?.let {
                add(DetailField("Диафрагма", "f/${formatDecimal(it)}"))
            }

            exif.doubleAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let {
                add(DetailField("Выдержка", formatExposure(it)))
            }

            exif.attribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let {
                add(DetailField("ISO", it))
            }

            exif.doubleAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let {
                add(DetailField("Экспокоррекция", "${formatSigned(it)} EV"))
            }

            val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            if (flash >= 0) {
                add(DetailField("Вспышка", if ((flash and 0x1) == 1) "Сработала" else "Не сработала"))
            }

            val whiteBalance = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
            if (whiteBalance >= 0) {
                add(
                    DetailField(
                        "Баланс белого",
                        if (whiteBalance == ExifInterface.WHITE_BALANCE_AUTO.toInt()) "Авто" else "Ручной",
                    )
                )
            }

            exif.latLong?.let { location ->
                add(
                    DetailField(
                        "GPS",
                        String.format(Locale.ROOT, "%.6f, %.6f", location[0], location[1]),
                    )
                )
            }

            exif.attribute(ExifInterface.TAG_SOFTWARE)?.let {
                add(DetailField("ПО", it))
            }
        }

        return fields.takeIf { it.isNotEmpty() }?.let {
            DetailSection(
                title = if (item.kind == MediaKind.RAW) "EXIF / RAW" else "EXIF",
                fields = it,
            )
        }
    }

    private fun loadVideoDetails(item: MediaItem): DetailSection? {
        val retriever = MediaMetadataRetriever()

        val fields = runCatching {
            retriever.setDataSource(context, item.uri)

            buildList {
                retriever.value(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let {
                    add(DetailField("Ширина", "$it px"))
                }
                retriever.value(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let {
                    add(DetailField("Высота", "$it px"))
                }
                retriever.value(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let {
                    add(DetailField("Поворот", "$it°"))
                }
                retriever.value(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                    add(DetailField("Битрейт", formatBitrate(it)))
                }
                retriever.value(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let {
                    add(DetailField("Частота кадров", "$it fps"))
                }
                retriever.value(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let {
                    add(DetailField("MIME", it))
                }
                retriever.value(MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                    add(DetailField("Дата файла", it))
                }
            }
        }.getOrDefault(emptyList())

        retriever.release()

        return fields.takeIf { it.isNotEmpty() }?.let {
            DetailSection("Видео", it)
        }
    }

    private fun ExifInterface.attribute(tag: String): String? =
        getAttribute(tag)?.trim()?.takeIf { it.isNotBlank() }

    private fun ExifInterface.doubleAttribute(tag: String): Double? {
        val value = getAttributeDouble(tag, Double.NaN)
        return value.takeUnless { it.isNaN() || it.isInfinite() }
    }

    private fun MediaMetadataRetriever.value(key: Int): String? =
        extractMetadata(key)?.trim()?.takeIf { it.isNotBlank() }

    private fun fileExtension(name: String, mimeType: String?): String {
        val extension = name.substringAfterLast('.', "").trim().uppercase(Locale.ROOT)
        if (extension.isNotBlank()) return extension
        return mimeType?.substringAfterLast('/')?.uppercase(Locale.ROOT) ?: "Неизвестно"
    }

    private fun formatDate(value: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(value))

    private fun formatDuration(value: Long): String {
        val totalSeconds = value / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
    }

    private fun formatBitrate(value: Long): String =
        if (value >= 1_000_000L) {
            String.format(Locale.ROOT, "%.1f Mbps", value / 1_000_000.0)
        } else {
            String.format(Locale.ROOT, "%.0f kbps", value / 1000.0)
        }

    private fun formatExposure(seconds: Double): String =
        when {
            seconds <= 0.0 -> "-"
            seconds < 1.0 -> {
                val denominator = (1.0 / seconds).roundToInt().coerceAtLeast(1)
                "1/$denominator с"
            }
            else -> "${formatDecimal(seconds)} с"
        }

    private fun formatSigned(value: Double): String =
        if (value > 0.0) "+${formatDecimal(value)}" else formatDecimal(value)

    private fun formatDecimal(
        value: Double,
        suffix: String = "",
    ): String {
        val rounded = if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
        }
        return rounded + suffix
    }
}

package com.mistermikhail.fgallery.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException

/** Copy portable camera/GPS/time EXIF to edited JPEGs; dimensions/orientation belong to new pixels. */
internal object CropExif {
    // ExifInterface reads RAW (DNG/NEF/CR2) and writes supported JPEG IFDs.
    private val tags = listOf(
        "Make", "Model", "Software", "DateTime", "DateTimeOriginal", "DateTimeDigitized",
        "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
        "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
        "ExposureTime", "FNumber", "ISOSpeedRatings", "PhotographicSensitivity",
        "ExposureProgram", "ExposureBiasValue", "ExposureMode", "MeteringMode",
        "Flash", "FocalLength", "FocalLengthIn35mmFilm", "WhiteBalance",
        "DigitalZoomRatio", "SceneCaptureType", "SensingMethod",
        "LensMake", "LensModel", "LensSpecification", "BodySerialNumber",
        "Artist", "Copyright", "UserComment", "ImageDescription",
        "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef",
        "GPSAltitude", "GPSAltitudeRef", "GPSTimeStamp", "GPSDateStamp",
        "GPSProcessingMethod", "GPSMapDatum", "GPSImgDirection", "GPSImgDirectionRef",
        "GPSDestLatitude", "GPSDestLatitudeRef", "GPSDestLongitude", "GPSDestLongitudeRef",
        "GPSDestBearing", "GPSDestBearingRef", "GPSHPositioningError",
        "FlashpixVersion", "ColorSpace", "ComponentsConfiguration",
        "ExposureIndex", "LightSource", "SubjectDistance", "Sharpness", "Contrast",
        "Saturation", "CustomRendered", "DeviceSettingDescription"
    )

    fun transfer(context: Context, original: MediaItem, edited: Uri, targetMime: String) {
        if (targetMime != "image/jpeg") return
        val source = context.contentResolver.openInputStream(original.uri)?.use { ExifInterface(it) }
            ?: throw IOException("Не удалось прочитать EXIF исходника")
        val attributes = tags.mapNotNull { tag -> source.getAttribute(tag)?.let { tag to it } }
        // If RAW EXIF cannot be parsed, report the failure rather than silently saving a stripped JPEG.
        if (original.kind == MediaKind.RAW && attributes.isEmpty()) {
            throw IOException("EXIF RAW-файла не прочитан: копия не сохранена без метаданных")
        }
        if (attributes.isEmpty()) return
        val descriptor = context.contentResolver.openFileDescriptor(edited, "rw")
            ?: throw IOException("Нет доступа для записи EXIF")
        descriptor.use { file ->
            val output = ExifInterface(file.fileDescriptor)
            attributes.forEach { (tag, value) -> output.setAttribute(tag, value) }
            // Cropping and rotation are baked into the new raster; stale dimensions/orientation are invalid.
            output.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            output.saveAttributes()
        }
        val verification = context.contentResolver.openInputStream(edited)?.use { ExifInterface(it) }
            ?: throw IOException("Не удалось проверить сохранённый EXIF")
        for (tag in listOf("Make", "Model", "DateTimeOriginal", "ExposureTime", "FNumber",
            "GPSLatitude", "GPSLongitude")) {
            source.getAttribute(tag)?.let { before ->
                check(verification.getAttribute(tag) == before) { "EXIF $tag не сохранился" }
            }
        }
    }
}

internal object CropCopyNames {
    fun next(original: String, ext: String, exists: (String) -> Boolean): String {
        val stem = original.substringBeforeLast('.', original).ifBlank { "photo" }
        for (number in 1..99999) {
            val candidate = "${stem}_${number}.${ext}"
            if (!exists(candidate)) return candidate
        }
        error("Слишком много копий файла")
    }
}

# FGallery Stage 2 / 0.3.1 — R8/JNI compatibility.
# Native methods are resolved by JNI names, not via ordinary Java references.
-keepclasseswithmembernames class * {
    native <methods>;
}

# AndroidLibRaw 2.0.7 uses JNI exported class/method names and a native handle.
-keep class com.homesoft.photo.libraw.** { *; }

# PDFium and TIFF libraries invoke internal models/methods via reflection/JNI.
-keep class io.legere.pdfiumandroid.** { *; }
-keep class org.pdfium.** { *; }
-keep class org.beyka.tiffbitmapfactory.** { *; }
-keep class org.beyka.** { *; }

# Keep user-defined native entrypoints. Their names must remain stable.
-keepclasseswithmembers class com.mistermikhail.fgallery.** {
    native <methods>;
}

# Media3 extensibility includes reflective renderer lookups. Keep classes
# which are instantiated by their fully-qualified names.
-keep class androidx.media3.exoplayer.ext.** { *; }
-keep class androidx.media3.decoder.** { *; }

# Preserve class names used by serialized Android state restoration.
-keepnames class * implements android.os.Parcelable

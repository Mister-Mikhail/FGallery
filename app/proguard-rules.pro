# Stage 2 / 0.3.1: native-JNI safety rules for first R8 QA build.
# Narrow these only after verifying physical IIQ/3FR/CR2/TIFF/PDF on Android 16.
#
# AndroidLibRaw JNI binds native methods by Java class/method signatures.
-keep class com.homesoft.photo.libraw.** { *; }
-keep class com.mistermikhail.fgallery.data.TiffNative { *; }
# JNI PDF and TIFF decoder libraries and their reflected models.
-keep class io.legere.pdfiumandroid.** { *; }
-keep class com.github.beyka.tiffbitmapfactory.** { *; }
# All JNI signatures are stable, including Media3/third-party native entry points.
-keepclasseswithmembernames class * { native <methods>; }
# Spherical video renderer is a Java compatibility copy backed by Media3 classes.
-keep class androidx.media3.exoplayer.video.spherical.** { *; }
# Dependency consumer rules remain active. Avoid globally keeping Compose or
# material-icons-extended: shrinking those unused icons drives the size saving.

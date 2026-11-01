# Runtime referenced from the separate, unminified AndroidJUnitRunner test APK.
# These keep rules apply ONLY to the minifiedTest build; never to the release APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Compose test runtime calls DefaultImpls from its separate unminified APK.
-keep class androidx.compose.ui.platform.InfiniteAnimationPolicy** { *; }

# The separately compiled AndroidTest APK calls these implementation methods
# and Kotlin default-argument bridges directly. R8 cannot see those callers.
# Preserve ONLY in minifiedTest, never in the distributed R8 release.
-keep class com.mistermikhail.fgallery.data.** { *; }
-keep class androidx.compose.ui.platform.ViewRootForTest** { *; }

# Compose UI testing calls platform APIs from a distinct, non-minified test APK.
# Preserve their exact methods and synthetic bridges in minifiedTest only.
-keep class androidx.compose.ui.platform.** { *; }

# Test source references public/internal Compose entrypoints and Kotlin bridges.
-keep class com.mistermikhail.fgallery.ui.** { *; }

# Crop instrumented tests invoke a debug-only host from an unminified test APK.
-keep class com.mistermikhail.fgallery.CropTestHostActivity** { *; }
-keep class com.mistermikhail.fgallery.TestDriveProvider** { *; }

# Instrumentation test bytecode lives in its own APK. The app shrinker
# otherwise erases Compose runtime/lazy methods invoked only by that APK
# (Snapshot.hasPendingChanges, LazyListScope.items$default), producing
# NoSuchMethodError and a false minified-instrumentation failure.
# Test build ONLY: the release APK still receives normal R8 shrinking.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.foundation.lazy.** { *; }

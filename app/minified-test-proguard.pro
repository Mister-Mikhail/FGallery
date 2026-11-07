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

# Compose UI instrumentation lives in a separate APK; RootForTest and
# semantics methods are otherwise optimized away from the host app.
# These rules apply ONLY to minifiedTest, not the distributed release APK.
-keep class androidx.compose.ui.node.** { *; }
-keep class androidx.compose.ui.semantics.** { *; }

# Compose UI tests invoke LayoutInfo, ImageBitmapKt and other UI ABI
# from their separate APK; keep the whole Compose UI namespace here only.
-keep class androidx.compose.ui.** { *; }

# AndroidTest is compiled and installed as a separate, UNMINIFIED APK.
# It calls public and Kotlin default-argument Compose APIs through a binary
# boundary invisible to the app's R8 analysis. Keep those APIs in the
# minifiedTest HOST ONLY. Production release remains fully R8-minified.
# This supersedes the piecemeal rules above (Snapshot, LayoutInfo, RootForTest).
-keep class androidx.compose.** { *; }

# CatalogDatabaseTest is in a separate AndroidTest APK and directly calls
# Room.inMemoryDatabaseBuilder(), which production code never calls. R8
# cannot see that binary reference and strips the static factory method.
# Preserve only Room's test-facing factory API on minifiedTest; do not
# disable R8 for release or keep the whole Room runtime.
-keep class androidx.room.Room { *; }

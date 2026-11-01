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

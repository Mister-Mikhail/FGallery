# Runtime referenced from the separate, unminified AndroidJUnitRunner test APK.
# These keep rules apply ONLY to the minifiedTest build; never to the release APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Compose test runtime calls DefaultImpls from its separate unminified APK.
-keep class androidx.compose.ui.platform.InfiniteAnimationPolicy** { *; }

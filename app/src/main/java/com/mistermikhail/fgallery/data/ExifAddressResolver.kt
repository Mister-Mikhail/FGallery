package com.mistermikhail.fgallery.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Uses the device's geocoder; the source image and its other metadata stay on the device. */
internal object ExifAddressResolver {
    private data class Key(val coordinates: ExifGpsCoordinates, val locale: Locale)
    private data class Cached(val address: String?, val validUntil: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val requests = mutableMapOf<Key, Deferred<String?>>()
    private val cache = LruCache<Key, Cached>(200)
    private val concurrentLookups = Semaphore(2)

    suspend fun resolve(context: Context, coordinates: ExifGpsCoordinates, locale: Locale): String? {
        val key = Key(coordinates, locale)
        val result = mutex.withLock {
            cache.get(key)?.takeIf { it.validUntil > SystemClock.elapsedRealtime() }?.let {
                return it.address
            }
            requests[key] ?: scope.async {
                val resolved = withTimeoutOrNull(7_000) {
                    concurrentLookups.withPermit {
                        lookup(context.applicationContext, key)
                    }
                }
                mutex.withLock {
                    val lifetime = if (resolved == null) 30_000L else 24 * 60 * 60 * 1000L
                    cache.put(key, Cached(resolved, SystemClock.elapsedRealtime() + lifetime))
                    requests.remove(key)
                }
                resolved
            }.also { requests[key] = it }
        }
        return result.await()
    }

    @Suppress("DEPRECATION")
    private suspend fun lookup(context: Context, key: Key): String? {
        try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, key.locale)
            val (latitude, longitude) = key.coordinates
            val address = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<Address?> { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                runInterruptible { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }
            }
            return address?.getAddressLine(0)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (failure: Exception) {
            if (failure is kotlin.coroutines.cancellation.CancellationException) throw failure
            return null
        }
    }
}

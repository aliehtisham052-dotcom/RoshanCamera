package com.innovation313.roshancamera.location

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Turns coordinates into a human address, and caches the answer.
 *
 * The cache is keyed on coordinates rounded to about a hundred metres, because
 * a person photographing twenty crates in one yard should cost one lookup, not
 * twenty. Reverse geocoding needs the network; when it is unavailable the
 * address falls back to plain coordinates rather than to a stale guess, because
 * a wrong street name on a proof photo is worse than no street name.
 */
class AddressResolver(context: Context) {

    private val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null
    private val cache = LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true)

    suspend fun resolve(latitude: Double, longitude: Double): String {
        val key = cacheKey(latitude, longitude)
        synchronized(cache) { cache[key] }?.let { return it }

        val address = lookup(latitude, longitude) ?: coordinates(latitude, longitude)
        synchronized(cache) {
            cache[key] = address
            while (cache.size > CACHE_SIZE) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return address
    }

    @Suppress("DEPRECATION")
    private suspend fun lookup(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            val coder = geocoder ?: return@withContext null
            runCatching {
                val results = coder.getFromLocation(latitude, longitude, 1)
                val first = results?.firstOrNull() ?: return@runCatching null
                // The full formatted line carries the street — the "exact
                // location" the stamp is judged by. The assembled parts remain
                // only as a fallback for geocoders that return no address line.
                first.getAddressLine(0)?.takeIf { it.isNotBlank() }?.let { return@runCatching it }
                val parts = buildList {
                    first.subLocality?.let(::add)
                    first.locality?.let(::add)
                    first.subAdminArea?.takeIf { it != first.locality }?.let(::add)
                    first.adminArea?.let(::add)
                    first.countryName?.let(::add)
                }
                parts.distinct().joinToString(", ").ifBlank { null }
            }.getOrNull()
        }

    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    /** ~100 m buckets: three decimal places of a degree. */
    private fun cacheKey(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.3f/%.3f", latitude, longitude)

    private companion object {
        const val CACHE_SIZE = 64
    }
}

package com.innovation313.roshancamera.location

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Turns coordinates into the two lines the stamp needs — a bold region line
 * ("Pasrur, Punjab, Pakistan") and the exact street line under it — matching
 * the layout of the reference stamp the owner supplied.
 *
 * Cached on ~100 m buckets: photographing twenty crates in one yard costs one
 * lookup, not twenty. Offline, both lines fall back to plain coordinates —
 * a wrong street name on a proof photo is worse than no street name.
 */
class AddressResolver(context: Context) {

    data class Resolved(val region: String, val exact: String)

    private val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null
    private val cache = LinkedHashMap<String, Resolved>(CACHE_SIZE, 0.75f, true)

    suspend fun resolve(latitude: Double, longitude: Double): Resolved {
        val key = cacheKey(latitude, longitude)
        synchronized(cache) { cache[key] }?.let { return it }

        val fallback = coordinates(latitude, longitude)
        val resolved = lookup(latitude, longitude) ?: Resolved(fallback, fallback)
        synchronized(cache) {
            cache[key] = resolved
            while (cache.size > CACHE_SIZE) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return resolved
    }

    @Suppress("DEPRECATION")
    private suspend fun lookup(latitude: Double, longitude: Double): Resolved? =
        withContext(Dispatchers.IO) {
            val coder = geocoder ?: return@withContext null
            runCatching {
                val first = coder.getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull() ?: return@runCatching null

                val region = buildList {
                    first.locality?.let(::add)
                    first.subAdminArea?.takeIf { it != first.locality }?.let(::add)
                    first.adminArea?.let(::add)
                    first.countryName?.let(::add)
                }.distinct().joinToString(", ")

                // The full formatted line carries the street — the "exact
                // location" the stamp is judged by.
                val exact = first.getAddressLine(0)
                    ?.takeIf { it.isNotBlank() }
                    ?: buildList {
                        first.thoroughfare?.let(::add)
                        first.subLocality?.let(::add)
                        first.locality?.let(::add)
                    }.distinct().joinToString(", ")

                if (region.isBlank() && exact.isBlank()) null
                else Resolved(
                    region = region.ifBlank { exact },
                    exact = exact.ifBlank { region }
                )
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

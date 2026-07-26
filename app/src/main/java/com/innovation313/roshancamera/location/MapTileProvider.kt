package com.innovation313.roshancamera.location

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The little map on the stamp, in the style of the leading GPS cameras.
 *
 * Tiles come from OpenStreetMap — no key, no billing account, which a public
 * repo can live with. The usage policy asks for a real User-Agent and modest
 * volume: one 256 px tile per ~500 m bucket, LRU-cached, is well inside it,
 * and the renderer draws the required attribution onto the tile itself.
 *
 * Strictly best-effort like the weather: short timeouts, null on failure, and
 * the stamp simply omits the map rather than delaying a capture. This is the
 * one place the "no map engine" rule bends — a cached static tile costs none
 * of the startup or memory weight that rule exists to keep out.
 */
class MapTileProvider {

    /** A fetched tile plus where the fix falls on it, in tile pixels. */
    data class Tile(val bitmap: Bitmap, val pinX: Int, val pinY: Int)

    private val cache = LruCache<String, Bitmap>(CACHE_TILES)

    suspend fun tileFor(latitude: Double, longitude: Double): Tile? =
        withContext(Dispatchers.IO) {
            val n = 1 shl ZOOM
            val xExact = (longitude + 180.0) / 360.0 * n
            val latRad = latitude * PI / 180.0
            val yExact = (1.0 - ln(tan(latRad) + 1 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
            val x = floor(xExact).toInt().coerceIn(0, n - 1)
            val y = floor(yExact).toInt().coerceIn(0, n - 1)

            val key = "$ZOOM/$x/$y"
            val cached = synchronized(cache) { cache.get(key) }
            val bitmap = cached ?: fetch(key)?.also {
                synchronized(cache) { cache.put(key, it) }
            } ?: return@withContext null

            Tile(
                bitmap = bitmap,
                pinX = ((xExact - x) * TILE_PX).toInt().coerceIn(0, TILE_PX - 1),
                pinY = ((yExact - y) * TILE_PX).toInt().coerceIn(0, TILE_PX - 1)
            )
        }

    private fun fetch(key: String): Bitmap? = runCatching {
        val connection = URL("https://tile.openstreetmap.org/$key.png")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        // OSM's tile policy rejects the default Java agent; identify honestly.
        connection.setRequestProperty(
            "User-Agent",
            "RoshanCamera/1.0 (Android; innovation313.support@gmail.com)"
        )
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private companion object {
        /** Street-level, matching what the stamp's address line claims. */
        const val ZOOM = 16
        const val TILE_PX = 256
        const val CACHE_TILES = 8
        const val TIMEOUT_MS = 4_000
    }
}

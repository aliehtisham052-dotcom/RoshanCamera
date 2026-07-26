package com.innovation313.roshancamera.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Current temperature for the stamp, in the style of the leading GPS cameras.
 *
 * Open-Meteo: no API key, no account, free for this volume — which matters in
 * a repo that is public and an app that ships no secrets. Weather is the one
 * thing here that needs the network, so it is strictly best-effort: a ~2 km
 * cache bucket with a half-hour TTL, four-second timeouts, and null on any
 * failure. The stamp simply omits the temperature rather than ever delaying
 * or blocking a capture on a web request.
 */
class WeatherProvider {

    private data class Reading(val tempC: Int, val fetchedAt: Long)

    private val cache = HashMap<String, Reading>()

    suspend fun currentTempC(latitude: Double, longitude: Double): Int? =
        withContext(Dispatchers.IO) {
            val key = String.format(Locale.US, "%.2f/%.2f", latitude, longitude)

            synchronized(cache) { cache[key] }
                ?.takeIf { System.currentTimeMillis() - it.fetchedAt < TTL_MS }
                ?.let { return@withContext it.tempC }

            runCatching {
                val url = URL(
                    String.format(
                        Locale.US,
                        "https://api.open-meteo.com/v1/forecast" +
                            "?latitude=%.4f&longitude=%.4f&current_weather=true",
                        latitude, longitude
                    )
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.inputStream.use { stream ->
                    val body = stream.readBytes().decodeToString()
                    val temp = JSONObject(body)
                        .getJSONObject("current_weather")
                        .getDouble("temperature")
                        .roundToInt()
                    synchronized(cache) {
                        cache[key] = Reading(temp, System.currentTimeMillis())
                    }
                    temp
                }
            }.getOrNull()
        }

    private companion object {
        const val TTL_MS = 30 * 60 * 1000L
        const val TIMEOUT_MS = 4_000
    }
}

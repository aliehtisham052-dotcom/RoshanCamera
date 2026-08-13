package com.innovation313.roshancamera.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Current temperature and condition for the stamp's weather row.
 *
 * Open-Meteo: no API key, no account, free for this volume — which matters in
 * a repo that is public and an app that ships no secrets. Weather is the one
 * thing here that needs the network, so it is strictly best-effort: a ~2 km
 * cache bucket with a half-hour TTL, four-second timeouts, and null on any
 * failure. The stamp simply omits the weather rather than ever delaying or
 * blocking a capture on a web request.
 */
class WeatherProvider {

    /** Temperature plus the WMO weather code Open-Meteo reports. */
    data class Weather(val tempC: Int, val wmoCode: Int)

    private data class Reading(val weather: Weather, val fetchedAt: Long)

    private val cache = HashMap<String, Reading>()

    suspend fun current(latitude: Double, longitude: Double): Weather? =
        withContext(Dispatchers.IO) {
            val key = String.format(Locale.US, "%.2f/%.2f", latitude, longitude)

            synchronized(cache) { cache[key] }
                ?.takeIf { System.currentTimeMillis() - it.fetchedAt < TTL_MS }
                ?.let { return@withContext it.weather }

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
                    val current = JSONObject(body).getJSONObject("current_weather")
                    val weather = Weather(
                        tempC = current.getDouble("temperature").roundToInt(),
                        wmoCode = current.optInt("weathercode", -1)
                    )
                    synchronized(cache) {
                        cache[key] = Reading(weather, System.currentTimeMillis())
                    }
                    weather
                }
            }.getOrNull()
        }

    private companion object {
        const val TTL_MS = 30 * 60 * 1000L
        const val TIMEOUT_MS = 4_000
    }
}

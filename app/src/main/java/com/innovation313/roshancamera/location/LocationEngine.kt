package com.innovation313.roshancamera.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps a current position ready before the shutter is ever pressed.
 *
 * Competitor reviews describe the same failure again and again: the map shows
 * the right place but an old address is burned into the photo. That happens
 * when an app asks for a location at capture time and accepts whatever cached
 * fix comes back first. This engine instead starts listening the moment the
 * camera screen appears, and reports honestly how good the current fix is.
 *
 * Nothing here runs in the background. Updates start in `onStart` and stop in
 * `onStop`; the app declares no background-location permission at all.
 */
class LocationEngine(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _state = MutableStateFlow<LocationState>(LocationState.Searching)
    val state: StateFlow<LocationState> = _state.asStateFlow()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fix = result.lastLocation ?: return
            _state.value = classify(fix)
        }
    }

    private var running = false

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission()) return
        running = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        // A cached fix gives the HUD something to show immediately, but it is
        // deliberately never treated as locked — see classify().
        client.lastLocation.addOnSuccessListener { cached ->
            if (cached != null && _state.value is LocationState.Searching) {
                _state.value = classify(cached)
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        client.removeLocationUpdates(callback)
        _state.value = LocationState.Searching
    }

    /**
     * A fix counts as locked only when it is both recent and accurate enough
     * that the address it resolves to will be the right one. Everything else is
     * reported as weak, and the shutter says so rather than stamping a guess.
     */
    private fun classify(fix: Location): LocationState {
        val ageMs = System.currentTimeMillis() - fix.time
        val accuracy = if (fix.hasAccuracy()) fix.accuracy else Float.MAX_VALUE
        return when {
            ageMs > STALE_AFTER_MS -> LocationState.Weak(fix, accuracy, StaleReason.OLD_FIX)
            accuracy > LOCK_ACCURACY_M -> LocationState.Weak(fix, accuracy, StaleReason.LOW_ACCURACY)
            else -> LocationState.Locked(fix, accuracy)
        }
    }

    companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val FASTEST_INTERVAL_MS = 500L

        /** Beyond this, the address on the photo may be the wrong street. */
        const val LOCK_ACCURACY_M = 30f

        /** A fix older than this is treated as unproven, however precise it claims to be. */
        const val STALE_AFTER_MS = 30_000L
    }
}

enum class StaleReason { OLD_FIX, LOW_ACCURACY }

sealed interface LocationState {
    data object Searching : LocationState
    data class Weak(val fix: Location, val accuracyMetres: Float, val reason: StaleReason) : LocationState
    data class Locked(val fix: Location, val accuracyMetres: Float) : LocationState

    val fixOrNull: Location?
        get() = when (this) {
            is Locked -> fix
            is Weak -> fix
            Searching -> null
        }

    val isLocked: Boolean get() = this is Locked
}

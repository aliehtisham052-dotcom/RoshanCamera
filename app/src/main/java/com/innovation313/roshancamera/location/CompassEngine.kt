package com.innovation313.roshancamera.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Live compass heading for the "NE 45°" row, from the rotation-vector sensor.
 *
 * Same lifecycle discipline as [LocationEngine]: starts with the screen,
 * stops with it, nothing in the background. Emits null when the device has no
 * usable sensor, and the row simply hides rather than showing a guess.
 */
class CompassEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _azimuthDegrees = MutableStateFlow<Int?>(null)
    val azimuthDegrees: StateFlow<Int?> = _azimuthDegrees.asStateFlow()

    val available: Boolean get() = sensor != null

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        val s = sensor ?: return
        sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        _azimuthDegrees.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).roundToInt() % 360

        // A degree of hysteresis keeps the row from flickering in the hand.
        val previous = _azimuthDegrees.value
        if (previous == null || angularGap(previous, degrees) >= 2) {
            _azimuthDegrees.value = degrees
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun angularGap(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    companion object {
        private val NAMES =
            arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

        /** "NE 45°" — the exact form on the owner's mockup. */
        fun describe(degrees: Int): String {
            val index = ((degrees + 22.5) / 45.0).toInt() % 8
            return "${NAMES[index]} $degrees°"
        }
    }
}

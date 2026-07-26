package com.innovation313.roshancamera

import android.app.Application

/**
 * Application entry point.
 *
 * Deliberately empty for now. Roshan Camera is offline-first: nothing is
 * initialised at process start that would slow the first frame. Location
 * pre-warming belongs to the camera screen's lifecycle, not here.
 */
class RoshanCameraApp : Application()

package com.innovation313.roshancamera

import android.content.Context

/**
 * The few things a user chooses. Kept in preferences rather than a database
 * because none of it is a record — it is configuration.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Printed as the first line of the stamp. Blank falls back to the app name. */
    var businessName: String?
        get() = prefs.getString(KEY_BUSINESS_NAME, null)
        set(value) = prefs.edit().putString(KEY_BUSINESS_NAME, value?.trim()).apply()

    /** Torch on capture. Lives here, off the camera screen, by the owner's direction. */
    var flashOn: Boolean
        get() = prefs.getBoolean(KEY_FLASH, false)
        set(value) = prefs.edit().putBoolean(KEY_FLASH, value).apply()

    /** Rule-of-thirds guide over the viewfinder. */
    var gridOn: Boolean
        get() = prefs.getBoolean(KEY_GRID, false)
        set(value) = prefs.edit().putBoolean(KEY_GRID, value).apply()

    /** BCP-47 tag chosen in the app, independent of the system language. */
    var languageTag: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** False = 3:4 (default), true = 9:16, toggled by the side ratio button. */
    var ratioWide: Boolean
        get() = prefs.getBoolean(KEY_RATIO_WIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_RATIO_WIDE, value).apply()

    /** Compass row on the overlay and the photo. On by default, per the mockup. */
    var compassOn: Boolean
        get() = prefs.getBoolean(KEY_COMPASS, true)
        set(value) = prefs.edit().putBoolean(KEY_COMPASS, value).apply()

    private companion object {
        const val FILE = "roshan_camera_settings"
        const val KEY_BUSINESS_NAME = "business_name"
        const val KEY_LANGUAGE = "language_tag"
        const val KEY_FLASH = "flash_on"
        const val KEY_GRID = "grid_on"
        const val KEY_RATIO_WIDE = "ratio_wide"
        const val KEY_COMPASS = "compass_on"
    }
}

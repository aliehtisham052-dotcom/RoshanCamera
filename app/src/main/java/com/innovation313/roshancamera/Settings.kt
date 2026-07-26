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

    /** BCP-47 tag chosen in the app, independent of the system language. */
    var languageTag: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    private companion object {
        const val FILE = "roshan_camera_settings"
        const val KEY_BUSINESS_NAME = "business_name"
        const val KEY_LANGUAGE = "language_tag"
    }
}

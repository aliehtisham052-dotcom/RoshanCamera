package com.innovation313.roshancamera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.innovation313.roshancamera.databinding.ActivitySettingsBinding

/**
 * Business name and language.
 *
 * The language picker uses per-app locales rather than restarting the process
 * with a swapped configuration, so the choice survives updates and shows up in
 * the system's own app-language settings.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { Settings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.businessName.setText(settings.businessName.orEmpty())
        binding.back.setOnClickListener { finish() }

        binding.save.setOnClickListener {
            settings.businessName = binding.businessName.text?.toString()
            finish()
        }

        binding.languageEnglish.setOnClickListener { applyLanguage("en") }
        binding.languageUrdu.setOnClickListener { applyLanguage("ur") }
        binding.languageRomanUrdu.setOnClickListener { applyLanguage("ur-Latn") }
    }

    private fun applyLanguage(tag: String) {
        settings.languageTag = tag
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}

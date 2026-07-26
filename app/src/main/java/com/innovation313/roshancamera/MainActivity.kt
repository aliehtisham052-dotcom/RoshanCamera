package com.innovation313.roshancamera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.innovation313.roshancamera.databinding.ActivityMainBinding

/**
 * Placeholder launch screen.
 *
 * The camera surface, location engine and stamp pipeline are added in
 * later steps; this exists so the project builds and installs from day one.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}

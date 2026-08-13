package com.innovation313.roshancamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.innovation313.roshancamera.databinding.ActivityMainBinding
import com.innovation313.roshancamera.location.AddressResolver
import com.innovation313.roshancamera.location.CompassEngine
import com.innovation313.roshancamera.location.LocationEngine
import com.innovation313.roshancamera.location.LocationState
import com.innovation313.roshancamera.location.StaleReason
import com.innovation313.roshancamera.location.WeatherProvider
import com.innovation313.roshancamera.location.MapTileProvider
import com.innovation313.roshancamera.stamp.MapTileBitmap
import com.innovation313.roshancamera.proof.Proof
import com.innovation313.roshancamera.proof.ProofLedger
import com.innovation313.roshancamera.proof.ProofRecord
import com.innovation313.roshancamera.stamp.StampContent
import com.innovation313.roshancamera.stamp.StampIcons
import com.innovation313.roshancamera.stamp.StampRenderer
import com.innovation313.roshancamera.storage.PhotoStore
import com.innovation313.roshancamera.storage.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The camera screen, in the owner's mockup design: a stack of live pills up
 * the lower-left — date/time, coordinates, address, altitude and accuracy,
 * weather, map, compass, watermark — mirroring exactly what will be burned
 * onto the photo, with settings/flash/ratio/compass controls down the right.
 *
 * The one rule that shapes this class: **the shutter never waits.** Capture
 * hands back an in-memory frame and the button is live again immediately;
 * hashing, geocoding, stamping and saving all happen afterwards on a background
 * dispatcher. A rider photographing fifteen parcels should be able to press
 * fifteen times without the app blocking once.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val locationEngine by lazy { LocationEngine(this) }
    private val compassEngine by lazy { CompassEngine(this) }
    private val addressResolver by lazy { AddressResolver(this) }
    private val photoStore by lazy { PhotoStore(this) }
    private val ledger by lazy { ProofLedger(this) }
    private val settings by lazy { Settings(this) }
    private val weather by lazy { WeatherProvider() }
    private val mapTiles by lazy { MapTileProvider() }

    private var lastWeather: WeatherProvider.Weather? = null
    private var latestAzimuth: Int? = null

    private var imageCapture: ImageCapture? = null
    private var latestState: LocationState = LocationState.Searching
    private var flashOn = false
    private var lastResolvedBucket: String? = null

    private var askedThisLaunch = false

    /** The same badge art the overlay shows, pre-rendered for the photo stamp. */
    private val stampIcons by lazy {
        StampIcons(
            calendar = badgeBitmap(R.drawable.ic_row_calendar, R.color.badge_blue),
            pin = badgeBitmap(R.drawable.ic_row_pin, R.color.badge_green),
            home = badgeBitmap(R.drawable.ic_row_home, R.color.badge_indigo),
            mountain = badgeBitmap(R.drawable.ic_row_mountain, R.color.badge_teal),
            sun = badgeBitmap(R.drawable.ic_row_sun, R.color.badge_amber),
            compass = badgeBitmap(R.drawable.ic_row_compass, R.color.badge_teal),
            camera = badgeBitmap(R.drawable.ic_row_cam, R.color.badge_slate)
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { applyPermissionState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system hands over from the
        // launch theme rather than flashing an empty window.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpSystemBars()
        binding.locationStatus.padForStatusBar()
        binding.controls.padForNavigationBar()

        binding.shutter.setOnClickListener { capture() }
        binding.openGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        binding.openSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.sideSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.sideFlash.setOnClickListener { toggleFlash() }
        binding.sideRatio.setOnClickListener { toggleRatio() }
        binding.sideCompass.setOnClickListener { toggleCompass() }

        binding.permissionAction.setOnClickListener { onPermissionActionClicked() }

        // Until the first fix, only the clock and the watermark have anything
        // truthful to say; the location rows appear as the data arrives.
        binding.pillCoords.visibility = View.GONE
        binding.pillAltitude.visibility = View.GONE
        binding.pillWeather.visibility = View.GONE
        binding.pillCompass.visibility = View.GONE
        binding.tvExact.visibility = View.GONE
        binding.tvRegion.text = getString(R.string.gps_searching)

        startStampClock()

        // TextureView rather than SurfaceView. Several OEM builds never deliver
        // a first frame to a SurfaceView-backed preview, and the symptom is a
        // permanently black screen with no error to go on.
        binding.preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        observeLocation()
        observeCompass()
    }

    override fun onResume() {
        super.onResume()
        // Settings sits above this screen, so everything it controls is
        // re-read here — the side buttons and the settings switches drive the
        // same stored values.
        flashOn = settings.flashOn
        imageCapture?.flashMode =
            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        binding.gridOverlay.visibility = if (settings.gridOn) View.VISIBLE else View.GONE
        renderSideControls()
        binding.tvWatermark.text = watermarkText()
    }

    override fun onStart() {
        super.onStart()
        // Re-checked on every entry, so returning from the system settings
        // screen with a permission newly granted just works.
        applyPermissionState()
        loadGalleryThumb()
        if (settings.compassOn) compassEngine.start()
    }

    override fun onStop() {
        super.onStop()
        locationEngine.stop()
        compassEngine.stop()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * The single place that decides what the screen shows.
     *
     * Without this the app had no answer for a refused permission: the preview
     * stayed black and the pill read "finding your location" forever, which
     * looks exactly like a broken app rather than a missing grant.
     */
    private fun applyPermissionState() {
        val missing = REQUIRED_PERMISSIONS.filterNot(::granted)

        if (missing.isEmpty()) {
            binding.permissionPanel.visibility = View.GONE
            binding.shutter.isEnabled = true
            startCamera()
            locationEngine.start()
            return
        }

        if (!askedThisLaunch) {
            askedThisLaunch = true
            permissionLauncher.launch(missing.toTypedArray())
            return
        }

        // Asked and still missing. Android only lets an app re-prompt while it
        // may still show a rationale; past that the only route is settings, so
        // the button has to say which of the two it is.
        val canAskAgain = missing.any(::shouldShowRequestPermissionRationale)
        binding.permissionPanel.visibility = View.VISIBLE
        binding.shutter.isEnabled = false
        binding.permissionMessage.setText(
            when {
                Manifest.permission.CAMERA in missing &&
                    Manifest.permission.ACCESS_FINE_LOCATION in missing -> R.string.permission_need_both
                Manifest.permission.CAMERA in missing -> R.string.permission_need_camera
                else -> R.string.permission_need_location
            }
        )
        binding.permissionAction.setText(
            if (canAskAgain) R.string.permission_allow else R.string.permission_open_settings
        )
        binding.permissionAction.tag = canAskAgain
    }

    private fun onPermissionActionClicked() {
        val canAskAgain = binding.permissionAction.tag as? Boolean ?: true
        if (canAskAgain) {
            permissionLauncher.launch(REQUIRED_PERMISSIONS.filterNot(::granted).toTypedArray())
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    // ---- Side controls -----------------------------------------------------

    private fun toggleFlash() {
        settings.flashOn = !settings.flashOn
        flashOn = settings.flashOn
        imageCapture?.flashMode =
            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        renderSideControls()
    }

    private fun toggleRatio() {
        settings.ratioWide = !settings.ratioWide
        renderSideControls()
        // The ratio lives inside the camera binding, so the camera restarts.
        if (REQUIRED_PERMISSIONS.all(::granted)) startCamera()
    }

    private fun toggleCompass() {
        settings.compassOn = !settings.compassOn
        if (settings.compassOn) compassEngine.start() else compassEngine.stop()
        renderSideControls()
        renderCompassRow()
    }

    private fun renderSideControls() {
        binding.sideFlash.imageTintList = ContextCompat.getColorStateList(
            this, if (settings.flashOn) R.color.accent_amber else android.R.color.white
        )
        binding.sideCompass.imageTintList = ContextCompat.getColorStateList(
            this, if (settings.compassOn) R.color.accent_amber else android.R.color.white
        )
        binding.sideRatio.text = if (settings.ratioWide) RATIO_WIDE_LABEL else RATIO_CLASSIC_LABEL
    }

    private fun renderCompassRow() {
        val az = latestAzimuth
        if (settings.compassOn && az != null) {
            binding.pillCompass.visibility = View.VISIBLE
            binding.tvCompass.text = CompassEngine.describe(az)
        } else {
            binding.pillCompass.visibility = View.GONE
        }
    }

    // ---- Camera ------------------------------------------------------------

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    if (settings.ratioWide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                    else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also {
                    it.surfaceProvider = binding.preview.surfaceProvider
                }

            // MINIMIZE_LATENCY is the whole point: it trades a little noise
            // reduction for a shutter that fires when it is pressed.
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(resolutionSelector)
                .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
            }.onFailure {
                // A silent failure here is what a black preview looks like from
                // the outside, so it is stated on screen rather than logged.
                binding.permissionPanel.visibility = View.VISIBLE
                binding.permissionMessage.text = getString(R.string.camera_unavailable)
                binding.permissionAction.visibility = View.GONE
                binding.shutter.isEnabled = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---- Live overlay ------------------------------------------------------

    private fun observeLocation() {
        lifecycleScope.launch {
            locationEngine.state.collectLatest { state ->
                latestState = state
                binding.locationStatus.text = describe(state)
                val locked = state.isLocked
                binding.gpsChip.setBackgroundResource(
                    if (locked) R.drawable.bg_gps_chip_locked else R.drawable.bg_gps_chip_weak
                )
                binding.gpsDot.background.setTint(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (locked) R.color.status_locked else R.color.status_weak
                    )
                )
                updateStampPreview(state)
            }
        }
    }

    private fun observeCompass() {
        lifecycleScope.launch {
            compassEngine.azimuthDegrees.collectLatest { az ->
                latestAzimuth = az
                renderCompassRow()
            }
        }
    }

    /**
     * The live pills mirror what will be drawn on the next photo — the single
     * most-requested convenience in competitor reviews. The address is
     * re-resolved only when the ~100 m bucket changes, so walking around a
     * yard does not fire a geocoder call per second.
     */
    private fun updateStampPreview(state: LocationState) {
        val fix = state.fixOrNull
        if (fix == null) {
            binding.pillCoords.visibility = View.GONE
            binding.pillAltitude.visibility = View.GONE
            binding.tvExact.visibility = View.GONE
            binding.tvRegion.text = getString(R.string.gps_searching)
            return
        }

        binding.pillCoords.visibility = View.VISIBLE
        binding.tvCoords.text = coordsText(fix.latitude, fix.longitude)

        binding.pillAltitude.visibility = View.VISIBLE
        val accuracy = if (fix.hasAccuracy()) fix.accuracy.roundToInt() else 0
        binding.tvAltitude.text = getString(
            R.string.altitude_row, altitudeText(fix.hasAltitude(), fix.altitude), accuracy
        )

        val bucket = String.format(Locale.US, "%.3f/%.3f", fix.latitude, fix.longitude)
        if (bucket != lastResolvedBucket) {
            lastResolvedBucket = bucket
            lifecycleScope.launch {
                val resolved = addressResolver.resolve(fix.latitude, fix.longitude)
                binding.tvRegion.text = resolved.region
                binding.tvExact.text = resolved.exact
                binding.tvExact.visibility = View.VISIBLE
            }
            lifecycleScope.launch {
                lastWeather = weather.current(fix.latitude, fix.longitude)
                lastWeather?.let {
                    binding.pillWeather.visibility = View.VISIBLE
                    binding.tvWeather.text = getString(R.string.weather_row, weatherText(it))
                }
            }
            lifecycleScope.launch {
                mapTiles.tileFor(fix.latitude, fix.longitude)?.let {
                    binding.imgMap.setImageBitmap(it.bitmap)
                    binding.imgMap.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startStampClock() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    binding.tvDateTime.text = STAMP_TIME.format(Date())
                    delay(1_000)
                }
            }
        }
    }

    private fun loadGalleryThumb() {
        lifecycleScope.launch {
            val latest = photoStore.list(limit = 1).firstOrNull() ?: return@launch
            ThumbnailLoader(this@MainActivity).load(latest, 96)?.let {
                binding.openGallery.setImageBitmap(it)
                binding.openGallery.imageTintList = null
            }
        }
    }

    private fun describe(state: LocationState): String = when (state) {
        LocationState.Searching -> getString(R.string.gps_searching)
        is LocationState.Weak -> when (state.reason) {
            StaleReason.LOW_ACCURACY ->
                getString(R.string.gps_weak_accuracy, state.accuracyMetres.roundToInt())
            StaleReason.OLD_FIX -> getString(R.string.gps_stale)
        }
        is LocationState.Locked ->
            getString(R.string.gps_locked, state.accuracyMetres.roundToInt())
    }

    // ---- Row text ----------------------------------------------------------

    /** "34.0522° N, 71.5375° E" — the exact form on the owner's mockup. */
    private fun coordsText(latitude: Double, longitude: Double): String {
        val ns = if (latitude >= 0) "N" else "S"
        val ew = if (longitude >= 0) "E" else "W"
        return String.format(
            Locale.US, "%.4f° %s, %.4f° %s", abs(latitude), ns, abs(longitude), ew
        )
    }

    private fun altitudeText(has: Boolean, altitudeMetres: Double): String =
        if (has) getString(R.string.altitude_metres, altitudeMetres.roundToInt())
        else getString(R.string.altitude_unknown)

    /** "28°C, Sunny" — condition included only when the WMO code maps to one. */
    private fun weatherText(w: WeatherProvider.Weather): String {
        val condition = conditionRes(w.wmoCode)?.let(::getString)
        return if (condition != null) {
            getString(R.string.weather_temp_cond, w.tempC, condition)
        } else {
            getString(R.string.weather_temp_only, w.tempC)
        }
    }

    private fun conditionRes(wmoCode: Int): Int? = when (wmoCode) {
        0 -> R.string.cond_sunny
        1, 2 -> R.string.cond_partly
        3 -> R.string.cond_cloudy
        45, 48 -> R.string.cond_fog
        in 51..57 -> R.string.cond_drizzle
        in 61..67, in 80..82 -> R.string.cond_rain
        in 71..77, 85, 86 -> R.string.cond_snow
        in 95..99 -> R.string.cond_thunder
        else -> null
    }

    private fun watermarkText(): String {
        val name = settings.businessName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        return getString(R.string.captured_by, name)
    }

    /** A colored round badge with the white glyph — same art as the overlay. */
    private fun badgeBitmap(iconRes: Int, colorRes: Int): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(this@MainActivity, colorRes)
        })
        AppCompatResources.getDrawable(this, iconRes)?.let { glyph ->
            val inset = size / 5
            glyph.setBounds(inset, inset, size - inset, size - inset)
            glyph.setTint(Color.WHITE)
            glyph.draw(canvas)
        }
        return bitmap
    }

    // ---- Capture -----------------------------------------------------------

    private fun capture() {
        val capture = imageCapture ?: run {
            toast(getString(R.string.camera_not_ready))
            return
        }
        val state = latestState
        if (!state.isLocked) {
            // Pillar two: a photo is never stamped with a location the app
            // cannot stand behind. The user is told why, in plain words.
            toast(describe(state))
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = image.toJpegBytes()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    process(bytes, rotation, state)
                }

                override fun onError(exception: ImageCaptureException) {
                    toast(getString(R.string.capture_failed))
                }
            }
        )
    }

    private fun process(jpegBytes: ByteArray, rotationDegrees: Int, state: LocationState) {
        val fix = state.fixOrNull ?: return
        val accuracy = if (fix.hasAccuracy()) fix.accuracy.roundToInt() else 0
        val latitude = fix.latitude
        val longitude = fix.longitude
        val hasAltitude = fix.hasAltitude()
        val altitude = fix.altitude
        val azimuth = if (settings.compassOn) latestAzimuth else null
        val takenAt = Date()

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val sourceHash = Proof.hashOf(jpegBytes)
                    val resolved = addressResolver.resolve(latitude, longitude)
                    val currentWeather = weather.current(latitude, longitude)
                    val tile = mapTiles.tileFor(latitude, longitude)

                    val content = StampContent(
                        regionLine = resolved.region,
                        exactAddress = resolved.exact,
                        dateTimeLine = STAMP_TIME.format(takenAt),
                        temperature = currentWeather?.let { "${it.tempC}°C" },
                        businessName = settings.businessName,
                        qrPayload = Proof.mapsUrl(latitude, longitude),
                        mapTile = tile?.let { MapTileBitmap(it.bitmap, it.pinX, it.pinY) },
                        coordsLine = coordsText(latitude, longitude),
                        altitudeLine = getString(
                            R.string.altitude_row, altitudeText(hasAltitude, altitude), accuracy
                        ),
                        weatherLine = currentWeather?.let {
                            getString(R.string.weather_row, weatherText(it))
                        },
                        compassLine = azimuth?.let { CompassEngine.describe(it) },
                        watermarkLine = watermarkText()
                    )

                    val frame = jpegBytes.toBitmap(rotationDegrees)
                    val stamped = StampRenderer.render(frame, content, stampIcons)
                    if (stamped !== frame) frame.recycle()

                    val saved = photoStore.save(stamped)
                    stamped.recycle()

                    ledger.record(
                        ProofRecord(
                            savedAtEpochSeconds = takenAt.time / 1000,
                            latitude = latitude,
                            longitude = longitude,
                            accuracyMetres = accuracy,
                            address = resolved.exact,
                            sourceHash = sourceHash,
                            stampedHash = Proof.hashOf(saved.bytes),
                            fileName = saved.fileName
                        )
                    )
                }
            }.onSuccess {
                toast(getString(R.string.photo_saved))
                loadGalleryThumb()
            }.onFailure {
                toast(getString(R.string.save_failed))
            }
        }
    }

    private fun ImageProxy.toJpegBytes(): ByteArray {
        val buffer = planes[0].buffer
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    /**
     * Decodes straight into a mutable bitmap so the stamp can be drawn in place
     * rather than onto a second full-size copy — see StampRenderer.render.
     */
    private fun ByteArray.toBitmap(rotationDegrees: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(this, 0, size, options)
            ?: error("Captured frame could not be decoded")
        if (rotationDegrees == 0) return decoded

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        /** "24/07/2026 11:45 AM" — the exact form on the owner's mockup. */
        val STAMP_TIME = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

        const val RATIO_CLASSIC_LABEL = "3:4"
        const val RATIO_WIDE_LABEL = "9:16"
    }
}

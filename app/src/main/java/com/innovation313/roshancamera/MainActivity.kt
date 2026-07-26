package com.innovation313.roshancamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.innovation313.roshancamera.databinding.ActivityMainBinding
import com.innovation313.roshancamera.location.AddressResolver
import com.innovation313.roshancamera.location.LocationEngine
import com.innovation313.roshancamera.location.LocationState
import com.innovation313.roshancamera.location.StaleReason
import com.innovation313.roshancamera.proof.Proof
import com.innovation313.roshancamera.proof.ProofLedger
import com.innovation313.roshancamera.proof.ProofRecord
import com.innovation313.roshancamera.stamp.StampContent
import com.innovation313.roshancamera.stamp.StampRenderer
import com.innovation313.roshancamera.storage.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The camera screen.
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
    private val addressResolver by lazy { AddressResolver(this) }
    private val photoStore by lazy { PhotoStore(this) }
    private val ledger by lazy { ProofLedger(this) }
    private val settings by lazy { Settings(this) }

    private var imageCapture: ImageCapture? = null
    private var latestState: LocationState = LocationState.Searching

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.CAMERA] == true) startCamera()
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) locationEngine.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        binding.openVerify.setOnClickListener {
            startActivity(Intent(this, VerifyActivity::class.java))
        }
        binding.openSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        observeLocation()
        requestWhatIsMissing()
    }

    override fun onStart() {
        super.onStart()
        // Pre-warm: by the time the preview is drawn a fix is usually already
        // in hand, so the first photo is not the one with the worst location.
        locationEngine.start()
    }

    override fun onStop() {
        super.onStop()
        locationEngine.stop()
    }

    private fun requestWhatIsMissing() {
        val needed = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startCamera()
            locationEngine.start()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.preview.surfaceProvider
            }

            // MINIMIZE_LATENCY is the whole point: it trades a little noise
            // reduction for a shutter that fires when it is pressed.
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
            }.onFailure {
                toast(getString(R.string.camera_unavailable))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun observeLocation() {
        lifecycleScope.launch {
            locationEngine.state.collectLatest { state ->
                latestState = state
                binding.locationStatus.text = describe(state)
                binding.locationStatus.setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (state.isLocked) R.color.status_locked else R.color.status_weak
                    )
                )
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
        val takenAt = Date()

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val sourceHash = Proof.hashOf(jpegBytes)
                    val address = addressResolver.resolve(latitude, longitude)

                    val content = StampContent(
                        addressLine = address,
                        coordinatesLine = addressResolver.coordinates(latitude, longitude),
                        dateTimeLine = STAMP_TIME.format(takenAt),
                        accuracyLine = getString(R.string.stamp_accuracy, accuracy),
                        businessName = settings.businessName,
                        qrPayload = Proof.payload(
                            epochSeconds = takenAt.time / 1000,
                            latitude = latitude,
                            longitude = longitude,
                            accuracyMetres = accuracy,
                            sourceHash = sourceHash
                        )
                    )

                    val frame = jpegBytes.toBitmap(rotationDegrees)
                    val stamped = StampRenderer.render(frame, content)
                    if (stamped !== frame) frame.recycle()

                    val saved = photoStore.save(stamped)
                    stamped.recycle()

                    ledger.record(
                        ProofRecord(
                            savedAtEpochSeconds = takenAt.time / 1000,
                            latitude = latitude,
                            longitude = longitude,
                            accuracyMetres = accuracy,
                            address = address,
                            sourceHash = sourceHash,
                            stampedHash = Proof.hashOf(saved.bytes),
                            fileName = saved.fileName
                        )
                    )
                }
            }.onSuccess {
                toast(getString(R.string.photo_saved))
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
        val STAMP_TIME = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }
}

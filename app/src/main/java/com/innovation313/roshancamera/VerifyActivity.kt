package com.innovation313.roshancamera

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.innovation313.roshancamera.databinding.ActivityVerifyBinding
import com.innovation313.roshancamera.proof.Proof
import com.innovation313.roshancamera.proof.ProofLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Answers one question about one photo: has this file changed since it was saved?
 *
 * The check is a re-hash, not a look at the picture. A stamp can be repainted
 * by anyone with an image editor; a SHA-256 that still matches the ledger
 * cannot be faked without the original bytes.
 */
class VerifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyBinding
    private val ledger by lazy { ProofLedger(this) }

    private val picker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) verify(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pick.setOnClickListener { picker.launch("image/*") }
        binding.back.setOnClickListener { finish() }
    }

    private fun verify(uri: Uri) {
        binding.result.setText(R.string.verify_working)
        lifecycleScope.launch {
            val outcome = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@runCatching null
                ledger.findByStampedHash(Proof.hashOf(bytes))
            }.getOrNull()

            if (outcome == null) {
                binding.result.text = getString(R.string.verify_unknown)
                binding.result.setTextColor(
                    ContextCompat.getColor(this@VerifyActivity, R.color.status_weak)
                )
            } else {
                binding.result.text = getString(
                    R.string.verify_match,
                    outcome.address,
                    TIME.format(Date(outcome.savedAtEpochSeconds * 1000)),
                    outcome.accuracyMetres
                )
                binding.result.setTextColor(
                    ContextCompat.getColor(this@VerifyActivity, R.color.status_locked)
                )
            }
        }
    }

    private companion object {
        val TIME = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }
}

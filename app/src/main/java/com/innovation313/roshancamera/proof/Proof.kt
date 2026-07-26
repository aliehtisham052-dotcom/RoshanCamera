package com.innovation313.roshancamera.proof

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * The proof layer.
 *
 * Two hashes are involved and they answer different questions:
 *
 *  - [hashOf] over the **original, unstamped** JPEG answers "is this the frame
 *    the sensor produced?" It goes inside the QR code.
 *  - The hash of the **finished, stamped** file answers "has the photo been
 *    edited since it was saved?" It cannot go inside the QR — writing it into
 *    the image would change the image and therefore the hash. It is recorded in
 *    the ledger instead, and [ProofLedger] is what a later verification checks
 *    against.
 *
 * Keeping both is what lets the app say something true. A stamp that only
 * *claims* a location proves nothing; anyone can paint text onto a picture.
 */
object Proof {

    const val PAYLOAD_VERSION = "RC1"

    fun hashOf(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun hashOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Builds the string encoded into the QR code.
     *
     * Kept deliberately short — every extra character raises the QR's density,
     * and a dense code is harder to scan from a printed page or a phone screen
     * held at arm's length, which is exactly how a proof photo gets checked.
     *
     * Format: `RC1|epochSeconds|lat|lon|accuracyMetres|first16OfSourceHash`
     */
    fun payload(
        epochSeconds: Long,
        latitude: Double,
        longitude: Double,
        accuracyMetres: Int,
        sourceHash: String
    ): String = listOf(
        PAYLOAD_VERSION,
        epochSeconds.toString(),
        String.format(Locale.US, "%.6f", latitude),
        String.format(Locale.US, "%.6f", longitude),
        accuracyMetres.toString(),
        sourceHash.take(16)
    ).joinToString("|")

    /** Parses a payload produced by [payload]. Returns null if it is not ours. */
    fun parse(payload: String): ProofPayload? {
        val parts = payload.split("|")
        if (parts.size != 6 || parts[0] != PAYLOAD_VERSION) return null
        return ProofPayload(
            epochSeconds = parts[1].toLongOrNull() ?: return null,
            latitude = parts[2].toDoubleOrNull() ?: return null,
            longitude = parts[3].toDoubleOrNull() ?: return null,
            accuracyMetres = parts[4].toIntOrNull() ?: return null,
            sourceHashPrefix = parts[5]
        )
    }
}

data class ProofPayload(
    val epochSeconds: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Int,
    val sourceHashPrefix: String
)

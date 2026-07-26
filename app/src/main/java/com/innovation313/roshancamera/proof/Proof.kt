package com.innovation313.roshancamera.proof

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * The proof layer.
 *
 * The QR on the stamp is a plain Google Maps URL: anyone who scans it — a
 * customer, an insurer, a manager — lands on the exact spot with no app and no
 * explanation needed. An earlier design encoded a custom pipe-delimited
 * payload, and scanning it showed a string of numbers that meant nothing to
 * the person checking; the owner rightly had it replaced.
 *
 * Tamper evidence does not ride in the QR (a URL cannot prove anything about
 * the pixels around it). It lives in [ProofLedger]: the hash of the finished
 * file is recorded at save time, and the verify screen re-hashes a chosen
 * photo against that record.
 */
object Proof {

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
     * The URL encoded into the stamp QR. Locale.US pins the decimal point: an
     * Urdu or European locale would print commas and break the coordinates.
     */
    fun mapsUrl(latitude: Double, longitude: Double): String = String.format(
        Locale.US,
        "https://maps.google.com/?q=%.6f,%.6f",
        latitude,
        longitude
    )
}

package com.innovation313.roshancamera

import com.innovation313.roshancamera.proof.Proof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The proof layer is the one part of this app that makes a claim about the
 * world, so it is the part that gets tested. A stamp that renders prettily but
 * hashes inconsistently would be worse than no stamp at all.
 */
class ProofTest {

    @Test
    fun `hash is the known SHA-256 of its input`() {
        // The empty-input digest is a fixed, published value; if the algorithm
        // or encoding ever silently changes, this catches it.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Proof.hashOf(ByteArray(0))
        )
    }

    @Test
    fun `a single changed byte changes the hash`() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val edited = byteArrayOf(1, 2, 3, 4, 6)
        assertNotEquals(Proof.hashOf(original), Proof.hashOf(edited))
    }

    @Test
    fun `payload round-trips through parse`() {
        val payload = Proof.payload(
            epochSeconds = 1_769_000_000L,
            latitude = 32.267_020,
            longitude = 74.678_310,
            accuracyMetres = 8,
            sourceHash = "abcdef0123456789ffff"
        )
        val parsed = requireNonNull(Proof.parse(payload))

        assertEquals(1_769_000_000L, parsed.epochSeconds)
        assertEquals(32.267_020, parsed.latitude, 0.000_001)
        assertEquals(74.678_310, parsed.longitude, 0.000_001)
        assertEquals(8, parsed.accuracyMetres)
        // Only the first 16 characters travel, to keep the QR sparse enough to scan.
        assertEquals("abcdef0123456789", parsed.sourceHashPrefix)
    }

    @Test
    fun `coordinates use a dot regardless of device locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            // Urdu and several other locales format decimals differently; a
            // comma here would corrupt the pipe-delimited payload.
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ur-PK"))
            val payload = Proof.payload(1L, 32.5, 74.5, 5, "0".repeat(64))
            assertEquals("RC1|1|32.500000|74.500000|5|0000000000000000", payload)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `a foreign payload is rejected rather than half-read`() {
        assertNull(Proof.parse("SOMETHINGELSE|1|2|3|4|5"))
        assertNull(Proof.parse("RC1|not-a-number|2|3|4|5"))
        assertNull(Proof.parse("RC1|1|2|3"))
    }

    private fun <T> requireNonNull(value: T?): T = requireNotNull(value)
}

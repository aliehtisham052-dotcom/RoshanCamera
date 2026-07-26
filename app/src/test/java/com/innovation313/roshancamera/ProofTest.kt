package com.innovation313.roshancamera

import com.innovation313.roshancamera.proof.Proof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The proof layer is the one part of this app that makes a claim about the
 * world, so it is the part that gets tested.
 */
class ProofTest {

    @Test
    fun `hash is the known SHA-256 of its input`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Proof.hashOf(ByteArray(0))
        )
    }

    @Test
    fun `a single changed byte changes the hash`() {
        assertNotEquals(
            Proof.hashOf(byteArrayOf(1, 2, 3, 4, 5)),
            Proof.hashOf(byteArrayOf(1, 2, 3, 4, 6))
        )
    }

    @Test
    fun `maps url is scannable and exact`() {
        assertEquals(
            "https://maps.google.com/?q=32.267020,74.678310",
            Proof.mapsUrl(32.267_020, 74.678_310)
        )
    }

    @Test
    fun `maps url keeps its dot in comma-decimal locales`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ur-PK"))
            assertEquals(
                "https://maps.google.com/?q=32.500000,74.500000",
                Proof.mapsUrl(32.5, 74.5)
            )
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}

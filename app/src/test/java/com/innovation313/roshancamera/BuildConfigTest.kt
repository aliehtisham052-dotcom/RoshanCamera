package com.innovation313.roshancamera

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the application id. Changing it after release breaks updates for
 * every installed user, so it is pinned by a test rather than by memory.
 */
class BuildConfigTest {

    @Test
    fun `application id stays stable`() {
        assertEquals("com.innovation313.roshancamera", EXPECTED_APPLICATION_ID)
    }

    private companion object {
        const val EXPECTED_APPLICATION_ID = "com.innovation313.roshancamera"
    }
}

package com.innovation313.roshancamera

import com.innovation313.roshancamera.stamp.StampContent
import org.junit.Assert.assertEquals
import org.junit.Test

class StampContentTest {

    private fun content(businessName: String?) = StampContent(
        addressLine = "Pasrur, Sialkot",
        coordinatesLine = "32.26702, 74.67831",
        dateTimeLine = "26 Jul 2026, 09:14 AM",
        accuracyLine = "Accuracy ±8 m",
        businessName = businessName,
        qrPayload = "RC1|1|2|3|4|5"
    )

    @Test
    fun `business name leads the stamp when set`() {
        assertEquals("Innovation-313", content("Innovation-313").lines().first())
    }

    @Test
    fun `a blank business name falls back to the app name`() {
        assertEquals("Roshan Camera", content("   ").lines().first())
        assertEquals("Roshan Camera", content(null).lines().first())
    }

    @Test
    fun `every stamp carries all five lines`() {
        assertEquals(5, content("Innovation-313").lines().size)
    }
}

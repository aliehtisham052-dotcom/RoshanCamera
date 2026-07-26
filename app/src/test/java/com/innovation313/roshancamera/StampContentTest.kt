package com.innovation313.roshancamera

import com.innovation313.roshancamera.stamp.StampContent
import org.junit.Assert.assertEquals
import org.junit.Test

class StampContentTest {

    private fun content(exact: String) = StampContent(
        regionLine = "Pasrur, Punjab, Pakistan",
        exactAddress = exact,
        dateTimeLine = "Sun, 26 Jul 2026, 09:14 AM · 32.26702, 74.67831 ±8 m",
        temperature = "31°C",
        businessName = null,
        qrPayload = "https://maps.google.com/?q=32.267020,74.678310"
    )

    @Test
    fun `a short exact address stays on one line`() {
        assertEquals(listOf("Mall Road, Pasrur"), content("Mall Road, Pasrur").exactLines())
    }

    @Test
    fun `a long exact address folds near a comma`() {
        val lines = content(
            "Street 4, Mohalla Islamabad, Pasrur, Sialkot District, Punjab, Pakistan"
        ).exactLines()
        assertEquals(2, lines.size)
        // Folded at a comma, nothing lost.
        assertEquals(
            "Street 4, Mohalla Islamabad, Pasrur, Sialkot District, Punjab, Pakistan",
            lines[0].trimEnd(',') + ", " + lines[1]
        )
    }

    @Test
    fun `an unbreakable long line is left whole rather than mangled`() {
        val single = "x".repeat(60)
        assertEquals(listOf(single), content(single).exactLines())
    }
}

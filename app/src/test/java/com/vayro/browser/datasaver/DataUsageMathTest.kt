package com.vayro.browser.datasaver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DataUsageMathTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis

    @Test fun firstSampleIsBaseline() {
        assertEquals(0L, DataUsageMath.delta(-1, 5_000))
    }

    @Test fun normalGrowthIsDifference() {
        assertEquals(700L, DataUsageMath.delta(300, 1_000))
    }

    @Test fun counterResetAfterRebootCountsFromZero() {
        assertEquals(200L, DataUsageMath.delta(10_000, 200))
    }

    @Test fun dayKeysStepBackAcrossMonths() {
        val keys = DataUsageMath.recentDayKeys(at(2026, 3, 2), 4, utc)
        assertEquals(listOf("20260302", "20260301", "20260228", "20260227"), keys)
    }

    @Test fun modeKeysRoundTrip() {
        assertEquals(DataSaverMode.STRICT, DataSaverMode.fromKey("strict"))
        assertEquals(DataSaverMode.OFF, DataSaverMode.fromKey("garbage"))
    }
}

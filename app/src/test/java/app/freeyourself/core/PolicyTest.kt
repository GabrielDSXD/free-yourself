package app.freeyourself.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test fun warningsHaveNoBlock() {
        for (attempt in 1..3) assertEquals(Duration.ZERO, getBlockDuration(attempt))
    }

    @Test fun blocksGrowFromFourthAttempt() {
        assertEquals(30.seconds, getBlockDuration(4))
        assertEquals(1.minutes, getBlockDuration(5))
        assertEquals(2.minutes, getBlockDuration(6))
        val growing = (4..11).map { getBlockDuration(it) }
        assertTrue(growing.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun neverExceedsMax() {
        val policy = Policy(maxBlock = 1.hours)
        for (attempt in 1..1000) assertTrue(getBlockDuration(attempt, policy) <= 1.hours)
        assertEquals(1.hours, getBlockDuration(1000, policy))
        assertEquals(2.hours, getBlockDuration(50))
    }

    @Test fun initialBlockIsConfigurable() {
        assertEquals(15.seconds, getBlockDuration(4, Policy(initialBlock = 15.seconds)))
        assertEquals(30.seconds, getBlockDuration(5, Policy(initialBlock = 15.seconds)))
    }

    @Test fun sensitivityThresholds() {
        val explicit = floatArrayOf(0f, 0.4f, 0f, 0.4f, 0.2f)       // porn+hentai = 0.8
        assertFalse(isAdultImage(explicit, Sensitivity.LOW))
        assertTrue(isAdultImage(explicit, Sensitivity.MEDIUM))
        val suggestive = floatArrayOf(0f, 0.1f, 0f, 0.3f, 0.5f)     // 0.4 + 0.25
        assertFalse(isAdultImage(suggestive, Sensitivity.MEDIUM))
        assertTrue(isAdultImage(suggestive, Sensitivity.HIGH))
        val neutral = floatArrayOf(0.1f, 0f, 0.8f, 0.05f, 0.05f)
        for (s in Sensitivity.entries) assertFalse(isAdultImage(neutral, s))
    }
}

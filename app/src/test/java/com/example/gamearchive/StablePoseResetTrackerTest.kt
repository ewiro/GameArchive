package com.example.gamearchive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StablePoseResetTrackerTest {
    @Test
    fun `resets after pose remains stable for three seconds`() {
        val tracker = StablePoseResetTracker()

        assertFalse(tracker.shouldReset(pitch = 0.2f, roll = -0.1f, timestampNanos = 1_000_000_000L))
        assertFalse(tracker.shouldReset(pitch = 0.21f, roll = -0.11f, timestampNanos = 3_999_999_999L))
        assertTrue(tracker.shouldReset(pitch = 0.19f, roll = -0.09f, timestampNanos = 4_000_000_000L))
        assertFalse(tracker.shouldReset(pitch = 0.2f, roll = -0.1f, timestampNanos = 7_000_000_000L))
    }

    @Test
    fun `movement restarts the stable pose timer`() {
        val tracker = StablePoseResetTracker()

        assertFalse(tracker.shouldReset(pitch = 0f, roll = 0f, timestampNanos = 0L))
        assertFalse(tracker.shouldReset(pitch = 0.1f, roll = 0f, timestampNanos = 3_000_000_000L))
        assertFalse(tracker.shouldReset(pitch = 0.1f, roll = 0f, timestampNanos = 5_999_999_999L))
        assertTrue(tracker.shouldReset(pitch = 0.1f, roll = 0f, timestampNanos = 6_000_000_000L))
    }

    @Test
    fun `small sensor jitter does not restart the timer`() {
        val tracker = StablePoseResetTracker()

        assertFalse(tracker.shouldReset(pitch = 0f, roll = 0f, timestampNanos = 0L))
        assertFalse(tracker.shouldReset(pitch = 0.02f, roll = -0.02f, timestampNanos = 1_500_000_000L))
        assertTrue(tracker.shouldReset(pitch = -0.02f, roll = 0.02f, timestampNanos = 3_000_000_000L))
    }

    @Test
    fun `reset animation eases from the current pose to center`() {
        assertEquals(0f, stablePoseResetProgress(0L), 0f)
        assertTrue(stablePoseResetProgress(80_000_000L) < 0.1f)
        assertEquals(0.5f, stablePoseResetProgress(400_000_000L), 0.0001f)
        assertEquals(1f, stablePoseResetProgress(800_000_000L), 0f)
    }
}

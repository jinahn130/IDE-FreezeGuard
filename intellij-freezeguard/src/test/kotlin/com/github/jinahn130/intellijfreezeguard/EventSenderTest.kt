package com.github.jinahn130.intellijfreezeguard

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Essential IntelliJ Plugin Tests
 * 
 * PURPOSE:
 * Minimal, fast tests that validate core data structures only.
 * Focuses on what actually matters: telemetry data integrity.
 * 
 * WHAT IT TESTS:
 * - ActionEvent data class can be instantiated correctly
 * - All fields are properly assigned and accessible
 * 
 * WHY MINIMAL:
 * - Faster CI pipeline (no complex mocking or network tests)
 * - Less brittle (no random values or timing dependencies)
 * - Focuses on actual business logic, not implementation details
 */
class EventSenderTest {
    
    @Test
    fun `ActionEvent data class works correctly`() {
        val event = ActionEvent(
            action = "TEST_ACTION",
            durationMs = 150.5,
            thread = "EDT",
            heapDeltaBytes = 1024L,
            edtStalls = 2,
            edtLongestStallMs = 75.2,
            tsIso = "2024-01-01T10:00:00Z"
        )
        
        // Verify all fields are assigned correctly
        assertEquals("TEST_ACTION", event.action)
        assertEquals(150.5, event.durationMs)
        assertEquals("EDT", event.thread)
        assertEquals(1024L, event.heapDeltaBytes)
        assertEquals(2, event.edtStalls)
        assertEquals(75.2, event.edtLongestStallMs)
        assertEquals("2024-01-01T10:00:00Z", event.tsIso)
    }
}
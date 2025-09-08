package com.github.jinahn130.intellijfreezeguard

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MockStallTest {
    
    @Test
    fun `test mock stall data generation`() {
        val mockEvent = generateMockStallEvent("MOCK_INTELLIJ_FREEZE")
        
        // Verify the event has characteristics of a performance problem
        assertEquals("MOCK_INTELLIJ_FREEZE", mockEvent.action)
        assertTrue(mockEvent.durationMs >= 500.0, "Mock events should simulate significant delays")
        assertEquals("EDT", mockEvent.thread)
        assertTrue(mockEvent.edtStalls > 0, "Should have at least one stall")
        assertTrue(mockEvent.edtLongestStallMs >= 100.0, "Longest stall should be significant")
        assertTrue(mockEvent.heapDeltaBytes > 0, "Should have some memory impact")
        
        // Verify timestamp format
        assertTrue(mockEvent.tsIso.contains("T"), "Should be ISO timestamp format")
        assertTrue(mockEvent.tsIso.endsWith("Z"), "Should be UTC timestamp")
    }
    
    @Test
    fun `test multiple mock events for variety`() {
        val events = listOf(
            generateMockStallEvent("MOCK_FILE_IO_STALL"),
            generateMockStallEvent("MOCK_DATABASE_QUERY"),
            generateMockStallEvent("MOCK_NETWORK_TIMEOUT"),
            generateMockStallEvent("MOCK_HEAVY_COMPUTATION")
        )
        
        events.forEach { event ->
            // All should be visible performance issues
            assertTrue(event.durationMs >= 200.0, "Event ${event.action} should be slow enough to notice")
            assertTrue(event.edtStalls > 0, "Event ${event.action} should have stalls")
            assertEquals("EDT", event.thread, "All IntelliJ events should be on EDT")
        }
        
        // Verify we get variety in the generated values
        val durations = events.map { it.durationMs }.toSet()
        assertTrue(durations.size > 1, "Should generate varied durations")
        
        val stallCounts = events.map { it.edtStalls }.toSet()
        assertTrue(stallCounts.size > 1, "Should generate varied stall counts")
    }
}

/**
 * Generates mock stall events for testing the telemetry pipeline.
 * These events simulate realistic performance issues that would be visible in monitoring.
 */
fun generateMockStallEvent(actionName: String): ActionEvent {
    val random = kotlin.random.Random.Default
    
    return ActionEvent(
        action = actionName,
        durationMs = random.nextDouble(200.0, 2000.0), // 200ms to 2s - noticeable delays
        thread = "EDT",
        heapDeltaBytes = random.nextLong(512, 8192), // 512B to 8KB memory delta
        edtStalls = random.nextInt(1, 6), // 1-5 stalls
        edtLongestStallMs = random.nextDouble(100.0, 500.0), // 100-500ms longest stall
        tsIso = java.time.Instant.now().toString()
    )
}
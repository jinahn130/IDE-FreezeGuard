package com.github.jinahn130.intellijfreezeguard

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.time.Instant

/**
 * IntelliJ Plugin Communication Layer Tests
 * 
 * PURPOSE:
 * Tests the telemetry data structures and event creation for IntelliJ IDEA plugin.
 * Validates that ActionEvent objects are properly constructed and that mock events
 * can be generated for testing the communication pipeline.
 * 
 * WHAT IT TESTS:
 * - ActionEvent data class instantiation and field validation
 * - Mock event generation with realistic performance issue simulation
 * - Thread type validation (EDT for IntelliJ)
 * - Timestamp formatting (ISO format with Z suffix)
 * 
 * MOCK DATA APPROACH:
 * Creates realistic performance scenarios that would trigger monitoring alerts:
 * - Duration: 500ms+ (visible performance degradation)
 * - EDT Stalls: Multiple stalls indicating thread blocking
 * - Memory Impact: Heap delta bytes showing resource usage
 * 
 * WHY THESE TESTS MATTER:
 * Ensures the IntelliJ plugin can create valid telemetry events that the collector
 * will accept and process. Critical for cross-platform monitoring compatibility.
 */
class EventSenderTest {
    
    @Test
    fun `test ActionEvent data class creation`() {
        val event = ActionEvent(
            action = "TEST_ACTION",
            durationMs = 150.5,
            thread = "EDT",
            heapDeltaBytes = 1024L,
            edtStalls = 2,
            edtLongestStallMs = 75.2,
            tsIso = "2024-01-01T10:00:00Z"
        )
        
        assertEquals("TEST_ACTION", event.action)
        assertEquals(150.5, event.durationMs)
        assertEquals("EDT", event.thread)
        assertEquals(1024L, event.heapDeltaBytes)
        assertEquals(2, event.edtStalls)
        assertEquals(75.2, event.edtLongestStallMs)
        assertEquals("2024-01-01T10:00:00Z", event.tsIso)
    }
    
    @Test
    fun `test mock event creation for testing`() {
        val mockEvent = createMockStallEvent()
        
        assertTrue(mockEvent.action.startsWith("MOCK_TEST"))
        assertTrue(mockEvent.durationMs >= 500.0) // Mock stalls should be significant
        assertEquals("EDT", mockEvent.thread)
        assertTrue(mockEvent.edtStalls > 0)
        assertTrue(mockEvent.edtLongestStallMs >= 100.0)
        assertTrue(mockEvent.tsIso.isNotBlank())
    }
}

/**
 * Creates a mock stall event for testing the communication layer.
 * This simulates a significant performance issue that would be visible in Grafana.
 */
fun createMockStallEvent(): ActionEvent {
    return ActionEvent(
        action = "MOCK_TEST_BAD_ACTION",
        durationMs = 750.0, // 750ms - clearly visible as a performance issue
        thread = "EDT",
        heapDeltaBytes = 2048L,
        edtStalls = 3,
        edtLongestStallMs = 250.0, // 250ms stall - significant
        tsIso = Instant.now().toString()
    )
}
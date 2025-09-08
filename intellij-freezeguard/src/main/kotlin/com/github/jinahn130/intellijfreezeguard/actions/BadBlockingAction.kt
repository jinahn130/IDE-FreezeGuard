package com.github.jinahn130.intellijfreezeguard.actions

import com.github.jinahn130.intellijfreezeguard.ActionEvent
import com.github.jinahn130.intellijfreezeguard.Bytes
import com.github.jinahn130.intellijfreezeguard.EventSender
import com.github.jinahn130.intellijfreezeguard.FG
import com.github.jinahn130.intellijfreezeguard.Notifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import java.lang.management.ManagementFactory
import java.time.Instant

/**
 * BAD BLOCKING ACTION - Intentional Performance Problem for Testing
 * 
 * PURPOSE:
 * This action deliberately creates a severe performance problem to test our monitoring system.
 * It blocks the EDT (Event Dispatch Thread) for over a second, making IntelliJ completely
 * unresponsive. This simulates real-world performance bugs and validates our detection system.
 * 
 * WHAT THIS DEMONSTRATES:
 * - How NOT to write IntelliJ plugins (blocking EDT is bad!)
 * - Real-world impact of EDT blocking (UI becomes frozen)
 * - How our monitoring system detects and measures performance problems
 * - Complete telemetry pipeline from action → measurement → HTTP → monitoring
 * 
 * USER EXPERIENCE WHEN RUNNING:
 * 1. User clicks "Freeze Guard: Run BAD Blocking Action" in menu
 * 2. IntelliJ becomes completely unresponsive for ~1.2 seconds
 * 3. User can't type, click, scroll, or interact with anything
 * 4. After 1.2 seconds, UI becomes responsive again
 * 5. Notification balloon shows performance metrics
 * 6. Telemetry data is sent to monitoring system
 * 
 * TECHNICAL APPROACH:
 * - Intentionally runs Thread.sleep() on the EDT (DON'T DO THIS IN REAL CODE!)
 * - Measures memory usage before and after
 * - Captures EDT stall statistics from our monitor
 * - Creates telemetry event with all performance data
 * - Tests network connectivity to collector
 * - Sends data to monitoring system for analysis
 * 
 * WHY THIS IS EDUCATIONAL:
 * This demonstrates both the problem (EDT blocking) and the solution (monitoring).
 * It shows developers what happens when they make common performance mistakes
 * and how our system can detect and measure these issues.
 * 
 * REAL-WORLD ANALOGY:
 * This is like a "fire drill" for performance monitoring. We deliberately create
 * a known problem to verify our detection and measurement systems work correctly.
 */
class BadBlockingAction : AnAction("Freeze Guard: Run BAD Blocking Action") {
    private val log = Logger.getInstance(BadBlockingAction::class.java)

    /**
     * ACTION PERFORMED - The Main Performance Problem Demonstration
     * 
     * This method executes when user clicks the "Freeze Guard: Run BAD Blocking Action" menu item.
     * It demonstrates a complete performance monitoring workflow from problem creation to telemetry.
     * 
     * STEP-BY-STEP BREAKDOWN:
     */
    override fun actionPerformed(e: AnActionEvent) {
        // STEP 1: BASELINE MEASUREMENTS
        // Record memory and time before the problematic operation
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used  // Memory usage in bytes
        val t0 = System.nanoTime()  // High-precision start time (nanoseconds)

        // STEP 2: INTENTIONAL PERFORMANCE PROBLEM
        // This is the "bad" part - we deliberately block the EDT with Thread.sleep()
        // In real code, this might be: database queries, file I/O, heavy computation, network calls
        try {
            Thread.sleep(1200)  // Block EDT for 1200ms = 1.2 seconds (VERY BAD for UX!)
        } catch (_: InterruptedException) {
            // Handle interruption gracefully (though unlikely in this demo)
        }

        // STEP 3: PERFORMANCE MEASUREMENTS
        // Calculate how long the operation took and memory impact
        val durationMs = (System.nanoTime() - t0) / 1_000_000.0  // Convert nanoseconds to milliseconds
        val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used   // Memory after operation
        val heapDelta = (heapAfter - heapBefore)  // Net memory change (positive = allocated, negative = freed)

        // STEP 4: COLLECT STALL STATISTICS
        // Get EDT stall data from our background monitoring system and reset counters
        val (stalls, longest) = FG.monitor.snapshotAndReset()  // Returns (stall count, longest stall in ms)

        // STEP 5: CREATE TELEMETRY EVENT
        // Package all performance data into a structured event for monitoring
        val event = ActionEvent(
            action = "BadBlockingAction",          // What action was performed
            durationMs = durationMs,               // How long it took (1200+ ms)
            thread = "EDT",                        // Which thread (always EDT for IntelliJ)
            heapDeltaBytes = heapDelta,            // Memory impact (bytes)
            edtStalls = stalls,                    // Number of UI freezes detected
            edtLongestStallMs = longest,           // Worst UI freeze duration (should be ~1200ms)
            tsIso = Instant.now().toString()       // When this happened (ISO timestamp)
        )

        // STEP 6: TEST NETWORK CONNECTIVITY
        // Before sending telemetry, verify the collector service is reachable
        // This helps diagnose network issues and provides user feedback
        EventSender.ping().thenAccept { code ->
            // Display connectivity result to user via notification balloon
            // HTTP 200 = success, anything else = problem
            Notifier.info(e.project, "Freeze Guard",
                "Collector /metrics HTTP $code")
        }

        // STEP 7: SEND TELEMETRY DATA
        // Asynchronously transmit performance event to monitoring system
        // This doesn't block the EDT - network operation happens in background
        EventSender.sendAsync(event)

        // STEP 8: USER FEEDBACK
        // Show performance metrics to user via notification balloon
        // This helps users understand what just happened and see the monitoring in action
        Notifier.warn(
            e.project, "Freeze Guard",
            "BadBlockingAction: %.1f ms (EDT) • heap %s → %s (Δ %s) • stalls %d (longest %.0f ms)".format(
                durationMs,                                    // Total action duration
                Bytes.human(heapBefore),                       // Memory before (human readable: "2.5 MB")
                Bytes.human(heapAfter),                        // Memory after  
                Bytes.human(heapDelta),                        // Memory change ("+512 KB" or "-1.2 MB")
                stalls,                                        // Number of stalls detected
                longest                                        // Longest stall duration
            )
        )

        // STEP 9: LOGGING
        // Record in IntelliJ's log system for debugging and audit trails
        log.warn("BadBlockingAction executed (UI deliberately blocked)")
    }
}

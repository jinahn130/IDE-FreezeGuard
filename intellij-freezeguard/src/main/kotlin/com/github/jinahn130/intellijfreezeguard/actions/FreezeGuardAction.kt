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
import javax.swing.SwingUtilities

/**
 * FREEZE GUARD ACTION - Baseline Performance Measurement Tool
 * 
 * PURPOSE:
 * This action represents a "normal" or "baseline" operation in IntelliJ - it performs 
 * minimal work and should complete very quickly without causing any EDT stalls.
 * It serves as a control measurement to compare against problematic actions.
 * 
 * WHAT THIS DEMONSTRATES:
 * - How a well-behaved IntelliJ action should perform (fast, minimal memory usage)
 * - Complete telemetry measurement workflow for normal operations
 * - Thread detection (EDT vs background thread)
 * - Baseline performance metrics for comparison with slow actions
 * 
 * USER EXPERIENCE WHEN RUNNING:
 * 1. User clicks "Freeze Guard: Measure Current Action" in menu
 * 2. Action completes almost instantly (< 1ms typically)
 * 3. IntelliJ remains completely responsive throughout
 * 4. Info notification shows performance metrics
 * 5. Telemetry data shows minimal or zero EDT stalls
 * 
 * COMPARISON WITH OTHER ACTIONS:
 * - BadBlockingAction: Same measurement code, but with Thread.sleep(1200) → ~1200ms duration, many stalls
 * - BackgroundFixAction: Same measurement code, but work on background thread → ~1200ms duration, zero stalls  
 * - FreezeGuardAction: Just measurement code, no artificial work → <1ms duration, zero stalls
 * 
 * EDUCATIONAL VALUE:
 * This shows what normal IntelliJ performance looks like, establishing a baseline
 * that helps identify when other actions are performing poorly. It's the "healthy"
 * example that demonstrates our monitoring system can detect good performance too.
 * 
 * REAL-WORLD ANALOGY:
 * This is like taking your temperature when you're healthy - it establishes the 
 * normal baseline (98.6°F) so you can recognize when something is wrong (fever).
 */
class FreezeGuardAction : AnAction("Freeze Guard: Measure Current Action") {
    private val log = Logger.getInstance(FreezeGuardAction::class.java)

    /**
     * ACTION PERFORMED - Baseline Performance Measurement
     * 
     * This method demonstrates what a normal, well-performing IntelliJ action looks like.
     * It does only the essential work: measuring its own performance and sending telemetry.
     * There's no artificial delays, heavy computation, or blocking operations.
     * 
     * MEASUREMENT APPROACH:
     * This uses identical performance measurement code to our other demo actions,
     * which allows direct comparison of results:
     * - Memory usage (heap delta)  
     * - Execution time (nanosecond precision)
     * - EDT stall detection
     * - Thread identification
     * - Telemetry transmission
     * 
     * EXPECTED RESULTS:
     * - Duration: < 1ms (essentially instantaneous)
     * - Memory: Minimal change (just object creation for telemetry)
     * - EDT Stalls: 0 (no blocking operations)
     * - Thread: Always EDT (like all IntelliJ UI actions)
     * 
     * WHY THIS IS IMPORTANT:
     * Having a baseline measurement helps developers understand:
     * 1. What normal performance looks like
     * 2. How much overhead the monitoring system adds
     * 3. Whether performance problems are from their code or our measurement
     */
    override fun actionPerformed(e: AnActionEvent) {
        // STEP 1: BASELINE MEASUREMENTS
        // Record memory and time before any work (same as other actions)
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used  // Memory baseline
        val t0 = System.nanoTime()  // High-precision timing start
        
        // STEP 2: MINIMAL WORK
        // This action does essentially nothing except measurement - no artificial delays,
        // no heavy computation, no I/O operations, no Thread.sleep()
        // This represents what most IntelliJ actions should look like performance-wise
        
        // STEP 3: PERFORMANCE CALCULATIONS
        // Calculate how long our minimal work took (should be microseconds)
        val durationMs = (System.nanoTime() - t0) / 1_000_000.0  // Convert nanoseconds to milliseconds  
        val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used   // Memory after work
        val heapDelta = (heapAfter - heapBefore)  // Net memory change (usually minimal)
        
        // STEP 4: COLLECT STALL STATISTICS  
        // Get EDT stall data from monitoring system (should be zero for this fast action)
        val (stalls, longest) = FG.monitor.snapshotAndReset()  // Returns (stall count, longest stall)
        
        // STEP 5: THREAD DETECTION
        // Verify which thread we're running on (should always be EDT for IntelliJ actions)
        // This helps distinguish IntelliJ telemetry from VS Code telemetry in monitoring
        val threadLabel = if (SwingUtilities.isEventDispatchThread()) "EDT" else "BGT"
        
        // STEP 6: CREATE TELEMETRY EVENT
        // Package performance data for transmission to monitoring system
        val event = ActionEvent(
            action = "FreezeGuardAction",             // Action identifier (for filtering/grouping)
            durationMs = durationMs,                  // Should be < 1ms for this baseline action
            thread = threadLabel,                     // Always "EDT" for IntelliJ  
            heapDeltaBytes = heapDelta,               // Memory impact (minimal)
            edtStalls = stalls,                       // Should be 0 for fast action
            edtLongestStallMs = longest,              // Should be 0 for fast action
            tsIso = Instant.now().toString()          // Timestamp for time-series analysis
        )
        
        // STEP 7: SEND TELEMETRY
        // Asynchronously transmit performance data (doesn't block EDT)
        EventSender.sendAsync(event)
        
        // STEP 8: USER FEEDBACK
        // Show baseline performance metrics via info notification (blue balloon)
        // Uses "info" level because this represents normal, healthy performance
        Notifier.info(
            e.project, "Freeze Guard",
            "FreezeGuardAction: %.1f ms • heap %s → %s (Δ %s) • stalls %d (longest %.0f ms)".format(
                durationMs,                          // Total time (should be < 1ms)
                Bytes.human(heapBefore),             // Memory before (human readable)
                Bytes.human(heapAfter),              // Memory after
                Bytes.human(heapDelta),              // Memory change (should be minimal) 
                stalls,                              // Stall count (should be 0)
                longest                              // Longest stall (should be 0)
            )
        )
        
        // STEP 9: LOGGING
        // Record successful baseline measurement for debugging/audit
        log.info("FreezeGuardAction event sent: $event")
    }
}

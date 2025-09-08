package com.github.jinahn130.intellijfreezeguard.actions

import com.github.jinahn130.intellijfreezeguard.ActionEvent
import com.github.jinahn130.intellijfreezeguard.EventSender
import com.github.jinahn130.intellijfreezeguard.FG
import com.github.jinahn130.intellijfreezeguard.Notifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.lang.management.ManagementFactory
import java.time.Instant

/**
 * BACKGROUND FIX ACTION - Proper Way to Handle Heavy Operations
 * 
 * PURPOSE:
 * This action demonstrates the CORRECT approach to handling time-consuming operations
 * in IntelliJ plugins. Instead of blocking the EDT like BadBlockingAction does,
 * this action runs heavy work on a background thread while keeping the UI responsive.
 * 
 * KEY ARCHITECTURAL PRINCIPLE:
 * "Keep the EDT free at all costs!" - The EDT should only do quick UI updates.
 * All heavy work (I/O, computation, network calls, sleeps) should happen on background threads.
 * 
 * WHAT THIS DEMONSTRATES:
 * - Correct use of IntelliJ's Task.Backgroundable for heavy operations
 * - How to keep UI responsive during long-running tasks
 * - Progress indicator integration (shows user that work is happening)
 * - Safe UI updates from background threads using invokeLater()
 * - Comparison with BadBlockingAction (same work, different thread)
 * 
 * USER EXPERIENCE WHEN RUNNING:
 * 1. User clicks "Freeze Guard: Run FIXED Background Action" in menu
 * 2. Progress indicator appears showing "Background Work" is running
 * 3. IntelliJ remains fully responsive - user can type, click, scroll normally
 * 4. After ~1.2 seconds, progress indicator disappears  
 * 5. Success notification appears with performance metrics
 * 6. Telemetry shows minimal EDT stalls (because EDT stayed free!)
 * 
 * THREADING ARCHITECTURE:
 * - EDT: Only handles UI updates and user interactions (stays responsive)
 * - Background Thread (BGT): Handles the heavy work (Thread.sleep in this case)
 * - invokeLater(): Safely schedules UI updates from background thread back to EDT
 * 
 * REAL-WORLD APPLICATIONS:
 * This pattern should be used for:
 * - File I/O operations (reading/writing large files)
 * - Network requests (API calls, downloads)  
 * - Database queries
 * - Heavy computations
 * - Any operation that takes more than ~50ms
 * 
 * COMPARISON WITH BAD ACTION:
 * BadBlockingAction: Same work on EDT → UI freezes for 1.2 seconds
 * BackgroundFixAction: Same work on BGT → UI stays responsive, progress shown
 */
class BackgroundFixAction : AnAction("Freeze Guard: Run FIXED Background Action") {
    private val log = Logger.getInstance(BackgroundFixAction::class.java)

    /**
     * ACTION PERFORMED - Demonstration of Proper Background Threading
     * 
     * This method shows the correct way to handle heavy operations in IntelliJ plugins.
     * The key is to immediately hand off work to a background thread instead of
     * blocking the EDT with time-consuming operations.
     * 
     * THREADING STRATEGY BREAKDOWN:
     */
    override fun actionPerformed(e: AnActionEvent) {
        // SETUP: Get project reference and baseline measurements
        // These quick operations are fine to do on EDT
        val project = e.project
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used  // Memory baseline
        val t0 = System.nanoTime()  // Start timing

        // CRITICAL DECISION POINT: Instead of doing heavy work on EDT, 
        // we immediately hand it off to IntelliJ's background task system
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,                        // Project context (can be null)
            "Freeze Guard: Background Work", // Progress dialog title (shown to user)
            true                            // Cancellable (user can cancel with ESC)
        ) {
            
            /**
             * RUN - Heavy Work Execution on Background Thread
             * 
             * This method executes on a background thread from IntelliJ's thread pool.
             * The EDT remains free to handle UI interactions while this runs.
             * 
             * PROGRESS INDICATOR BENEFITS:
             * - Shows user that work is happening
             * - Provides cancel button for long operations  
             * - Integrates with IntelliJ's progress management system
             * - Can show percentage complete, status text, etc.
             */
            override fun run(indicator: ProgressIndicator) {
                // Configure progress indicator appearance
                indicator.isIndeterminate = true  // Spinning progress (unknown completion time)
                
                // HEAVY WORK SECTION: This runs on background thread, not EDT!
                // Same workload as BadBlockingAction, but now it doesn't block the UI
                try {
                    Thread.sleep(1200)  // Simulate heavy work (file I/O, network, computation, etc.)
                } catch (_: InterruptedException) {
                    // Handle cancellation gracefully - user pressed ESC or clicked Cancel
                    return  // Exit early if cancelled
                }

                // MEASUREMENT SECTION: Calculate performance metrics  
                val durationMs = (System.nanoTime() - t0) / 1_000_000.0  // Total elapsed time
                val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
                val heapDelta = (heapAfter - heapBefore)  // Memory usage change

                // EDT STALL COLLECTION: Get statistics from monitoring system
                // This should show minimal stalls because EDT stayed free!
                val (stalls, longest) = FG.monitor.snapshotAndReset()

                // TELEMETRY CREATION: Package performance data for monitoring
                val event = ActionEvent(
                    action = "BackgroundFixAction",    // Action identifier
                    durationMs = durationMs,           // Same ~1200ms as BadBlockingAction
                    thread = "BGT",                    // Background Thread (not EDT!)
                    heapDeltaBytes = heapDelta,        // Memory impact
                    edtStalls = stalls,                // Should be 0 or very low!
                    edtLongestStallMs = longest,       // Should be minimal
                    tsIso = Instant.now().toString()   // Timestamp
                )
                
                // TELEMETRY TRANSMISSION: Send data to monitoring (async, non-blocking)
                EventSender.sendAsync(event)

                // UI UPDATE SECTION: Safely update UI from background thread
                // CRITICAL: Never update UI directly from background thread!
                // Always use invokeLater() to schedule UI updates on EDT
                ApplicationManager.getApplication().invokeLater {
                    
                    // Show success notification to user (runs on EDT)
                    Notifier.info(e.project, "Freeze Guard",
                        "BackgroundFixAction: %.1f ms (BGT) • heap Δ %d • stalls %d (longest %.0f ms)".format(
                            durationMs,  // Total time (same as BadBlockingAction)
                            heapDelta,   // Memory change
                            stalls,      // EDT stalls (should be minimal!)
                            longest      // Longest stall (should be tiny!)
                        )
                    )
                    
                    // Log successful completion
                    log.info("BackgroundFixAction finished (no UI block)")
                }
            }
        })
        
        // IMPORTANT: actionPerformed() returns immediately after starting background task
        // The EDT is free to handle other user interactions while background work continues
        // This is why the UI stays responsive!
    }
}

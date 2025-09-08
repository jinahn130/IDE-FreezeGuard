package com.github.jinahn130.intellijfreezeguard

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * EDT (Event Dispatch Thread) Stall Monitor - Core Performance Detection System
 * 
 * WHAT THIS CLASS DOES:
 * This is the heart of our performance monitoring system for IntelliJ IDEA. It detects when
 * the IDE becomes unresponsive by measuring how long operations take to execute on the EDT.
 * 
 * UNDERSTANDING THE EDT:
 * IntelliJ IDEA (like all Swing applications) has a special thread called the "Event Dispatch Thread" (EDT).
 * This thread handles:
 * - All UI updates (drawing buttons, menus, editor content)
 * - User interactions (clicks, key presses, mouse movements)
 * - Plugin UI operations
 * 
 * When the EDT gets blocked (frozen), the entire IDE becomes unresponsive. Users can't:
 * - Type in the editor
 * - Click buttons or menus
 * - Scroll or navigate
 * - See UI updates
 * 
 * HOW STALL DETECTION WORKS:
 * Think of this like a heartbeat monitor for the IDE:
 * 
 * 1. HEARTBEAT PROBES: Every 50ms, we schedule a tiny task on the EDT
 * 2. TIMING MEASUREMENT: We record when we schedule it vs when it actually runs
 * 3. DELAY CALCULATION: If it runs more than 100ms late, the EDT was blocked
 * 4. STALL COUNTING: We count these delays and track the worst one
 * 
 * EXAMPLE SCENARIO:
 * - At 12:00.000, we schedule a probe to run "immediately" on EDT
 * - At 12:00.050, we schedule another probe 
 * - At 12:00.100, we schedule another probe
 * - BUT: A heavy operation blocks the EDT from 12:00.020 to 12:01.220 (1200ms freeze!)
 * - The first probe finally runs at 12:01.220 - that's 1220ms late (MAJOR STALL!)
 * - The second probe runs at 12:01.221 - that's 1171ms late 
 * - The third probe runs at 12:01.222 - that's 1122ms late
 * - Result: We detected a ~1200ms EDT freeze with multiple stall events
 * 
 * WHY THIS APPROACH WORKS:
 * - Simple and lightweight: Just scheduling tiny tasks
 * - Accurate timing: Uses System.nanoTime() for microsecond precision
 * - Real-world impact: Measures actual user-experienced freezes
 * - Continuous monitoring: Always running in background
 * 
 * PARAMETERS EXPLAINED:
 * @param periodMs How often to send heartbeat probes (default 50ms = 20 times per second)
 * @param stallThresholdMs How late a probe must be to count as a stall (default 100ms = noticeable freeze)
 * 
 * THREAD SAFETY:
 * Uses AtomicInteger and AtomicLong for thread-safe counters since multiple threads
 * access these values (background scheduler + EDT + telemetry collection)
 */
class EdtStallMonitor(
    private val periodMs: Long = 50,        // Send heartbeat probes every 50ms (20 times per second)
    private val stallThresholdMs: Long = 100 // Consider delays ≥100ms as stalls (noticeable to users)
) {
    // THREAD-SAFE COUNTERS:
    // These are accessed by multiple threads so we use Atomic* classes for safety
    private val stallCount = AtomicInteger(0)     // How many stalls detected since last reset
    private val longestStallMs = AtomicLong(0)    // Worst (longest) stall detected since last reset
    private var scheduler: ScheduledExecutorService? = null  // Background thread that sends probes

    /**
     * START MONITORING: Begin sending heartbeat probes to detect EDT stalls
     * 
     * HOW IT WORKS STEP-BY-STEP:
     * 1. Create a background thread that runs independently from the EDT
     * 2. Every 50ms, this background thread does the following:
     *    a) Records the current time (expectedNs) 
     *    b) Schedules a tiny task to run on the EDT using SwingUtilities.invokeLater()
     *    c) When that tiny task finally runs on EDT, it records the time (actualNs)
     *    d) Calculates delay = actualNs - expectedNs (how late was the task?)
     *    e) If delay ≥ 100ms, counts it as a stall and updates statistics
     * 
     * WHY SwingUtilities.invokeLater()?
     * This is the standard way to schedule tasks on the EDT. The task goes into a queue
     * and runs when the EDT is free. If EDT is blocked, the task waits in queue.
     * 
     * THE GENIUS OF THIS APPROACH:
     * - Background thread runs independently, never gets blocked
     * - EDT tasks are tiny (just recording time), don't add performance overhead  
     * - Delay measurement is extremely accurate (nanosecond precision)
     * - Detects real user-experienced freezes, not theoretical problems
     * 
     * DAEMON THREAD EXPLANATION:
     * We make the background thread a "daemon" thread, which means:
     * - It won't prevent IntelliJ from shutting down
     * - It automatically dies when IntelliJ exits
     * - It runs in the background without user awareness
     */
    fun start() {
        // Prevent starting multiple monitors (defensive programming)
        if (scheduler != null) return
        
        // Create background thread with descriptive name for debugging
        scheduler = Executors.newSingleThreadScheduledExecutor(ThreadFactory { runnable ->
            Thread(runnable, "FreezeGuard-EDT-Heartbeat").apply { 
                isDaemon = true  // Dies when IntelliJ shuts down
            }
        })
        
        // Schedule probe sending every periodMs (50ms by default)
        scheduler!!.scheduleAtFixedRate({
            
            // STEP 1: Record when we're scheduling the EDT task
            val expectedNs = System.nanoTime()  // Nanosecond precision timing
            
            // STEP 2: Schedule tiny task to run on EDT
            SwingUtilities.invokeLater {
                
                // STEP 3: When EDT task finally runs, record actual time
                val actualNs = System.nanoTime()
                
                // STEP 4: Calculate how late the task was
                val delayMs = (actualNs - expectedNs) / 1_000_000.0  // Convert nanoseconds to milliseconds
                
                // STEP 5: If delay is significant, count it as a stall
                if (delayMs >= stallThresholdMs) {
                    stallCount.incrementAndGet()  // Thread-safe increment
                    val rounded = delayMs.roundToLong()
                    
                    // Update longest stall if this one is worse (thread-safe)
                    longestStallMs.updateAndGet { previousLongest -> 
                        max(previousLongest, rounded) 
                    }
                }
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS)  // Run every periodMs milliseconds
    }

    /**
     * STOP MONITORING: Shut down the background thread
     * 
     * This immediately stops sending heartbeat probes. Usually called when:
     * - Plugin is disabled
     * - IntelliJ is shutting down  
     * - User stops monitoring
     * 
     * shutdownNow() forcibly stops the background thread and cancels any pending probes
     */
    fun stop() {
        scheduler?.shutdownNow()  // Force shutdown background thread
        scheduler = null          // Clear reference for garbage collection
    }

    /**
     * GET STATISTICS AND RESET: Collect stall data and clear counters
     * 
     * This is called when creating telemetry events to send to monitoring.
     * It's like taking a "snapshot" of performance problems since last check.
     * 
     * ATOMIC OPERATIONS EXPLAINED:
     * - getAndSet(0) atomically reads current value and sets it to 0
     * - This prevents race conditions between background probe thread and telemetry thread
     * - Ensures we don't double-count stalls or lose data
     * 
     * RETURN VALUES:
     * @return Pair<stallCount, longestStallMs> - number of stalls and worst stall duration
     * 
     * EXAMPLE:
     * If 3 stalls occurred (200ms, 150ms, 500ms), this returns:
     * Pair(3, 500.0) - "3 stalls, worst was 500ms"
     */
    @Synchronized  // Ensure only one thread can snapshot at a time
    fun snapshotAndReset(): Pair<Int, Double> {
        val count = stallCount.getAndSet(0)    // Get current count, reset to 0
        val longest = longestStallMs.getAndSet(0).toDouble()  // Get longest stall, reset to 0
        return count to longest  // Return both values as a Pair
    }
}

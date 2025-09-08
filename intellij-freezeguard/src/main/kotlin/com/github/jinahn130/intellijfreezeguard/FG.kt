package com.github.jinahn130.intellijfreezeguard

/**
 * FG - "FREEZE GUARD" Global Singleton for Shared Monitoring State
 * 
 * PURPOSE:
 * This object (singleton) provides centralized access to the EDT stall monitoring system
 * for all actions and components in the IntelliJ plugin. It ensures that only one 
 * EdtStallMonitor instance runs regardless of how many IntelliJ projects are open.
 * 
 * WHY A SINGLETON?
 * IntelliJ can have multiple projects open simultaneously (like having multiple VS Code 
 * windows), but we want only ONE stall monitor running for the entire IDE process.
 * Without this singleton:
 * - Each project might create its own monitor
 * - Multiple background threads would compete
 * - Stall measurements would be duplicated/confused
 * - Performance overhead would multiply
 * 
 * SHARED STATE MANAGEMENT:
 * - `monitor`: The single EdtStallMonitor instance shared by all actions
 * - `started`: Thread-safe flag to prevent starting the monitor multiple times
 * 
 * THREAD SAFETY:
 * Uses @Volatile on the `started` flag to ensure visibility across threads.
 * This prevents race conditions when multiple IntelliJ components try to
 * initialize the monitoring system simultaneously.
 * 
 * USAGE PATTERN:
 * All FreezeGuard actions follow this pattern:
 * ```kotlin
 * val (stalls, longest) = FG.monitor.snapshotAndReset()
 * ```
 * This gives each action access to the same monitoring data and ensures
 * consistent stall detection across all measurements.
 * 
 * LIFECYCLE MANAGEMENT:
 * - Started by: MyProjectActivity when first project opens
 * - Accessed by: All FreezeGuard actions (BadBlockingAction, BackgroundFixAction, etc.)
 * - Stopped by: IntelliJ shutdown (daemon thread auto-cleanup)
 * 
 * EDUCATIONAL VALUE:
 * This demonstrates proper singleton design for IntelliJ plugins:
 * - Global state when needed (monitoring system)
 * - Thread safety with @Volatile
 * - Resource sharing across multiple projects
 * - Initialization guard to prevent duplicates
 */
object FG {

    /**
     * SHARED EDT STALL MONITOR - The Heart of Performance Detection
     * 
     * This single EdtStallMonitor instance is shared by all FreezeGuard actions
     * and provides consistent stall detection across the entire IntelliJ process.
     * 
     * HOW THE HEARTBEAT MONITORING WORKS:
     * Think of this like a medical heart monitor for IntelliJ's EDT:
     * 
     * 1. REGULAR HEARTBEATS: Every 50ms, a background thread schedules a tiny task on the EDT
     * 2. TIMING MEASUREMENT: Records when the task was scheduled vs when it actually runs
     * 3. DELAY DETECTION: If the task runs more than 100ms late, the EDT was blocked
     * 4. STALL COUNTING: Tracks how many delays occurred and the worst delay duration
     * 
     * DETAILED STEP-BY-STEP PROCESS:
     * 1. Background thread: t_expected = System.nanoTime() (record current time)
     * 2. Background thread: SwingUtilities.invokeLater { ... } (schedule EDT task)
     * 3. EDT (when free): t_actual = System.nanoTime() (record when task finally runs)
     * 4. EDT: delay = t_actual - t_expected (calculate how late the task was)
     * 5. EDT: if (delay >= 100ms) → count as stall, update statistics
     * 
     * WHY THIS APPROACH IS GENIUS:
     * - Measures REAL user-experienced freezes (not theoretical problems)
     * - Works for ANY cause of EDT blocking (heavy actions, slow painting, plugin bugs)
     * - Lightweight: tiny tasks don't add performance overhead
     * - Accurate: nanosecond precision timing
     * - Continuous: always monitoring in background
     * 
     * REAL-WORLD EXAMPLE:
     * - 12:00.000: Schedule heartbeat task
     * - 12:00.050: Schedule another heartbeat task  
     * - 12:00.100: Schedule another heartbeat task
     * - 12:00.020-12:01.220: BadBlockingAction blocks EDT with Thread.sleep(1200)
     * - 12:01.220: First heartbeat finally runs → 1220ms late → MAJOR STALL DETECTED!
     * - 12:01.221: Second heartbeat runs → 1171ms late → MAJOR STALL DETECTED!
     * - Result: 2+ stalls detected, longest = 1220ms
     * 
     * This is exactly how we detect that BadBlockingAction causes 1.2-second UI freezes!
     */
    val monitor = EdtStallMonitor()
    
    /**
     * INITIALIZATION GUARD - Prevents Duplicate Monitor Startup
     * 
     * This flag ensures the EdtStallMonitor starts only once, even if multiple
     * IntelliJ projects open simultaneously or actions are triggered rapidly.
     * 
     * @Volatile EXPLANATION:
     * Without @Volatile, different threads might see different values of `started`:
     * - Thread 1: reads started=false, begins initialization
     * - Thread 2: reads started=false (cached old value), also begins initialization  
     * - Result: Two monitors start, causing conflicts and duplicate measurements
     * 
     * With @Volatile:
     * - All threads see the most recent value of `started`
     * - Only one thread can change started from false to true
     * - Prevents race conditions during plugin initialization
     * 
     * USAGE PATTERN:
     * ```kotlin
     * if (!FG.started) {
     *     FG.started = true
     *     FG.monitor.start()  // Start background heartbeat monitoring
     * }
     * ```
     * 
     * This pattern is used in MyProjectActivity to initialize monitoring when
     * the first IntelliJ project opens.
     */
    @Volatile var started: Boolean = false
}

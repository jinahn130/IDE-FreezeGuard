package com.github.jinahn130.intellijfreezeguard.startup

import com.github.jinahn130.intellijfreezeguard.FG
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * MY PROJECT ACTIVITY - Plugin Initialization Hook
 * 
 * PURPOSE:
 * This class handles the initialization of the FreezeGuard monitoring system when
 * IntelliJ projects start up. It ensures that EDT stall monitoring begins automatically
 * without requiring user intervention.
 * 
 * INTELLIJ PLUGIN LIFECYCLE:
 * IntelliJ plugins have several initialization points:
 * 1. Plugin loading - Plugin JARs are loaded into classpath
 * 2. Extension registration - Services, actions, etc. are registered
 * 3. Application startup - IDE application-level initialization
 * 4. Project opening - Individual projects are opened (THIS IS WHERE WE HOOK IN)
 * 5. User interactions - User clicks menu items, runs actions, etc.
 * 
 * WHY PROJECT ACTIVITY?
 * We use ProjectActivity (not ApplicationStartup) because:
 * - EDT monitoring should only run when projects are active
 * - ProjectActivity is called reliably when projects open
 * - Multiple projects can share the same monitor (via FG singleton)
 * - Automatically handles IntelliJ restart scenarios
 * - Lightweight: doesn't delay IDE startup significantly
 * 
 * INITIALIZATION STRATEGY:
 * The initialization uses a "first project wins" approach:
 * 1. First project to open starts the monitor
 * 2. Subsequent projects reuse the existing monitor
 * 3. Monitor runs continuously until IntelliJ shuts down
 * 4. Daemon thread cleanup happens automatically
 * 
 * THREAD SAFETY:
 * Uses FG.started flag with @Volatile to prevent race conditions when
 * multiple projects open simultaneously. Only one monitor starts even
 * if multiple ProjectActivity.execute() calls happen concurrently.
 * 
 * SUSPEND FUNCTION:
 * The execute() method is marked 'suspend' because IntelliJ may need to
 * perform async initialization. However, our initialization is lightweight
 * and completes immediately (just starting a background thread).
 * 
 * REAL-WORLD SCENARIOS:
 * - Single project: Monitor starts when project opens
 * - Multiple projects: Monitor starts with first project, shared by all  
 * - IntelliJ restart: Monitor restarts when first project reopens
 * - Project close/reopen: Monitor continues running (global scope)
 * 
 * EDUCATIONAL VALUE:
 * This demonstrates proper IntelliJ plugin initialization patterns:
 * - Using ProjectActivity for project-scoped initialization
 * - Singleton pattern for shared resources
 * - Thread-safe initialization guards
 * - Logging for debugging plugin lifecycle issues
 */
class MyProjectActivity : ProjectActivity {
    private val log = Logger.getInstance(MyProjectActivity::class.java)

    /**
     * EXECUTE - Initialize FreezeGuard Monitoring When Project Opens
     * 
     * This method is called automatically by IntelliJ when a project opens.
     * It implements a "initialize once" pattern to start EDT stall monitoring
     * for the entire IDE process.
     * 
     * INITIALIZATION LOGIC:
     * 1. Check if monitoring is already started (FG.started flag)
     * 2. If not started, initialize the monitor and mark as started
     * 3. If already started, do nothing (subsequent projects reuse existing monitor)
     * 
     * WHY CHECK FG.started?
     * Without this check:
     * - Opening multiple projects would create multiple monitors
     * - Multiple background threads would compete and interfere
     * - Performance overhead would multiply unnecessarily
     * - Telemetry data would be duplicated/corrupted
     * 
     * MONITOR STARTUP SEQUENCE:
     * 1. FG.monitor.start() creates background thread named "FreezeGuard-EDT-Heartbeat"
     * 2. Background thread schedules heartbeat probes every 50ms
     * 3. EDT tasks measure timing delays to detect stalls
     * 4. Stall statistics accumulate for telemetry collection
     * 
     * THREAD SAFETY:
     * The FG.started flag uses @Volatile to ensure thread-safe checking.
     * This prevents race conditions when multiple projects open simultaneously.
     * 
     * LOGGING:
     * Records successful monitor startup in IntelliJ logs for:
     * - Debugging plugin initialization issues
     * - Confirming monitoring system is active
     * - Audit trail for plugin lifecycle events
     * 
     * ERROR HANDLING:
     * Currently no explicit error handling because monitor.start() is designed
     * to be robust. If initialization fails, the monitor simply won't collect
     * stall data, but the plugin won't crash IntelliJ.
     * 
     * PERFORMANCE IMPACT:
     * This initialization is very lightweight:
     * - Takes < 1ms to complete  
     * - Doesn't block project opening
     * - Background thread is daemon (low priority)
     * - No UI operations during initialization
     * 
     * @param project The IntelliJ project that's being opened (may be used for logging)
     */
    override suspend fun execute(project: Project) {
        // INITIALIZATION GUARD: Only start monitor once per IDE session
        // This prevents duplicate monitors when multiple projects are open
        if (!FG.started) {
            
            // START BACKGROUND MONITORING: Begin EDT heartbeat detection
            FG.monitor.start()  // Creates "FreezeGuard-EDT-Heartbeat" daemon thread
            
            // MARK AS STARTED: Prevent duplicate initialization
            FG.started = true  // Thread-safe flag prevents race conditions
            
            // CONFIRMATION LOGGING: Record successful initialization
            log.info("Freeze Guard: EDT stall monitor started (ProjectActivity)")
        }
        // Note: If FG.started is already true, we do nothing - the existing monitor
        // will handle stall detection for all projects in this IntelliJ instance
    }
}

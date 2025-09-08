/**
 * VS CODE FREEZE GUARD TYPE DEFINITIONS - Cross-Platform Telemetry Data Structures
 * 
 * PURPOSE:
 * This file defines TypeScript interfaces for telemetry data shared between the
 * VS Code extension and the monitoring collector service. These types ensure
 * data consistency and provide IDE support for performance event handling.
 * 
 * CROSS-PLATFORM CONSISTENCY:
 * These types mirror the data structures used in the IntelliJ plugin to ensure
 * consistent telemetry format across different IDEs. The collector service can
 * process events from both IntelliJ and VS Code using the same schema.
 * 
 * IntelliJ (Kotlin) ↔ VS Code (TypeScript)
 * =========================================
 * ActionEvent data class ↔ ActionEvent interface
 * Pair<Int, Double> ↔ StallSnapshot interface
 * 
 * JSON SCHEMA COMPATIBILITY:
 * The ActionEvent interface maps directly to the JSON payload sent to the
 * collector service via HTTP POST. Field names use snake_case to match
 * the collector's expected schema.
 * 
 * TYPE SAFETY BENEFITS:
 * - Compile-time validation of telemetry data structure
 * - IDE autocompletion for event fields
 * - Prevents typos in field names
 * - Documents expected data types and formats
 * - Enables refactoring with confidence
 */

/**
 * ACTION EVENT - Performance Measurement Data Structure
 * 
 * This interface represents a single performance measurement from a VS Code
 * command execution. Every time a user runs a FreezeGuard command, we create
 * one ActionEvent containing all the performance metrics.
 * 
 * FIELD DOCUMENTATION:
 * 
 * @field action - Command identifier (e.g., "badBlocking", "backgroundFix", "measure")
 *               Used to distinguish different types of operations in monitoring dashboards
 * 
 * @field duration_ms - Total execution time in milliseconds with decimal precision
 *                     Measures wall-clock time from command start to completion
 * 
 * @field thread - Which thread type executed the work
 *                'MAIN' = VS Code main thread (equivalent to IntelliJ's EDT)
 *                'WORKER' = Background/async processing (equivalent to IntelliJ's background threads)
 * 
 * @field heap_delta_bytes - Memory usage change in bytes (positive = allocated, negative = freed)
 *                          Measures Node.js heap usage difference before/after operation
 * 
 * @field edt_stalls - Number of main thread stalls detected during operation
 *                    'edt' name maintained for compatibility with IntelliJ data
 *                    Higher values indicate worse user experience (UI unresponsiveness)
 * 
 * @field edt_longest_stall_ms - Duration of worst main thread stall in milliseconds
 *                              'edt' name maintained for compatibility with IntelliJ data
 *                              Shows maximum UI freeze duration experienced
 * 
 * @field ts - ISO timestamp when event occurred (e.g., "2024-01-01T12:30:45.123Z")
 *            Used for time-series analysis and chronological ordering in monitoring
 * 
 * EXAMPLE EVENT DATA:
 * {
 *   "action": "badBlocking",
 *   "duration_ms": 1205.3,
 *   "thread": "MAIN",
 *   "heap_delta_bytes": 524288,
 *   "edt_stalls": 24,
 *   "edt_longest_stall_ms": 1180.2,
 *   "ts": "2024-01-01T12:30:45.123Z"
 * }
 * 
 * This tells us: "The bad blocking command took 1.2 seconds on the main thread,
 * used 512KB of memory, caused 24 UI stalls with the worst being 1.18 seconds"
 * 
 * NAMING CONVENTIONS:
 * - snake_case for JSON compatibility with Python collector service
 * - Descriptive names that explain what each metric represents
 * - Consistent with IntelliJ plugin field names for cross-platform analysis
 */
export interface ActionEvent {
  action: string;              // Command/operation identifier
  duration_ms: number;         // Total execution time (milliseconds)
  thread: 'MAIN' | 'WORKER';   // Thread type (main UI thread vs background)
  heap_delta_bytes: number;    // Memory usage change (bytes, signed)
  edt_stalls: number;          // Number of main thread stalls detected
  edt_longest_stall_ms: number; // Duration of worst stall (milliseconds)
  ts: string;                  // ISO timestamp of event occurrence
}

/**
 * STALL SNAPSHOT - Main Thread Performance Statistics
 * 
 * This interface represents a snapshot of main thread stall statistics collected
 * over a period of time. It's used to transfer stall data from the monitor to
 * the telemetry system and reset counters for the next measurement period.
 * 
 * USAGE PATTERN:
 * 1. Monitor continuously detects stalls and updates internal counters
 * 2. When action completes, call monitor.snapshotAndReset()
 * 3. Monitor returns StallSnapshot with current statistics
 * 4. Monitor resets internal counters to zero for next measurement
 * 5. StallSnapshot data is included in ActionEvent telemetry
 * 
 * FIELD DOCUMENTATION:
 * 
 * @field count - Total number of stalls detected since last reset
 *               Each stall represents a period where the main thread was blocked
 *               Higher values indicate more frequent UI unresponsiveness
 * 
 * @field longestMs - Duration of the worst stall in milliseconds
 *                   Shows the maximum time the UI was completely frozen
 *                   Critical metric for understanding user experience impact
 * 
 * EXAMPLE SCENARIOS:
 * 
 * Good Performance:
 * { count: 0, longestMs: 0 }
 * → No stalls detected, smooth user experience
 * 
 * Moderate Performance Issues:  
 * { count: 3, longestMs: 150.5 }
 * → 3 brief stalls, worst was 150ms (noticeable but not severe)
 * 
 * Severe Performance Problems:
 * { count: 24, longestMs: 1180.2 }
 * → 24 stalls with worst being 1.18 seconds (very poor user experience)
 * 
 * RELATIONSHIP TO ACTIONEVENT:
 * StallSnapshot data is embedded in ActionEvent as:
 * - snapshot.count → ActionEvent.edt_stalls
 * - snapshot.longestMs → ActionEvent.edt_longest_stall_ms
 * 
 * THREAD SAFETY:
 * The monitor ensures thread-safe snapshot operations so multiple actions
 * can call snapshotAndReset() concurrently without data corruption.
 */
export interface StallSnapshot {
  count: number;      // Total number of stalls detected
  longestMs: number;  // Duration of worst stall (milliseconds)
}
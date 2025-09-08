package com.github.jinahn130.intellijfreezeguard

import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * ACTION EVENT - Telemetry Data Structure for Performance Monitoring
 * 
 * This data class represents a single performance measurement from the IntelliJ plugin.
 * Every time a user action completes (like clicking a button or running a command),
 * we create one of these events to send to our monitoring system.
 * 
 * FIELD EXPLANATIONS:
 * 
 * @param action - What the user did (e.g., "FreezeGuard.BadBlockingAction", "File.Open", "Edit.Copy")
 *               This helps us understand which operations are causing performance problems
 * 
 * @param durationMs - How long the action took in milliseconds (e.g., 1250.5 = 1.25 seconds)
 *                   This is the total wall-clock time from start to finish
 * 
 * @param thread - Which thread the action ran on (always "EDT" for IntelliJ)
 *               EDT = Event Dispatch Thread (IntelliJ's main UI thread)
 *               This helps distinguish IntelliJ events from VS Code events in monitoring
 * 
 * @param heapDeltaBytes - How much memory (heap) the action used in bytes
 *                        Positive = allocated memory, Negative = freed memory
 *                        Helps identify memory-intensive operations
 * 
 * @param edtStalls - How many times the EDT got blocked during this action
 *                   More stalls = worse user experience (UI becomes unresponsive)
 *                   
 * @param edtLongestStallMs - Duration of the worst EDT stall in milliseconds
 *                          Shows the maximum time the UI was completely frozen
 * 
 * @param tsIso - Timestamp when the event occurred in ISO format (e.g., "2024-01-01T12:30:45.123Z")
 *               Used for time-series analysis and chronological ordering
 * 
 * EXAMPLE EVENT:
 * ActionEvent(
 *   action = "FreezeGuard.BadBlockingAction",
 *   durationMs = 2150.3,           // Action took 2.15 seconds
 *   thread = "EDT",                // Ran on Event Dispatch Thread  
 *   heapDeltaBytes = 524288,       // Used 512KB of memory
 *   edtStalls = 4,                 // EDT was blocked 4 times
 *   edtLongestStallMs = 850.2,     // Worst freeze lasted 850ms
 *   tsIso = "2024-01-01T12:30:45.123Z"
 * )
 * 
 * This tells us: "The bad blocking action took over 2 seconds, froze the UI 4 times 
 * (worst freeze was 850ms), and used half a megabyte of memory"
 */
data class ActionEvent(
    val action: String,              // What user action was performed
    val durationMs: Double,          // How long it took (milliseconds)
    val thread: String,              // Which thread (always "EDT" for IntelliJ)
    val heapDeltaBytes: Long,        // Memory usage change (bytes)
    val edtStalls: Int,              // Number of UI freezes during action
    val edtLongestStallMs: Double,   // Duration of worst UI freeze (milliseconds) 
    val tsIso: String                // When it happened (ISO timestamp)
)

/**
 * EVENT SENDER - HTTP Client for Sending Telemetry to Monitoring System
 * 
 * This object (singleton) handles sending performance data from the IntelliJ plugin
 * to our external monitoring collector service. Think of it as the "network layer"
 * of our monitoring system.
 * 
 * ARCHITECTURE OVERVIEW:
 * IntelliJ Plugin → EventSender (HTTP POST) → Collector Service → Prometheus → Grafana
 * 
 * WHY SINGLETON (object)?
 * - Only need one HTTP client for the entire plugin
 * - Shares connection pool across all telemetry sends  
 * - Ensures consistent configuration and logging
 * 
 * NETWORK DESIGN:
 * - Uses HTTP/1.1 (simple, reliable, widely supported)
 * - Connects to localhost (collector runs on same machine as IDE)
 * - Port 8000 (standard development port, not privileged)
 * - Asynchronous sends (don't block IDE while sending telemetry)
 * - Timeouts prevent hanging on network issues
 */
object EventSender {
    // LOGGING: Use IntelliJ's built-in logger (appears in IDE logs and help -> show log)
    private val log = Logger.getInstance(EventSender::class.java)

    // NETWORK ENDPOINTS: Where to send telemetry data
    private const val BASE = "http://127.0.0.1:8000"      // Collector service base URL
    private const val INGEST = "$BASE/ingest"              // POST telemetry events here
    private const val METRICS = "$BASE/metrics"            // GET to check if collector is alive

    /**
     * HTTP CLIENT CONFIGURATION
     * 
     * This creates a reusable HTTP client with optimized settings:
     * - HTTP/1.1: Simple, reliable, well-supported version
     * - 1500ms connect timeout: Don't hang if collector service is down
     * - Shared across all requests: Efficient connection pooling
     * 
     * WHY THESE SETTINGS?
     * - HTTP/1.1 vs HTTP/2: Simpler, fewer compatibility issues
     * - 1.5 second timeout: Balance between patience and responsiveness  
     * - Single client: Reuses TCP connections, more efficient than creating new clients
     */
    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)           // Reliable, simple HTTP version
        .connectTimeout(Duration.ofMillis(1500))        // Don't wait forever to connect
        .build()

    /**
     * PING - Check if Collector Service is Running
     * 
     * This function tests connectivity to the monitoring collector before sending
     * telemetry data. It helps diagnose network issues and provides better error messages.
     * 
     * HOW IT WORKS:
     * 1. Send GET request to collector's /metrics endpoint (Prometheus format)
     * 2. Ignore the response body (we just want to know if it's reachable)
     * 3. Return HTTP status code (200 = success, -1 = error)
     * 
     * WHY PING THE /metrics ENDPOINT?
     * - Always available (Prometheus standard)
     * - Lightweight (doesn't process data)
     * - Tests both network connectivity and service health
     * 
     * ASYNCHRONOUS DESIGN:
     * Returns CompletableFuture<Int> so it doesn't block the EDT while checking connectivity.
     * The calling code can handle the result when it's ready.
     * 
     * ERROR HANDLING:
     * If network fails, returns -1 and logs the error. This prevents crashes and
     * provides diagnostic information in IntelliJ logs.
     */
    fun ping() = client.sendAsync(
        HttpRequest.newBuilder()
            .uri(URI.create(METRICS))           // GET collector's metrics endpoint
            .timeout(Duration.ofMillis(1000))   // Quick timeout for ping
            .GET()                              // HTTP GET method
            .build(),
        HttpResponse.BodyHandlers.discarding() // Ignore response body, just want status
    ).thenApply { response -> 
        response.statusCode()                   // Extract HTTP status code (200, 404, 500, etc.)
    }.exceptionally { exception -> 
        log.warn("FreezeGuard ping error", exception) // Log network errors
        -1                                      // Return -1 to indicate failure
    }

    /**
     * SEND ASYNC - Transmit Performance Event to Collector
     * 
     * This is the core function that sends telemetry data from IntelliJ to our
     * monitoring system. It converts ActionEvent objects to JSON and POSTs them
     * to the collector service.
     * 
     * ASYNCHRONOUS DESIGN:
     * This function returns immediately and sends the HTTP request in the background.
     * This prevents blocking the EDT (IntelliJ's main thread) while network operations
     * are in progress. The user experience stays smooth even if network is slow.
     * 
     * JSON FORMAT:
     * Converts Kotlin ActionEvent to JSON that matches the collector's expected schema:
     * {
     *   "action": "FreezeGuard.BadBlockingAction",
     *   "duration_ms": 1250.500,
     *   "thread": "EDT", 
     *   "heap_delta_bytes": 524288,
     *   "edt_stalls": 4,
     *   "edt_longest_stall_ms": 850.200,
     *   "ts": "2024-01-01T12:30:45.123Z"
     * }
     * 
     * ERROR HANDLING:
     * - Network timeouts: 2 seconds for request, 3 seconds total
     * - HTTP errors: Logs non-2xx responses with body snippet
     * - Exceptions: Catches and logs all network/JSON errors
     * - Never crashes: Always handles errors gracefully
     * 
     * LOGGING STRATEGY:
     * - Always logs payload size (helps debug large events)
     * - Logs successful sends (confirms telemetry is working)
     * - Logs errors with details (helps troubleshoot collector issues)
     * - Uses log.warn() so messages appear even with default log levels
     */
    fun sendAsync(event: ActionEvent) {
        // STEP 1: Convert ActionEvent to JSON string
        // Use string interpolation for simplicity (more robust than manual JSON building)
        val json = """{
          "action":"${event.action}",
          "duration_ms":${"%.3f".format(event.durationMs)},
          "thread":"${event.thread}",
          "heap_delta_bytes":${event.heapDeltaBytes},
          "edt_stalls":${event.edtStalls},
          "edt_longest_stall_ms":${"%.3f".format(event.edtLongestStallMs)},
          "ts":"${event.tsIso}"
        }""".trimIndent()

        // STEP 2: Convert JSON string to UTF-8 bytes
        // HTTP requires byte arrays, and UTF-8 ensures international character support
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        log.warn("FreezeGuard payload bytes=${bytes.size}")  // Log payload size for debugging

        // STEP 3: Build HTTP POST request
        val req = HttpRequest.newBuilder()
            .uri(URI.create(INGEST))                                    // POST to /ingest endpoint
            .timeout(Duration.ofMillis(2000))                           // 2 second timeout per request
            .header("Content-Type", "application/json; charset=UTF-8")  // Tell server this is JSON
            // NOTE: HttpClient automatically sets Content-Length header for byte arrays
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))        // Send JSON bytes as POST body
            .build()

        // STEP 4: Send request asynchronously and handle response
        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())  // Send HTTP request in background
            .thenAccept { response ->                                 // When response arrives, process it
                val code = response.statusCode()
                
                // Check if HTTP status indicates success (2xx codes)
                if (code / 100 != 2) {  // 200-299 are success codes
                    // Log error responses with truncated body for debugging
                    log.warn("FreezeGuard ingest HTTP $code body='${response.body().take(200)}'")
                } else {
                    // Log successful submissions (confirms telemetry is working)
                    log.warn("FreezeGuard ingest OK $code")
                }
            }
            .orTimeout(3, TimeUnit.SECONDS)                          // Total timeout: 3 seconds
            .exceptionally { exception ->                            // Handle any network/timeout errors
                log.warn("FreezeGuard ingest error", exception)      // Log error for debugging
                null                                                 // Return null (required by exceptionally)
            }
    }
}

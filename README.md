# IDE-FreezeGuard

Cross-platform IDE performance monitoring toolkit for detecting and tracking UI freezes in development environments.


## Overview
IDE-FreezeGuard is a cross-platform performance monitoring toolkit that detects and analyzes UI freezes in modern IDEs. It instruments the Event Dispatch Thread (EDT) in IntelliJ and the main process event loop in VS Code—both of which are responsible for maintaining UI responsiveness. Monitoring these critical execution paths makes it possible to detect stalls and freezes that directly affect the interactive performance of the IDE.

Synthetic workloads can be triggered through a custom Freeze Guard action available in the IDE’s command palette. This enables controlled measurement of responsiveness, validation of performance fixes, and consistent capture of diagnostic data.

The toolkit provides both local notifications within IDEs and comprehensive telemetry collection through a shared monitoring stack. Together, these surfaces present a complete view of runtime performance, including:

- Stall distributions at P50, P95, and P99
- Action duration breakdowns
- Per-action memory consumption
- Heatmap visualizations of stall frequency and intensity

By combining immediate in-IDE alerts with aggregated telemetry, FreezeGuard transforms runtime stalls into actionable insights, enabling continuous improvement of IDE responsiveness and stability across platforms.


## Architecture

```
IDE-FreezeGuard/
├── intellij-freezeguard/    # IntelliJ IDEA plugin
├── vscode-freezeguard/      # VS Code extension  
├── collector/               # Shared telemetry collector (FastAPI)
├── prometheus/              # Metrics storage configuration
├── grafana/                 # Visualization dashboards
└── ops/                     # Docker Compose setup
```


The system provides **unified monitoring across both IDEs** through a centralized telemetry pipeline. Blocking action from Intellij and VS code is broadcasted out to the FastAPI collector using the /ingest endpoint, and then successively queried in Prometheus and Grafana for data collection and visualization.

```
┌─────────────────┐    ┌─────────────────┐
│  IntelliJ IDEA  │    │    VS Code      │
│     Plugin      │    │   Extension     │
└────────┬────────┘    └────────┬────────┘
         │ HTTP POST             │ HTTP POST  
         │ /ingest               │ /ingest
         │                       │
         └───────┐       ┌───────┘
                 ▼       ▼
         ┌─────────────────────────┐
         │   FastAPI Collector     │
         │     (Port 8000)         │
         └────────┬────────────────┘
                  │ /metrics endpoint
                  ▼
         ┌─────────────────────────┐
         │      Prometheus         │
         │     (Port 9090)         │
         └────────┬────────────────┘
                  │ PromQL queries
                  ▼
         ┌─────────────────────────┐
         │       Grafana           │
         │     (Port 3000)         │
         └─────────────────────────┘
```

## Demonstration
1. Create a UI blockingCalling the custom Freeze Guard action from the sandbox IDE.

![alt text](/docs/images/image.png)

![alt text](/docs/images/image-7.png)

2. Python Fast API reporting page shows the various actions taken from the IDE including the number of stalls, longest stalls, and heap data used.
![alt text](/docs/images/image-8.png)

3. Can query Prometheus for the time series data.
![alt text](/docs/images/image-12.png)

4. Grafana Dashboard for both Intellij and VS code
![alt text](/docs/images/image-10.png)
![alt text](/docs/images/image-11.png)



## Quick Start 

### 1. Start Monitoring Infrastructure

```bash
cd ops
docker compose up
```

Access points:
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090  
- **Collector**: http://localhost:8000/report

### 2. IDE-Specific Setup

#### IntelliJ IDEA
```bash
cd intellij-freezeguard
./gradlew runIde    # Launches development instance with plugin loaded
```

**Usage**: In the launched IDE instance, access actions via:
- **Find Action** (Ctrl+Shift+A / Cmd+Shift+A): Search for "Freeze Guard"
- **Tools Menu**: Look for FreezeGuard actions
- Available actions:
  - "Freeze Guard: Measure Current Action" 
  - "Freeze Guard: Run BAD Blocking Action"
  - "Freeze Guard: Run FIXED Background Action"

#### VS Code  
```bash
cd vscode-freezeguard/extension
npm install
npm run compile
```

**Development**: 
1. Open `vscode-freezeguard/` in VS Code
2. Press **F5** to start Extension Development Host (a sandbox IDE where you will see the  Freeze Guard actions)
3. In the new VS Code window, open Command Palette (**Cmd+Shift+P** / **Ctrl+Shift+P**)
4. Available commands:
   - "FreezeGuard: Measure Action"
   - "FreezeGuard: Bad Blocking Action" 
   - "FreezeGuard: Background Fix Action"


### Cross-Platform Dashboard Benefits

**Unified Performance View**: Both IDE's metrics appear on the same Grafana dashboard with platform-specific thread labels:
- **IntelliJ actions**: Display as `thread="EDT"` 
- **VS Code actions**: Display as `thread="Main"`
- **Combined P95 Latency**: Aggregates performance across both platforms
- **Thread Distribution**: Compare EDT vs Main Thread usage patterns

**Real-World Scenario**: Running the same action from both IDEs creates separate events:
1. IntelliJ sends: `{"action": "BadBlockingAction", "thread": "EDT", "edt_stalls": X}`
2. VS Code sends: `{"action": "BadBlockingAction", "thread": "Main", "edt_stalls": Y}`
3. Grafana displays: **Combined metrics** from both platforms in unified charts

This enables **cross-platform performance analysis** - compare how the same operations perform across different IDE platforms and identify if threading issues affect one platform more than another.

## System Architecture Deep Dive

### Component Communication Details

#### 1. IDE Plugin → Collector Communication
- **Protocol**: HTTP/1.1 POST requests to `http://127.0.0.1:8000/ingest`

- **Payload**: JSON-serialized `ActionEvent` objects

- **Error Handling**: Async fire-and-forget with timeout protection

- **Connection Pooling**: Reuses HTTP client connections for efficiency

**Why 127.0.0.1 instead of localhost?**
- **127.0.0.1**: Direct IPv4 loopback address, bypasses DNS resolution
- **localhost**: Hostname that requires DNS lookup, could resolve to IPv6 `::1`
- **Performance**: 127.0.0.1 is faster (no DNS overhead) and more predictable
- **Compatibility**: Some environments have IPv6 issues or DNS resolution problems
- **CI/CD**: Testing environments (like GitHub Actions) often prefer explicit IP addresses

```kotlin
// IntelliJ Implementation
val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofMillis(1500))
    .build()

client.sendAsync(request, responseHandler)
    .orTimeout(3, TimeUnit.SECONDS)
```

#### 2. Collector → Prometheus Integration
**Method**: Prometheus scrapes collector's `/metrics` endpoint every 15 seconds
**Format**: OpenMetrics/Prometheus text format
**Metrics Types**: Histograms (latency), Counters (events, stalls)
**Labels**: Action name, thread type for dimensional analysis

```python
# Collector exposes metrics at /metrics
@app.get("/metrics")
def metrics():
    return Response(content=generate_latest(), 
                   media_type=CONTENT_TYPE_LATEST)
```

#### 3. Prometheus → Grafana Visualization  
**Query Language**: PromQL for time-series analysis
**Datasource**: Grafana queries Prometheus at `http://prometheus:9090`
**Refresh Rate**: 5-second auto-refresh for real-time monitoring
**Time Windows**: 5-minute rolling windows for percentile calculations

```promql
# P95 latency across both IDEs
histogram_quantile(0.95, 
  rate(action_duration_seconds_bucket[5m]))
```

#### 4. Network Configuration (Docker Compose)
**Internal Network**: `fgnet` bridge network for container communication
**Service Discovery**: Containers communicate via service names (e.g., `http://prometheus:9090`)
**External Access**: Host ports mapped for development access
- Collector: `localhost:8000` → `collector:8000`
- Prometheus: `localhost:9090` → `prometheus:9090`  
- Grafana: `localhost:3000` → `grafana:3000`

### Data Flow Lifecycle

1. **Event Generation**: IDE action triggers performance measurement
2. **Local Processing**: Plugin calculates duration, memory delta, EDT stalls
3. **Serialization**: Event converted to JSON with ISO timestamps
4. **HTTP Transport**: Async POST to collector with retry logic
5. **Validation**: Collector validates using Pydantic models  
6. **Metrics Export**: Prometheus histograms/counters updated
7. **Storage**: Prometheus stores time-series data with labels
8. **Visualization**: Grafana queries and displays real-time charts
9. **Alerting**: (Future) Grafana can alert on performance thresholds

## Components

### IntelliJ Plugin (`intellij-freezeguard/`)
- Kotlin-based plugin for IntelliJ IDEA
- EDT stall monitoring with 50ms heartbeat probes
- Demo actions showing performance impact
- Integration with shared telemetry stack

### VS Code Extension (`vscode-freezeguard/`)  
- TypeScript-based extension for VS Code
- UI thread monitoring and performance tracking
- Command palette integration
- Telemetry reporting to shared collector

### Shared Monitoring Stack
- **Collector**: FastAPI service for telemetry ingestion
- **Prometheus**: Time-series metrics storage
- **Grafana**: Performance visualization dashboards
- **Docker Compose**: Complete infrastructure setup

## Usage

Both IDE implementations provide similar functionality:

1. **Performance Monitoring**: Automatic EDT/UI thread stall detection
2. **Demo Actions**: Compare blocking vs non-blocking operations
3. **Telemetry**: Optional metrics collection for analysis
4. **Notifications**: Real-time feedback on performance issues

## Demo Actions Explained

The plugin provides three demonstration actions that showcase different threading behaviors and their impact on UI responsiveness:

### 1. FreezeGuard Action (`FreezeGuardAction`)
- **Purpose**: Baseline measurement action that runs very quickly
- **Thread**: Executes on **EDT** (Event Dispatch Thread)  
- **Behavior**: Minimal work (~microseconds), just measures timing and memory
- **Sleep**: None - completes immediately
- **Expected Result**: Near-zero EDT stalls, very low duration

### 2. Bad Blocking Action (`BadBlockingAction`) 
- **Purpose**: Demonstrates **poor practice** - blocking the UI thread
- **Thread**: Executes on **EDT** (Event Dispatch Thread)
- **Behavior**: Calls `Thread.sleep(1200)` directly on EDT
- **Sleep**: 1.2 seconds of blocking sleep **on EDT**
- **Expected Result**: 
  - UI becomes completely unresponsive for 1.2 seconds
  - Users cannot interact with IDE (click, type, scroll, etc.)
  - ~24 EDT stalls detected (1200ms ÷ 50ms probe interval)
  - Longest stall ~1200ms
  - Warning notification shown

### 3. Background Fix Action (`BackgroundFixAction`)
- **Purpose**: Demonstrates **correct practice** - offloading work to background thread
- **Thread**: Heavy work runs on **BGT** (Background Thread), UI updates on **EDT**
- **Behavior**: Uses `Task.Backgroundable` to run `Thread.sleep(1200)` off-EDT
- **Sleep**: 1.2 seconds of sleep **on background thread** 
- **Expected Result**:
  - UI remains fully responsive during execution
  - Users can continue working normally
  - Progress indicator shown (modal dialog prevents interaction but doesn't freeze UI)
  - Near-zero EDT stalls detected
  - Duration same as BadBlocking but thread = "BGT"

## UI Thread Stall Detection Deep Dive

### What is a UI Thread Stall?
A **UI Thread Stall** occurs when the main UI thread is blocked and cannot process UI events (mouse clicks, keyboard input, repaints) within an acceptable timeframe.

**Platform-Specific Threading**:
- **IntelliJ IDEA**: Uses **EDT** (Event Dispatch Thread) - Swing's main UI thread
- **VS Code**: Uses **Main Thread** - Node.js/Electron's main event loop thread
- Both serve the same purpose: handle all UI interactions and keep the interface responsive

### Detection Mechanism
The monitor works by posting "heartbeat" probes to the main UI thread:

**IntelliJ Implementation** (`EdtStallMonitor`):

```kotlin
// Every 50ms, schedule a probe
val expectedTime = System.nanoTime()
SwingUtilities.invokeLater {
    val actualTime = System.nanoTime() 
    val delay = actualTime - expectedTime
    if (delay >= 100ms) {
        recordStall(delay) // This is a stall!
    }
}
```

**VS Code Implementation**: Uses `setImmediate()` to schedule probes on the Node.js main event loop (similar concept, different API).

### Ping/Probe Timing Example
During BadBlockingAction's 1200ms freeze:
- **t=0ms**: Action starts, calls `Thread.sleep(1200)` (IntelliJ) or blocking operation (VS Code)
- **t=0, 50, 100, 150, 200ms...**: Monitor posts probes to main UI thread queue
- **t=0-1200ms**: Main UI thread is blocked, cannot execute queued probes
- **t=1200ms**: Block ends, UI thread processes all queued probes at once
- **Result**: ~24 probes delayed by ~1200, 1150, 1100, 1050ms respectively

### What Stalls Represent
Each stall represents a **missed opportunity** for user interaction:
- **1 stall = 100ms** where user input would be ignored  
- **24 stalls in 1200ms** = UI completely unresponsive for that duration
- **Not individual user actions**, but monitoring intervals where the main UI thread couldn't respond

## API and Data Contract

### Event Data Model (`ActionEvent`)
The system uses a standardized event model for cross-platform telemetry:

```typescript
// Pydantic model (Python) / Kotlin data class
interface ActionEvent {
    action: string;              // Action name (e.g., "BadBlockingAction")
    duration_ms: number;         // Total execution time in milliseconds
    thread: "EDT" | "BGT";       // Thread type: Event Dispatch Thread or Background Thread  
    heap_delta_bytes: number;    // Memory allocation during action (optional)
    edt_stalls: number;          // Number of EDT stalls detected (≥100ms delays)
    edt_longest_stall_ms: number; // Longest single stall duration in ms
    ts: string;                  // ISO 8601 timestamp
}
```

### FastAPI Endpoints

#### `POST /ingest` - Event Ingestion
**Purpose**: Receives performance events from IDE plugins
**Validation**: Uses Pydantic for type checking and data validation
**Request Body**: JSON matching `ActionEvent` schema
**Response**: `{"ok": true}` on success, detailed error on failure

```bash
curl -X POST http://localhost:8000/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "action": "BadBlockingAction",
    "duration_ms": 1205.3,
    "thread": "EDT", 
    "edt_stalls": 24,
    "edt_longest_stall_ms": 1200.1,
    "ts": "2024-09-06T16:30:00Z"
  }'
```

#### `GET /metrics` - Prometheus Metrics
**Purpose**: Exposes metrics in Prometheus format for scraping
**Response**: Text-based Prometheus metrics format
**Content-Type**: `application/openmetrics-text`

#### `GET /report` - Human-Readable Events  
**Purpose**: Shows recent events in readable format for debugging
**Response**: Plain text format showing last 50 events
**Usage**: Quick verification that events are being received

### Prometheus Metrics Exported

```yaml
# Action execution times (histogram with percentiles)
action_duration_seconds{action="BadBlockingAction", thread="EDT"}

# EDT stall durations (histogram) 
edt_stall_duration_seconds{action="BadBlockingAction"}

# Count of stalls detected (counter)
edt_stalls_total{action="BadBlockingAction"} 

# Total events ingested (counter)
events_total{action="BadBlockingAction", thread="EDT"}
```

## Grafana Dashboard Deep Dive

The dashboard provides real-time visualization of IDE performance metrics:

### Key Visualizations

#### 1. **Latency Percentiles (P50/P95/P99)**
- **Query**: `histogram_quantile(0.95, rate(action_duration_seconds_bucket[5m]))`
- **Purpose**: Shows action completion times at different percentiles
- **Interpretation**: 
  - P50 = 50% of actions complete faster than this time
  - P95 = 95% of actions complete faster (captures most outliers)
  - P99 = 99% of actions complete faster (captures worst-case performance)

**Two Different Measurements**:
1. **action_duration_seconds**: Measures how long each plugin action takes
   - P95 = 1.2s means "95% of actions finish within 1.2 seconds"
2. **edt_stall_duration_seconds**: Measures how delayed each heartbeat probe gets
   - P95 = 1.2s means "95% of detected stalls are shorter than 1.2 seconds"

#### 2. **Stall Rate** 
- **Query**: `rate(edt_stalls_total[1m])`
- **Purpose**: Shows EDT stalls detected per second
- **Interpretation**: Higher values = more UI responsiveness issues

#### 3. **Longest Stall Duration**
- **Query**: `histogram_quantile(0.99, rate(edt_stall_duration_seconds_bucket[5m]))`
- **Purpose**: Shows worst-case EDT blocking times
- **Interpretation**: Values >1s indicate significant UI freezes

#### 4. **Thread Distribution**
- **Query**: `sum by (thread) (rate(events_total[5m]))`  
- **Purpose**: Shows whether work is properly distributed between EDT/BGT
- **Interpretation**: High EDT rates may indicate threading issues

### Dashboard Auto-Refresh
- **Refresh Rate**: 5 seconds
- **Time Window**: Last 30 minutes (configurable)
- **Variables**: Filter by action type and thread type

## Prometheus Query Examples

Access Prometheus at `http://localhost:9090` and try these queries:

### Basic Event Metrics
```promql
# Total events per second
rate(events_total[1m])

# Events by action type
sum by (action) (rate(events_total[5m]))

# EDT vs BGT thread usage
sum by (thread) (rate(events_total[1m]))
```

### Performance Analysis
```promql
# Average action duration (last 5 minutes)
rate(action_duration_seconds_sum[5m]) / rate(action_duration_seconds_count[5m])

# 95th percentile action latency
histogram_quantile(0.95, rate(action_duration_seconds_bucket[5m]))

# Actions taking longer than 1 second
increase(action_duration_seconds_bucket{le="1.0"}[5m])
```

### EDT Stall Analysis
```promql
# EDT stalls per minute
rate(edt_stalls_total[1m]) * 60

# Average stall duration when stalls occur
rate(edt_stall_duration_seconds_sum[5m]) / rate(edt_stall_duration_seconds_count[5m])

# Percentage of actions causing stalls
(rate(edt_stalls_total[5m]) > 0) / rate(events_total[5m]) * 100
```

## Docker Compose

### Why utilizes Docker Compose to orchesrate the telemetry flow?

**Simplified Dependency Management**: 
- Prometheus, Grafana, and Python FastAPI have different runtime requirements
- Docker eliminates "works on my machine" issues across different development environments
- Each service gets isolated environment with exact dependency versions

**Service Orchestration**:
- Automatic network configuration between services  
- Dependency management (`grafana` depends on `prometheus` depends on `collector`)
- Consistent port mapping and volume mounts
- Easy to tear down and recreate entire stack

**Production-Like Environment**:
- Same containerization approach used in production deployments
- Environment parity between development and production
- Easy horizontal scaling if needed

### Docker Compose Services

```yaml
# collector: FastAPI Python service (port 8000)
# prometheus: Time-series database (port 9090) 
# grafana: Visualization dashboard (port 3000)
```

### Service Communication
- **IDE Plugins** → **Collector** (`http://localhost:8000/ingest`)
- **Prometheus** → **Collector** (`http://collector:8000/metrics`) 
- **Grafana** → **Prometheus** (`http://prometheus:9090`)

### Running the Stack

```bash
# Start all services (builds collector image if needed)
cd ops
docker compose up

# Start in background
docker compose up -d

# View logs
docker compose logs -f

# Stop all services  
docker compose down

# Rebuild collector after code changes
docker compose up --build collector
```

### If not using Docker, services can be run manually

```bash
# Terminal 1: Python collector
cd collector
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000

# Terminal 2: Prometheus  
prometheus --config.file=prometheus/prometheus.yml --web.listen-address=:9090

# Terminal 3: Grafana
grafana-server --config=grafana.ini --homepath=/usr/share/grafana
```

## Development

### Local Development (IntelliJ Plugin)

```bash
# Run IDE with plugin for development and manual testing
cd intellij-freezeguard
./gradlew runIde

# Build and verify plugin
./gradlew buildPlugin
./gradlew verifyPlugin
```

## Testing

### Automated Testing Suite

The project includes comprehensive tests for the communication layer between IDEs and the monitoring stack:

**Quick Test All Components:**
```bash
./run-all-tests.sh
```

**Individual Component Tests:**

```bash
# IntelliJ Plugin Tests
cd intellij-freezeguard
./gradlew test

# VS Code Extension Tests  
cd vscode-freezeguard/extension
npm install && npm run compile && npm test

# Collector API Tests
cd collector
python3 -m pip install -r requirements.txt
python3 -m pytest tests/ -v
```

### Integration Testing

**End-to-End Mock Data Pipeline:**
```bash
# Start monitoring stack
docker compose up -d

# Inject mock performance issues and verify they appear in metrics
python3 test-integration.py

# View results in Grafana at http://localhost:3000
# Look for actions 'FreezeGuard.BadBlockingAction', 'freezeguard.badBlocking', etc. in the dashboards
```

**Mock Data Features:**
- **IntelliJ Mock Events**: Uses real action names (`FreezeGuard.BadBlockingAction`, `FreezeGuard.MeasureAction`) with simulated poor performance data (1.5s-2.2s delays, 3-9 EDT stalls)
- **VS Code Mock Events**: Uses real action names (`freezeguard.badBlocking`, `freezeguard.backgroundFix`, `freezeguard.measure`) with simulated performance issues (0.75s-1.2s delays, 2-4 main thread stalls)
- **Cross-Platform Labels**: Events tagged with appropriate thread types (EDT/MAIN/WORKER)
- **Realistic Testing**: Mock events use actual action names from the project for authentic telemetry pipeline validation
- **Note**: These are **simulated JSON events** sent directly to the collector via HTTP - they do NOT trigger actual UI actions or IDE freezes

## Testing Architecture

### Philosophy and Scope

The IDE-FreezeGuard testing strategy focuses on **validating the critical communication pipeline** between IDEs and the monitoring infrastructure. Rather than exhaustive unit testing, we prioritize testing the telemetry flow that enables performance monitoring across different development environments.

### Architecture Overview

```
Unit Tests        API Tests         Integration Tests      Manual Testing
┌─────────────┐   ┌─────────────┐   ┌─────────────────┐   ┌─────────────┐
│IntelliJ     │   │Collector    │   │End-to-End       │   │Real IDE     │
│Mock Events  │   │HTTP         │   │Mock Pipeline    │   │Usage        │
│VS Code      │   │Validation   │   │Docker Stack     │   │Grafana      │
│Stall Logic  │   │Prometheus   │   │HTTP → Metrics   │   │Dashboards   │
└─────────────┘   └─────────────┘   └─────────────────┘   └─────────────┘
```

### Stall Detection Technical Details

**IntelliJ EDT (Event Dispatch Thread) Monitoring:**
- **Mechanism**: Posts heartbeat probes every 50ms to the EDT using `SwingUtilities.invokeLater()`
- **Precision**: Uses `System.nanoTime()` for microsecond-level timing accuracy
- **Detection**: Measures delay between scheduling and execution; ≥100ms delay = stall
- **During 1200ms freeze**: Multiple probes accumulate delays, longest approaches freeze duration

```kotlin
val expectedNs = System.nanoTime()
SwingUtilities.invokeLater {
    val actualNs = System.nanoTime()
    val delayMs = (actualNs - expectedNs) / 1_000_000.0
    if (delayMs >= stallThresholdMs) {
        stallCount.incrementAndGet()
        longestStallMs.updateAndGet { prev -> max(prev, rounded) }
    }
}
```

**VS Code Main Thread Monitoring:**
- **Mechanism**: Uses `setInterval()` to measure timing consistency between expected vs actual intervals
- **Detection**: Compares actual interval against expected 50ms period
- **Calculation**: `delayMs = actualInterval - expectedInterval`
- **Thread Types**: Monitors both MAIN thread and WORKER thread performance

```typescript
const now = Date.now();
const actualInterval = now - this.lastProbeTime;
const delayMs = actualInterval - expectedInterval;
if (delayMs >= this.stallThresholdMs) {
    this.stallCount++;
    this.longestStallMs = Math.max(this.longestStallMs, delayMs);
}
```

### Test Categories

#### 1. **Unit Tests (Communication Layer)**
**Location**: `intellij-freezeguard/src/test/`, `vscode-freezeguard/extension/src/test/`

Validates telemetry data structures, mock event generation, cross-platform thread type compatibility, and stall detection algorithms without actual delays.

#### 2. **API Tests (Collector Validation)** 
**Location**: `collector/tests/`

Tests HTTP endpoints, cross-platform event ingestion, Prometheus metrics generation, and error handling for malformed requests.

#### 3. **Integration Tests (End-to-End Pipeline)**
**Location**: `test-integration.py` (root directory)

Validates the complete telemetry flow by injecting simulated events via HTTP and verifying they appear in Prometheus metrics and Grafana dashboards.

### Mock Data Strategy

Mock events use real action names from the project (`FreezeGuard.BadBlockingAction`, `freezeguard.badBlocking`, etc.) with simulated poor performance data (200ms-2s delays, realistic stall counts) to validate the telemetry pipeline without triggering actual UI freezes.

### Running Tests

#### **Quick Test All Components:**
```bash
./run-all-tests.sh
```

#### **Individual Component Testing:**

**IntelliJ Plugin:**
```bash
cd intellij-freezeguard && ./gradlew test
```

**VS Code Extension:**
```bash
cd vscode-freezeguard/extension && npm install && npm run compile && npm test
```

**Collector API:**
```bash
cd collector && python3 -m pip install -r requirements.txt && python3 -m pytest tests/ -v
```

#### **End-to-End Integration Testing:**

**Prerequisites:**
```bash
python3 -m pip install -r requirements.txt  # Install integration test dependencies
```

**Execute Integration Test:**
```bash
# Start monitoring stack
cd ops && docker compose up -d && cd ..

# Run integration pipeline test
python3 test-integration.py
```

**What the Integration Test Does:**

Validates the complete telemetry pipeline by:
1. Checking collector connectivity
2. Injecting mock events with real action names via HTTP POST
3. Verifying events appear in Prometheus metrics with proper cross-platform thread labels
4. Confirming the full flow: `Mock Events → Collector → Prometheus → Grafana`

**View Results:**
- Open Grafana: http://localhost:3000
- Look for actions 'FreezeGuard.BadBlockingAction', 'freezeguard.badBlocking', etc. in performance dashboards
- Observe heatmaps showing injected stall patterns

### Troubleshooting

**Common Issues:**

1. **Integration test fails with "ModuleNotFoundError: requests"**
   - Solution: `python3 -m pip install -r requirements.txt`

2. **Integration test fails with "Connection refused"**
   - Solution: Start monitoring stack: `cd ops && docker compose up -d`

3. **VS Code tests fail with compilation errors**
   - Solution: `npm run compile` before `npm test`

4. **Mock events don't appear in Grafana**
   - Check collector logs: `docker logs fg-collector`
   - Verify Prometheus targets: http://localhost:9090/targets
   - Wait 15-30 seconds for metrics scraping interval

**Test Data Cleanup:**
```bash
# Stop and reset monitoring stack
cd ops && docker compose down && docker compose up -d
```

This testing architecture ensures the critical telemetry communication pipeline works reliably across both IntelliJ and VS Code environments, providing confidence in the performance monitoring system's core functionality.

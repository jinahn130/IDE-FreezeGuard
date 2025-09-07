# VSCode FreezeGuard

A VSCode extension that monitors main thread performance and detects UI freezes, equivalent to the IntelliJ FreezeGuard plugin.

## Features

- **Main Thread Monitoring**: Continuously monitors the main thread for stalls using setImmediate probes
- **Performance Actions**: Three demonstration commands showing different threading behaviors
- **Telemetry Collection**: Sends performance metrics to a FastAPI collector
- **Real-time Visualization**: Grafana dashboards for performance analysis

## Extension Commands

- `Freeze Guard: Measure Current Action` - Quick performance measurement
- `Freeze Guard: Run BAD Blocking Action` - Intentionally blocks main thread (~1.2s)
- `Freeze Guard: Run FIXED Background Action` - Proper background execution with progress

## Setup

### 1. Install Extension Dependencies
```bash
cd extension
npm install
npm run compile
```

### 2. Start Monitoring Stack
```bash
cd ops
docker compose up
```

### 3. Access Dashboards
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090
- Collector: http://localhost:8000/report

## Development

The extension uses:
- **Main Thread Detection**: `setImmediate()` probes to detect stalls ≥100ms
- **Thread Types**: `MAIN` (extension main thread) and `WORKER` (background tasks)
- **Telemetry**: HTTP POST to collector at `localhost:8000/ingest`

## Architecture

```
VSCode Extension → HTTP → FastAPI Collector → Prometheus → Grafana
```

Built to mirror the IntelliJ FreezeGuard functionality in the VSCode ecosystem.
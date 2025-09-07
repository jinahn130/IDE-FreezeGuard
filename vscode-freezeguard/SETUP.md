# VSCode FreezeGuard Setup Instructions

## Prerequisites

- **Node.js** (v16 or later)
- **npm** (comes with Node.js)
- **Docker** and **Docker Compose**
- **VSCode** (for testing the extension)

## Quick Start

### 1. Build the Extension

```bash
cd vscode-freezeguard/extension
npm install
npm run compile
```

### 2. Start the Monitoring Stack

```bash
cd ../ops
docker compose up -d
```

### 3. Install Extension in VSCode

#### Option A: Development Mode (Recommended)
1. Open VSCode
2. Press `F1` and run "Extensions: Install from VSIX..."
3. Or open the extension folder directly:
   ```bash
   cd extension
   code .
   ```
4. Press `F5` to launch a new VSCode window with the extension loaded

#### Option B: Manual Installation
1. Package the extension:
   ```bash
   cd extension
   npx vsce package
   ```
2. Install the generated `.vsix` file in VSCode

### 4. Test the Extension

1. In VSCode, open Command Palette (`Cmd/Ctrl + Shift + P`)
2. Search for "Freeze Guard" commands:
   - `Freeze Guard: Measure Current Action`
   - `Freeze Guard: Run BAD Blocking Action`
   - `Freeze Guard: Run FIXED Background Action`

### 5. View Metrics

- **Grafana Dashboard**: http://localhost:3000 (admin/admin)
- **Prometheus Metrics**: http://localhost:9090
- **Collector Report**: http://localhost:8000/report

## Detailed Setup

### Extension Development

```bash
# Navigate to extension directory
cd vscode-freezeguard/extension

# Install dependencies
npm install

# Compile TypeScript
npm run compile

# Watch mode for development (optional)
npm run watch
```

### Monitoring Stack

```bash
# Navigate to ops directory
cd vscode-freezeguard/ops

# Start all services
docker compose up

# Or run in background
docker compose up -d

# View logs
docker compose logs -f

# Stop services
docker compose down
```

### Testing the Extension

1. **Load Extension in Development**:
   ```bash
   cd extension
   code .
   # Press F5 to launch Extension Development Host
   ```

2. **Run Commands**:
   - Open any workspace in the Extension Development Host window
   - Use `Cmd/Ctrl + Shift + P` to open Command Palette
   - Type "Freeze Guard" to see available commands

3. **Verify Telemetry**:
   - Run any command
   - Check http://localhost:8000/report for events
   - View Grafana dashboard at http://localhost:3000

## Expected Behavior

### BadBlockingAction
- Blocks main thread for ~1.2 seconds
- Shows warning notification with performance metrics
- Should show stalls in monitoring dashboard

### BackgroundFixAction  
- Shows progress notification
- Runs work asynchronously without blocking
- Should show minimal/no stalls

### FreezeGuardAction
- Quick measurement action
- Shows info notification with timing
- Minimal performance impact

## Troubleshooting

### Extension Not Loading
```bash
# Check compilation errors
cd extension
npm run compile

# Check VSCode developer console
# Help -> Toggle Developer Tools
```

### Telemetry Not Working
```bash
# Check collector is running
curl http://localhost:8000/report

# Check docker services
docker compose ps

# View collector logs
docker compose logs vscode-collector
```

### No Metrics in Grafana
1. Verify Prometheus is scraping: http://localhost:9090/targets
2. Check if metrics exist: http://localhost:9090/graph (search for `vscode_events_total`)
3. Ensure Grafana datasource is configured

### Port Conflicts
If ports 3000, 8000, or 9090 are in use, modify `docker-compose.yml`:
```yaml
ports:
  - "3001:3000"  # Change external port
  - "8001:8000"
  - "9091:9090"
```

## Development Workflow

1. Make changes to extension code
2. Run `npm run compile` 
3. Reload Extension Development Host (`Cmd/Ctrl + R`)
4. Test changes
5. Check telemetry in Grafana dashboard

## File Structure
```
vscode-freezeguard/
├── extension/              # VSCode extension
│   ├── src/               # TypeScript source
│   ├── package.json       # Extension manifest
│   └── tsconfig.json      # TypeScript config
├── collector/             # FastAPI telemetry collector
│   ├── app.py            # Main API
│   ├── models.py         # Data models
│   └── Dockerfile        # Container build
├── ops/                  # Infrastructure
│   ├── docker-compose.yml
│   ├── prometheus.yml
│   └── grafana/          # Dashboard configs
└── README.md
```
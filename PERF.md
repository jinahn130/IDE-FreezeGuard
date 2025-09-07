# Freeze Guard – Performance Demo

## Versions
- IDE: IntelliJ IDEA (sandbox) – <fill in>
- Plugin: Intellij_FreezeGuard – <commit/tag>
- Collector: FastAPI <fill in>, Prometheus 2.54.1, Grafana 11.0.0

## Repro Steps
1) Start stack:
   ```bash
   cd ops
   docker compose up
    ```
Grafana: http://localhost:3000 (admin / admin)

Prometheus: http://localhost:9090

Collector: http://localhost:8000/report
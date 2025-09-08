# Changelog

IDE FreezeGuard is a cross-platform performance monitoring solution for IntelliJ IDEA and Visual Studio Code.
It tracks UI freezes, measures performance metrics, and provides telemetry for monitoring system health.

## [Unreleased]

### Added
- Cross-platform performance monitoring architecture for IntelliJ IDEA and VS Code
- EDT (Event Dispatch Thread) stall detection system with nanosecond precision timing
- Main thread monitoring for VS Code extension with setInterval-based heartbeat probes
- HTTP telemetry collection service with FastAPI backend and Prometheus metrics export
- BadBlockingAction demonstration showing intentional UI blocking (Thread.sleep 1200ms)
- BackgroundFixAction demonstration showing proper async threading with progress indicators
- FreezeGuardAction baseline performance measurement for establishing healthy metrics
- Comprehensive test coverage including unit tests, API tests, and integration tests
- GitHub Actions CI/CD pipeline with automated testing and artifact generation
- Docker Compose setup for monitoring infrastructure (collector + Grafana + Prometheus)
- Human-readable memory formatting utilities (bytes to KB/MB/GB conversion)
- User notification systems with balloon popups showing performance metrics
- Plugin verification and compatibility testing across multiple IntelliJ versions

### Changed
- Migrated from basic plugin template to comprehensive cross-platform monitoring solution
- Enhanced telemetry data structure to include heap usage, stall counts, and timing precision
- Improved error handling and network connectivity testing for telemetry transmission
- Updated CI pipeline to build and test both IntelliJ plugin and VS Code extension

### Fixed
- Thread safety issues in stall monitoring with AtomicInteger and AtomicLong counters
- Memory leak prevention through proper resource cleanup and daemon thread management
- Network timeout handling for telemetry HTTP requests with proper error reporting
- Cross-platform compatibility issues between IntelliJ (Java/Kotlin) and VS Code (TypeScript)

## [1.0.0] - Initial Release

### Added
- Initial project structure and basic monitoring capabilities
- Foundation for cross-platform IDE performance monitoring
- Basic telemetry collection and HTTP transmission infrastructure
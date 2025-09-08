#!/bin/bash

# Run all tests for IDE-FreezeGuard project
set -e

echo "=== IDE-FreezeGuard Test Suite ==="
echo

# Test IntelliJ Plugin
echo "1. Testing IntelliJ Plugin..."
cd intellij-freezeguard
./gradlew test
echo "✓ IntelliJ Plugin tests passed"
cd ..
echo

# Test VS Code Extension  
echo "2. Testing VS Code Extension..."
cd vscode-freezeguard/extension
npm install >/dev/null 2>&1
npm run compile >/dev/null 2>&1
npm test
echo "✓ VS Code Extension tests passed"
cd ../..
echo

# Test Collector API
echo "3. Testing Collector API..."
cd collector
python3 -m pip install -r requirements.txt >/dev/null 2>&1
python3 -m pytest tests/ -v
echo "✓ Collector API tests passed"
cd ..
echo

echo "=== All Unit Tests Passed ==="
echo
echo "To run integration tests:"
echo "1. Install dependencies: python3 -m pip install -r requirements.txt"
echo "2. Start the monitoring stack: cd ops && docker compose up -d && cd .."
echo "3. Run integration test: python3 test-integration.py"
echo
echo "To view results in Grafana:"
echo "- Open http://localhost:3000"
echo "- Look for actions 'FreezeGuard.BadBlockingAction', 'freezeguard.badBlocking', etc. in the dashboards"
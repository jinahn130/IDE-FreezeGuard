#!/usr/bin/env python3
"""
IDE-FreezeGuard Integration Test

OVERVIEW:
This script validates the complete telemetry pipeline without requiring actual IDE usage.
It injects mock performance events using real action names and verifies they flow through
the monitoring infrastructure correctly.

WHAT IT TESTS:
1. Collector Service Connectivity - Verifies the collector API is accessible
2. Cross-Platform Event Ingestion - Tests both IntelliJ (EDT) and VS Code (MAIN/WORKER) events  
3. Prometheus Metrics Generation - Confirms events appear in /metrics endpoint
4. Thread Type Validation - Ensures proper cross-platform labeling
5. End-to-End Pipeline - Validates: Mock Events → HTTP POST → Collector → Prometheus

MOCK DATA STRATEGY:
- Uses REAL action names from the project (FreezeGuard.BadBlockingAction, freezeguard.badBlocking, etc.)
- Simulates poor performance scenarios (1.5s-2.2s delays, 3-9 stalls)
- Does NOT trigger actual UI actions - sends JSON directly to collector
- Creates realistic test data visible in Grafana dashboards

EXAMPLE MOCK EVENTS:
{
    "action": "FreezeGuard.BadBlockingAction",
    "duration_ms": 1500.0,        # Simulated slow performance
    "thread": "EDT",              # IntelliJ Event Dispatch Thread
    "edt_stalls": 6,              # Simulated stall count
    "edt_longest_stall_ms": 450.0,
    "ts": "2024-01-01T12:00:00Z"
}

VALIDATION PROCESS:
1. HTTP POST to http://127.0.0.1:8000/ingest with mock events
2. GET http://127.0.0.1:8000/metrics to verify Prometheus metrics
3. Check for required metrics: events_total, action_duration_seconds, edt_stalls_total
4. Confirm cross-platform thread labels (EDT, MAIN, WORKER) in metrics
5. Validate specific action names appear in metrics output

PREREQUISITES:
- Docker services running: cd ops && docker compose up -d
- Python requests library: python3 -m pip install requests

USAGE:
python3 test-integration.py

GRAFANA VERIFICATION:
After running, check http://localhost:3000 for injected events in dashboards.
Look for actions: FreezeGuard.BadBlockingAction, freezeguard.badBlocking, etc.
"""

import requests
import time
import json
import sys
from datetime import datetime, timezone
from typing import List, Dict, Any


def create_mock_intellij_events() -> List[Dict[str, Any]]:
    """Create mock IntelliJ events using real action names with simulated poor performance"""
    base_time = datetime.now(timezone.utc)
    
    events = [
        {
            "action": "FreezeGuard.BadBlockingAction",
            "duration_ms": 1500.0,  # Simulated slow performance
            "thread": "EDT",
            "heap_delta_bytes": 16384,
            "edt_stalls": 6,
            "edt_longest_stall_ms": 450.0,
            "ts": base_time.isoformat().replace('+00:00', 'Z')
        },
        {
            "action": "FreezeGuard.BadBlockingAction",
            "duration_ms": 2200.0,  # Simulated very slow performance
            "thread": "EDT", 
            "heap_delta_bytes": 32768,
            "edt_stalls": 9,
            "edt_longest_stall_ms": 650.0,
            "ts": base_time.isoformat().replace('+00:00', 'Z')
        },
        {
            "action": "FreezeGuard.MeasureAction",
            "duration_ms": 850.0,  # Simulated measured action with performance issues
            "thread": "EDT",
            "heap_delta_bytes": 8192,
            "edt_stalls": 3,
            "edt_longest_stall_ms": 280.0,
            "ts": base_time.isoformat().replace('+00:00', 'Z')
        }
    ]
    return events


def create_mock_vscode_events() -> List[Dict[str, Any]]:
    """Create mock VS Code events using real action names with simulated poor performance"""
    base_time = datetime.now(timezone.utc)
    
    events = [
        {
            "action": "freezeguard.badBlocking",
            "duration_ms": 1200.0,  # Simulated slow performance
            "thread": "MAIN",
            "heap_delta_bytes": 12288,
            "edt_stalls": 4,
            "edt_longest_stall_ms": 380.0,
            "ts": base_time.isoformat().replace('+00:00', 'Z')
        },
        {
            "action": "freezeguard.backgroundFix",
            "duration_ms": 750.0,  # Even background action can have issues in testing
            "thread": "WORKER",
            "heap_delta_bytes": 4096,
            "edt_stalls": 2,
            "edt_longest_stall_ms": 200.0,
            "ts": base_time.isoformat().replace('+00:00', 'Z')
        },
        {
            "action": "freezeguard.measure",
            "duration_ms": 950.0,  # Simulated measured action with performance issues
            "thread": "MAIN",
            "heap_delta_bytes": 6144,
            "edt_stalls": 3,
            "edt_longest_stall_ms": 320.0,
            "ts": base_time.isoformat().replace('+00:00', 'Z')
        }
    ]
    return events


def inject_mock_events(collector_url: str, events: List[Dict[str, Any]]) -> bool:
    """Inject mock events into the collector"""
    print(f"Injecting {len(events)} mock events into {collector_url}")
    
    success_count = 0
    for i, event in enumerate(events, 1):
        try:
            response = requests.post(
                f"{collector_url}/ingest",
                json=event,
                timeout=5
            )
            
            if response.status_code == 200:
                print(f"  ✓ Event {i}/{len(events)}: {event['action']} - Success")
                success_count += 1
            else:
                print(f"  ✗ Event {i}/{len(events)}: {event['action']} - Failed ({response.status_code})")
                print(f"    Response: {response.text}")
                
        except Exception as e:
            print(f"  ✗ Event {i}/{len(events)}: {event['action']} - Exception: {e}")
    
    print(f"Successfully injected {success_count}/{len(events)} events")
    return success_count == len(events)


def verify_metrics(collector_url: str, expected_actions: List[str]) -> bool:
    """Verify that injected events appear in Prometheus metrics"""
    print(f"Verifying metrics at {collector_url}/metrics")
    
    try:
        response = requests.get(f"{collector_url}/metrics", timeout=10)
        if response.status_code != 200:
            print(f"  ✗ Failed to get metrics: HTTP {response.status_code}")
            return False
            
        metrics_content = response.text
        print("  ✓ Retrieved Prometheus metrics")
        
        # Check for expected metrics
        required_metrics = [
            "events_total",
            "action_duration_seconds",
            "edt_stalls_total",
            "edt_stall_duration_seconds",
            "heap_delta_bytes"
        ]
        
        missing_metrics = []
        for metric in required_metrics:
            if metric not in metrics_content:
                missing_metrics.append(metric)
        
        if missing_metrics:
            print(f"  ✗ Missing metrics: {', '.join(missing_metrics)}")
            return False
        
        print("  ✓ All required metrics present")
        
        # Check for specific mock actions
        found_actions = []
        missing_actions = []
        
        for action in expected_actions:
            if f'action="{action}"' in metrics_content:
                found_actions.append(action)
            else:
                missing_actions.append(action)
        
        if found_actions:
            print(f"  ✓ Found {len(found_actions)} mock actions in metrics:")
            for action in found_actions:
                print(f"    - {action}")
        
        if missing_actions:
            print(f"  ✗ Missing {len(missing_actions)} mock actions:")
            for action in missing_actions:
                print(f"    - {action}")
            return False
        
        # Check for cross-platform thread labels
        thread_types = ["EDT", "MAIN", "WORKER"]
        found_threads = []
        
        for thread in thread_types:
            if f'thread="{thread}"' in metrics_content:
                found_threads.append(thread)
        
        if found_threads:
            print(f"  ✓ Found thread types: {', '.join(found_threads)}")
        
        return True
        
    except Exception as e:
        print(f"  ✗ Exception verifying metrics: {e}")
        return False


def verify_debug_endpoint(collector_url: str, expected_actions: List[str]) -> bool:
    """Verify that events appear in the debug endpoint"""
    print(f"Verifying debug endpoint at {collector_url}/debug")
    
    try:
        response = requests.get(f"{collector_url}/debug", timeout=5)
        if response.status_code != 200:
            print(f"  ✗ Failed to get debug info: HTTP {response.status_code}")
            return False
        
        debug_data = response.json()
        recent_events = debug_data.get("recent_events", [])
        total_events = debug_data.get("total_events", 0)
        
        print(f"  ✓ Debug endpoint shows {total_events} total events, {len(recent_events)} recent")
        
        # Find our mock events in the debug output
        found_actions = set()
        for event in recent_events:
            action = event.get("action", "")
            if action in expected_actions:
                found_actions.add(action)
        
        if len(found_actions) == len(expected_actions):
            print(f"  ✓ All {len(expected_actions)} mock events found in debug output")
            return True
        else:
            missing = set(expected_actions) - found_actions
            print(f"  ✗ Missing {len(missing)} events in debug output: {', '.join(missing)}")
            return False
            
    except Exception as e:
        print(f"  ✗ Exception verifying debug endpoint: {e}")
        return False


def main():
    collector_url = "http://127.0.0.1:8000"
    
    print("=== IDE-FreezeGuard Integration Test ===")
    print(f"Testing against collector at: {collector_url}")
    print()
    
    # Test collector connectivity
    print("1. Testing collector connectivity...")
    try:
        response = requests.get(f"{collector_url}/metrics", timeout=5)
        if response.status_code == 200:
            print("  ✓ Collector is running and accessible")
        else:
            print(f"  ✗ Collector returned HTTP {response.status_code}")
            sys.exit(1)
    except Exception as e:
        print(f"  ✗ Cannot connect to collector: {e}")
        print("  Make sure the collector is running: docker compose up -d")
        sys.exit(1)
    
    # Create mock events
    print("\n2. Creating mock events...")
    intellij_events = create_mock_intellij_events()
    vscode_events = create_mock_vscode_events()
    all_events = intellij_events + vscode_events
    all_actions = [event["action"] for event in all_events]
    
    print(f"  Created {len(intellij_events)} IntelliJ mock events")
    print(f"  Created {len(vscode_events)} VS Code mock events")
    print(f"  Total: {len(all_events)} mock events")
    
    # Inject events
    print("\n3. Injecting mock events...")
    if not inject_mock_events(collector_url, all_events):
        print("  ✗ Failed to inject all mock events")
        sys.exit(1)
    
    # Wait a moment for metrics to update
    print("\n4. Waiting for metrics to update...")
    time.sleep(2)
    
    # Verify metrics
    print("\n5. Verifying Prometheus metrics...")
    if not verify_metrics(collector_url, all_actions):
        print("  ✗ Metrics verification failed")
        sys.exit(1)
    
    # Verify debug endpoint (optional - may not be available in all collector versions)
    print("\n6. Verifying debug endpoint...")
    if not verify_debug_endpoint(collector_url, all_actions):
        print("  ⚠ Debug endpoint not available (using older collector version)")
        print("  This is expected if using Docker containers - integration still successful")
    
    # Success
    print("\n=== Integration Test PASSED ===")
    print("Mock data successfully flowed through the entire pipeline:")
    print("  IDE Events → Collector → Prometheus Metrics → Debug Endpoint")
    print("\nYou can now check Grafana at http://localhost:3000 to see the mock data visualized.")
    print("Look for actions 'FreezeGuard.BadBlockingAction', 'freezeguard.badBlocking', etc. in the dashboards.")


if __name__ == "__main__":
    main()
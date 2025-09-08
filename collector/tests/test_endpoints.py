import pytest
from fastapi.testclient import TestClient
from datetime import datetime
import json

from app import app

"""
Collector API Tests

PURPOSE:
Tests the FastAPI collector service that ingests telemetry events from both IntelliJ 
and VS Code extensions. Validates HTTP endpoints, data validation, cross-platform 
event handling, and Prometheus metrics generation.

WHAT IT TESTS:
1. /ingest Endpoint Validation
   - Accepts valid IntelliJ events (EDT thread)
   - Accepts valid VS Code events (MAIN/WORKER threads) 
   - Rejects invalid thread types and malformed JSON
   - Handles missing required fields appropriately

2. /metrics Endpoint Validation
   - Returns Prometheus format metrics
   - Includes required metric types (events_total, action_duration_seconds, etc.)
   - Shows cross-platform thread labels correctly
   - Updates metrics after event ingestion

3. Cross-Platform Event Processing
   - Validates different thread types (EDT, MAIN, WORKER)
   - Ensures consistent metric labeling across platforms
   - Tests realistic performance scenarios from both IDEs

MOCK DATA APPROACH:
- Creates realistic events using actual performance scenarios
- Simulates various thread types and performance patterns
- Tests error conditions (malformed JSON, invalid fields)
- Validates metric generation from ingested events

WHY THESE TESTS MATTER:
Ensures the collector can reliably handle telemetry from both IDE platforms,
properly validate incoming data, and generate accurate Prometheus metrics
for monitoring dashboards.
"""


@pytest.fixture
def client():
    return TestClient(app)


class TestIngestEndpoint:
    
    def test_ingest_intellij_mock_event(self, client):
        """Test ingesting a mock IntelliJ event"""
        mock_event = {
            "action": "MOCK_INTELLIJ_STALL_TEST",
            "duration_ms": 850.0,
            "thread": "EDT",
            "heap_delta_bytes": 3072,
            "edt_stalls": 4,
            "edt_longest_stall_ms": 275.0,
            "ts": "2024-01-01T12:00:00.000Z"
        }
        
        response = client.post("/ingest", json=mock_event)
        assert response.status_code == 200
        
        result = response.json()
        assert result["ok"] is True
    
    def test_ingest_vscode_mock_event(self, client):
        """Test ingesting a mock VS Code event"""
        mock_event = {
            "action": "MOCK_VSCODE_STALL_TEST", 
            "duration_ms": 650.5,
            "thread": "MAIN",
            "heap_delta_bytes": 2048,
            "edt_stalls": 3,
            "edt_longest_stall_ms": 220.5,
            "ts": "2024-01-01T12:30:00.000Z"
        }
        
        response = client.post("/ingest", json=mock_event)
        assert response.status_code == 200
        
        result = response.json()
        assert result["ok"] is True
    
    def test_ingest_worker_thread_event(self, client):
        """Test ingesting VS Code worker thread event"""
        mock_event = {
            "action": "MOCK_VSCODE_WORKER_STALL",
            "duration_ms": 450.0,
            "thread": "WORKER",
            "heap_delta_bytes": 1536,
            "edt_stalls": 2,
            "edt_longest_stall_ms": 180.0,
            "ts": "2024-01-01T13:00:00.000Z"
        }
        
        response = client.post("/ingest", json=mock_event)
        assert response.status_code == 200
    
    def test_ingest_invalid_thread_type(self, client):
        """Test rejection of invalid thread type"""
        invalid_event = {
            "action": "INVALID_THREAD_TEST",
            "duration_ms": 100.0,
            "thread": "INVALID_THREAD",  # Not in allowed list
            "heap_delta_bytes": 1024,
            "edt_stalls": 1,
            "edt_longest_stall_ms": 50.0,
            "ts": "2024-01-01T14:00:00.000Z"
        }
        
        response = client.post("/ingest", json=invalid_event)
        assert response.status_code == 422  # FastAPI returns 422 for validation errors
    
    def test_ingest_malformed_json(self, client):
        """Test handling of malformed JSON"""
        response = client.post(
            "/ingest",
            content='{"action": "test", "invalid": json}',
            headers={"content-type": "application/json"}
        )
        assert response.status_code == 400
        assert "json parse" in response.json()["error"]
    
    def test_ingest_missing_required_fields(self, client):
        """Test rejection of events with missing fields"""
        incomplete_event = {
            "action": "INCOMPLETE_TEST",
            "duration_ms": 100.0,
            # Missing thread, heap_delta_bytes, etc.
        }
        
        response = client.post("/ingest", json=incomplete_event)
        assert response.status_code == 422  # FastAPI returns 422 for validation errors


class TestMetricsEndpoint:
    
    def test_metrics_endpoint_returns_prometheus_format(self, client):
        """Test that metrics endpoint returns Prometheus format"""
        response = client.get("/metrics")
        assert response.status_code == 200
        assert response.headers["content-type"] == "text/plain; version=0.0.4; charset=utf-8"
        
        content = response.text
        assert "# HELP" in content
        assert "# TYPE" in content
    
    def test_metrics_after_mock_events(self, client):
        """Test that metrics reflect ingested mock events"""
        # Send several mock events
        mock_events = [
            generate_mock_intellij_event("MOCK_SLOW_STARTUP"),
            generate_mock_vscode_event("MOCK_EXTENSION_LOAD"), 
            generate_mock_intellij_event("MOCK_FILE_INDEXING")
        ]
        
        for event in mock_events:
            response = client.post("/ingest", json=event)
            assert response.status_code == 200
        
        # Check that metrics endpoint shows the events
        metrics_response = client.get("/metrics")
        assert metrics_response.status_code == 200
        
        metrics_content = metrics_response.text
        assert "events_total" in metrics_content
        assert "action_duration_seconds" in metrics_content


class TestDebugEndpoint:
    
    def test_debug_endpoint_shows_recent_events(self, client):
        """Test debug endpoint shows recent events"""
        # Send a mock event
        mock_event = generate_mock_intellij_event("DEBUG_TEST_EVENT")
        client.post("/ingest", json=mock_event)
        
        # Check debug endpoint
        response = client.get("/debug")
        assert response.status_code == 200
        
        debug_data = response.json()
        assert "recent_events" in debug_data
        assert len(debug_data["recent_events"]) > 0
        
        # Find our test event
        test_event = next(
            (e for e in debug_data["recent_events"] if e["action"] == "DEBUG_TEST_EVENT"),
            None
        )
        assert test_event is not None
        assert test_event["thread"] == "EDT"


def generate_mock_intellij_event(action_name: str) -> dict:
    """Generate a realistic mock IntelliJ event for testing"""
    import random
    
    return {
        "action": action_name,
        "duration_ms": round(random.uniform(200.0, 1500.0), 3),
        "thread": "EDT",
        "heap_delta_bytes": random.randint(1024, 16384),
        "edt_stalls": random.randint(1, 8),
        "edt_longest_stall_ms": round(random.uniform(100.0, 500.0), 3),
        "ts": datetime.now().isoformat() + "Z"
    }


def generate_mock_vscode_event(action_name: str) -> dict:
    """Generate a realistic mock VS Code event for testing"""
    import random
    
    return {
        "action": action_name,
        "duration_ms": round(random.uniform(150.0, 1200.0), 3),
        "thread": random.choice(["MAIN", "WORKER"]),
        "heap_delta_bytes": random.randint(512, 8192),
        "edt_stalls": random.randint(1, 6),
        "edt_longest_stall_ms": round(random.uniform(80.0, 400.0), 3),
        "ts": datetime.now().isoformat() + "Z"
    }
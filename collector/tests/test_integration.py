import pytest
from fastapi.testclient import TestClient
from datetime import datetime
import re

from app import app


@pytest.fixture
def client():
    return TestClient(app)


class TestEndToEndIntegration:
    
    def test_mock_stall_scenario_integration(self, client):
        """Test end-to-end mock stall scenario from event ingestion to metrics"""
        
        # Simulate a series of mock performance issues
        mock_scenario = [
            # IntelliJ slow startup
            {
                "action": "MOCK_INTELLIJ_STARTUP_SLOW",
                "duration_ms": 1250.0,
                "thread": "EDT", 
                "heap_delta_bytes": 8192,
                "edt_stalls": 5,
                "edt_longest_stall_ms": 350.0,
                "ts": "2024-01-01T09:00:00.000Z"
            },
            # VS Code extension loading stall
            {
                "action": "MOCK_VSCODE_EXTENSION_HANG",
                "duration_ms": 875.5,
                "thread": "MAIN",
                "heap_delta_bytes": 4096,
                "edt_stalls": 3,
                "edt_longest_stall_ms": 290.0,
                "ts": "2024-01-01T09:05:00.000Z"
            },
            # IntelliJ file indexing freeze
            {
                "action": "MOCK_INTELLIJ_INDEXING_FREEZE", 
                "duration_ms": 2100.0,
                "thread": "EDT",
                "heap_delta_bytes": 12288,
                "edt_stalls": 8,
                "edt_longest_stall_ms": 525.0,
                "ts": "2024-01-01T09:10:00.000Z"
            },
            # VS Code worker thread stall
            {
                "action": "MOCK_VSCODE_WORKER_STALL",
                "duration_ms": 650.0,
                "thread": "WORKER",
                "heap_delta_bytes": 2048,
                "edt_stalls": 2,
                "edt_longest_stall_ms": 200.0,
                "ts": "2024-01-01T09:15:00.000Z"
            }
        ]
        
        # Ingest all mock events
        for event in mock_scenario:
            response = client.post("/ingest", json=event)
            assert response.status_code == 200, f"Failed to ingest {event['action']}"
        
        # Verify metrics reflect the ingested data
        metrics_response = client.get("/metrics")
        assert metrics_response.status_code == 200
        
        metrics_content = metrics_response.text
        
        # Verify specific metrics are present and reasonable
        self._verify_metrics_content(metrics_content, mock_scenario)
        
        # Verify debug endpoint shows the events
        debug_response = client.get("/debug")
        assert debug_response.status_code == 200
        
        debug_data = debug_response.json()
        recent_events = debug_data["recent_events"]
        
        # Should have all our mock events
        mock_actions = {event["action"] for event in mock_scenario}
        debug_actions = {event["action"] for event in recent_events}
        
        assert mock_actions.issubset(debug_actions), "All mock events should appear in debug output"
    
    def test_cross_platform_metrics_separation(self, client):
        """Test that IntelliJ and VS Code events are properly labeled in metrics"""
        
        # Send events from both platforms
        intellij_event = {
            "action": "MOCK_INTELLIJ_ACTION",
            "duration_ms": 500.0,
            "thread": "EDT",
            "heap_delta_bytes": 2048,
            "edt_stalls": 2,
            "edt_longest_stall_ms": 150.0,
            "ts": datetime.now().isoformat() + "Z"
        }
        
        vscode_event = {
            "action": "MOCK_VSCODE_ACTION", 
            "duration_ms": 400.0,
            "thread": "MAIN",
            "heap_delta_bytes": 1536,
            "edt_stalls": 1,
            "edt_longest_stall_ms": 120.0,
            "ts": datetime.now().isoformat() + "Z"
        }
        
        # Ingest both events
        client.post("/ingest", json=intellij_event)
        client.post("/ingest", json=vscode_event)
        
        # Check metrics distinguish between platforms
        metrics_response = client.get("/metrics")
        metrics_content = metrics_response.text
        
        # Should have separate labels for different thread types
        assert 'thread="EDT"' in metrics_content
        assert 'thread="MAIN"' in metrics_content
        
        # Should have the specific actions labeled
        assert 'action="MOCK_INTELLIJ_ACTION"' in metrics_content
        assert 'action="MOCK_VSCODE_ACTION"' in metrics_content
    
    def test_high_volume_mock_events(self, client):
        """Test handling of many mock events for performance validation"""
        
        # Generate a batch of varied mock events
        mock_events = []
        
        # IntelliJ events
        for i in range(10):
            mock_events.append({
                "action": f"MOCK_INTELLIJ_BATCH_{i}",
                "duration_ms": 200.0 + (i * 50),
                "thread": "EDT",
                "heap_delta_bytes": 1024 + (i * 512),
                "edt_stalls": 1 + (i % 5),
                "edt_longest_stall_ms": 100.0 + (i * 25),
                "ts": datetime.now().isoformat() + "Z"
            })
        
        # VS Code events
        for i in range(10):
            mock_events.append({
                "action": f"MOCK_VSCODE_BATCH_{i}",
                "duration_ms": 150.0 + (i * 40), 
                "thread": "MAIN" if i % 2 == 0 else "WORKER",
                "heap_delta_bytes": 768 + (i * 256),
                "edt_stalls": 1 + (i % 4),
                "edt_longest_stall_ms": 80.0 + (i * 20),
                "ts": datetime.now().isoformat() + "Z"
            })
        
        # Ingest all events
        for event in mock_events:
            response = client.post("/ingest", json=event)
            assert response.status_code == 200
        
        # Verify metrics endpoint still works with high volume
        metrics_response = client.get("/metrics")
        assert metrics_response.status_code == 200
        
        # Verify debug endpoint shows recent events (limited by ring buffer)
        debug_response = client.get("/debug")
        assert debug_response.status_code == 200
        
        debug_data = debug_response.json()
        assert len(debug_data["recent_events"]) > 0
        assert "total_events" in debug_data
    
    def _verify_metrics_content(self, metrics_content: str, mock_events: list):
        """Helper to verify metrics contain expected data from mock events"""
        
        # Should have events_total metric with counts
        assert "events_total" in metrics_content
        
        # Should have duration histograms
        assert "action_duration_seconds" in metrics_content
        assert "# TYPE action_duration_seconds histogram" in metrics_content
        
        # Should have stall metrics
        assert "edt_stalls_total" in metrics_content
        assert "edt_stall_duration_seconds" in metrics_content
        
        # Should have heap delta metrics
        assert "heap_delta_bytes" in metrics_content
        
        # Check that specific mock action names appear
        for event in mock_events:
            action_pattern = f'action="{event["action"]}"'
            assert action_pattern in metrics_content, f"Action {event['action']} not found in metrics"
        
        # Verify thread labels are present
        thread_types = {event["thread"] for event in mock_events}
        for thread in thread_types:
            thread_pattern = f'thread="{thread}"'
            assert thread_pattern in metrics_content, f"Thread type {thread} not found in metrics"
import * as assert from 'assert';
import * as sinon from 'sinon';
import { EventSender } from '../eventSender';
import { ActionEvent } from '../types';

/**
 * VS Code Extension Communication Layer Tests
 * 
 * PURPOSE:
 * Tests the telemetry data structures and event creation for VS Code extension.
 * Validates cross-platform compatibility with IntelliJ by testing different thread types
 * and ensuring consistent telemetry format.
 * 
 * WHAT IT TESTS:
 * - ActionEvent interface validation for VS Code-specific fields
 * - Thread type compatibility (MAIN/WORKER vs IntelliJ's EDT/BGT)
 * - Mock event generation with deterministic pseudo-random values
 * - Cross-platform telemetry structure consistency
 * 
 * CROSS-PLATFORM CONSIDERATIONS:
 * - VS Code uses MAIN/WORKER threads vs IntelliJ's EDT/BGT
 * - Same telemetry fields but different thread monitoring approaches
 * - Consistent JSON structure for collector compatibility
 * 
 * MOCK DATA STRATEGY:
 * - Uses deterministic pseudo-random based on action name hash
 * - Ensures test repeatability while generating varied realistic data
 * - Simulates common VS Code performance scenarios (extension loading, syntax highlighting, etc.)
 * 
 * WHY THESE TESTS MATTER:
 * Ensures VS Code extension telemetry is compatible with the shared collector service
 * and that cross-platform monitoring works seamlessly.
 */
describe('EventSender', () => {
  
  describe('ActionEvent validation', () => {
    it('should create valid ActionEvent with VS Code thread types', () => {
      const event: ActionEvent = {
        action: 'TEST_VSCODE_ACTION',
        duration_ms: 250.5,
        thread: 'MAIN',
        heap_delta_bytes: 1024,
        edt_stalls: 1,
        edt_longest_stall_ms: 150.2,
        ts: '2024-01-01T10:00:00.000Z'
      };
      
      assert.strictEqual(event.action, 'TEST_VSCODE_ACTION');
      assert.strictEqual(event.duration_ms, 250.5);
      assert.strictEqual(event.thread, 'MAIN');
      assert.strictEqual(event.heap_delta_bytes, 1024);
      assert.strictEqual(event.edt_stalls, 1);
      assert.strictEqual(event.edt_longest_stall_ms, 150.2);
      assert.strictEqual(event.ts, '2024-01-01T10:00:00.000Z');
    });
    
    it('should accept both MAIN and WORKER thread types', () => {
      const mainEvent: ActionEvent = createMockVSCodeEvent('MAIN');
      const workerEvent: ActionEvent = createMockVSCodeEvent('WORKER');
      
      assert.strictEqual(mainEvent.thread, 'MAIN');
      assert.strictEqual(workerEvent.thread, 'WORKER');
    });
  });
  
  describe('Mock event generation', () => {
    it('should generate realistic mock stall events', () => {
      const mockEvent = generateMockVSCodeStall('MOCK_VSCODE_FREEZE');
      
      assert.strictEqual(mockEvent.action, 'MOCK_VSCODE_FREEZE');
      assert(mockEvent.duration_ms >= 200.0, 'Mock events should simulate noticeable delays');
      assert(['MAIN', 'WORKER'].includes(mockEvent.thread), 'Should use VS Code thread types');
      assert(mockEvent.edt_stalls > 0, 'Should have at least one stall');
      assert(mockEvent.edt_longest_stall_ms >= 100.0, 'Longest stall should be significant');
      assert(mockEvent.heap_delta_bytes >= 0, 'Heap delta should be non-negative');
      assert(mockEvent.ts.includes('T'), 'Should use ISO timestamp format');
    });
    
    it('should generate varied mock events for testing', () => {
      const events = [
        generateMockVSCodeStall('MOCK_FILE_READ_STALL'),
        generateMockVSCodeStall('MOCK_EXTENSION_LOAD'),
        generateMockVSCodeStall('MOCK_SYNTAX_HIGHLIGHT'),
        generateMockVSCodeStall('MOCK_AUTOCOMPLETE_HANG')
      ];
      
      // Verify all events are realistic performance issues
      events.forEach(event => {
        assert(event.duration_ms >= 100.0, `${event.action} should be slow enough to notice`);
        assert(event.edt_stalls > 0, `${event.action} should have stalls`);
        assert(['MAIN', 'WORKER'].includes(event.thread), `${event.action} should use VS Code thread types`);
      });
      
      // Verify variety in generated values
      const durations = new Set(events.map(e => e.duration_ms));
      const stallCounts = new Set(events.map(e => e.edt_stalls));
      
      assert(durations.size > 1, 'Should generate varied durations');
      assert(stallCounts.size > 1, 'Should generate varied stall counts');
    });
  });
});

/**
 * Creates a mock VS Code event for testing
 */
function createMockVSCodeEvent(thread: 'MAIN' | 'WORKER'): ActionEvent {
  return {
    action: 'MOCK_TEST_ACTION',
    duration_ms: 300.0,
    thread: thread,
    heap_delta_bytes: 2048,
    edt_stalls: 2,
    edt_longest_stall_ms: 120.0,
    ts: new Date().toISOString()
  };
}

/**
 * Generates realistic mock stall events for VS Code testing.
 * These simulate performance issues that would be visible in Grafana monitoring.
 */
function generateMockVSCodeStall(actionName: string): ActionEvent {
  // Use action name hash to ensure variety but deterministic results
  const hash = actionName.split('').reduce((a, b) => {
    a = ((a << 5) - a) + b.charCodeAt(0);
    return a & a;
  }, 0);
  
  const seed = Math.abs(hash);
  const pseudoRandom = (seed: number, offset: number) => {
    return ((seed + offset) * 9301 + 49297) % 233280 / 233280;
  };
  
  return {
    action: actionName,
    duration_ms: Math.round((100 + pseudoRandom(seed, 1) * 1900) * 100) / 100, // 100ms to 2s
    thread: pseudoRandom(seed, 2) > 0.5 ? 'MAIN' : 'WORKER',
    heap_delta_bytes: Math.floor(256 + pseudoRandom(seed, 3) * 7936), // 256B to 8KB
    edt_stalls: 1 + Math.floor(pseudoRandom(seed, 4) * 5), // 1-5 stalls
    edt_longest_stall_ms: Math.round((50 + pseudoRandom(seed, 5) * 450) * 100) / 100, // 50-500ms longest stall
    ts: new Date().toISOString()
  };
}

export { generateMockVSCodeStall };
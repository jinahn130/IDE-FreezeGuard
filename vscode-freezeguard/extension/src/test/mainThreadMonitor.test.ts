import * as assert from 'assert';
import * as sinon from 'sinon';

describe('MainThreadMonitor Mock Tests', () => {
  
  describe('Stall detection simulation', () => {
    it('should simulate stall detection logic', () => {
      // Mock the stall detection logic without actual timers
      const mockStallDetector = new MockStallDetector(50, 100); // 50ms interval, 100ms threshold
      
      // Simulate normal timing (no stall)
      mockStallDetector.simulateProbe(55); // 5ms late - under threshold
      assert.strictEqual(mockStallDetector.getStallCount(), 0);
      
      // Simulate stall timing
      mockStallDetector.simulateProbe(180); // 130ms late - over threshold
      assert.strictEqual(mockStallDetector.getStallCount(), 1);
      assert.strictEqual(mockStallDetector.getLongestStall(), 130);
      
      // Simulate multiple stalls
      mockStallDetector.simulateProbe(220); // 170ms late
      mockStallDetector.simulateProbe(90);  // 40ms late - under threshold
      mockStallDetector.simulateProbe(350); // 300ms late - major stall
      
      assert.strictEqual(mockStallDetector.getStallCount(), 3);
      assert.strictEqual(mockStallDetector.getLongestStall(), 300);
    });
    
    it('should handle edge cases in stall detection', () => {
      const detector = new MockStallDetector(50, 100);
      
      // Exactly at threshold should count as stall
      detector.simulateProbe(150); // exactly 100ms late
      assert.strictEqual(detector.getStallCount(), 1);
      
      // Just under threshold should not count
      detector.simulateProbe(149); // 99ms late
      assert.strictEqual(detector.getStallCount(), 1); // still 1, no new stall
      
      // Reset and test zero delay
      const detector2 = new MockStallDetector(50, 100);
      detector2.simulateProbe(50); // exactly on time
      assert.strictEqual(detector2.getStallCount(), 0);
    });
  });
  
  describe('Performance test data generation', () => {
    it('should generate realistic test stall patterns', () => {
      const testPattern = generateTestStallPattern(10); // 10 probes
      
      assert.strictEqual(testPattern.length, 10);
      
      // Should have a mix of normal and stall timings
      const normalTimings = testPattern.filter(t => t <= 100);
      const stallTimings = testPattern.filter(t => t > 100);
      
      assert(normalTimings.length > 0, 'Should have some normal timings');
      assert(stallTimings.length > 0, 'Should have some stall timings');
      
      // Stalls should be realistic (not too extreme)
      stallTimings.forEach(timing => {
        assert(timing <= 2000, `Stall timing ${timing}ms should be under 2 seconds`);
        assert(timing >= 100, `Stall timing ${timing}ms should be over threshold`);
      });
    });
  });
});

/**
 * Mock stall detector for testing without actual timers
 */
class MockStallDetector {
  private stallCount = 0;
  private longestStall = 0;
  private expectedInterval: number;
  private stallThreshold: number;
  
  constructor(intervalMs: number, thresholdMs: number) {
    this.expectedInterval = intervalMs;
    this.stallThreshold = thresholdMs;
  }
  
  simulateProbe(actualInterval: number): void {
    const delay = actualInterval - this.expectedInterval;
    
    if (delay >= this.stallThreshold) {
      this.stallCount++;
      this.longestStall = Math.max(this.longestStall, delay);
    }
  }
  
  getStallCount(): number {
    return this.stallCount;
  }
  
  getLongestStall(): number {
    return this.longestStall;
  }
  
  reset(): void {
    this.stallCount = 0;
    this.longestStall = 0;
  }
}

/**
 * Generates a realistic pattern of timing intervals for testing.
 * Mix of normal timings and stalls to simulate real-world conditions.
 */
function generateTestStallPattern(probeCount: number): number[] {
  const pattern: number[] = [];
  
  // Ensure we always have at least one normal and one stall timing
  let normalCount = 0;
  let stallCount = 0;
  
  for (let i = 0; i < probeCount; i++) {
    const shouldStall = Math.random() < 0.4 || (i >= probeCount - 2 && stallCount === 0);
    const shouldNormal = !shouldStall || (i >= probeCount - 2 && normalCount === 0);
    
    if (shouldStall && !shouldNormal) {
      // Generate stall timing (150ms to 800ms - over 100ms threshold)
      pattern.push(150 + Math.random() * 650);
      stallCount++;
    } else {
      // Generate normal timing (30ms to 90ms - under 100ms threshold)
      pattern.push(30 + Math.random() * 60);
      normalCount++;
    }
  }
  
  return pattern;
}

export { MockStallDetector, generateTestStallPattern };
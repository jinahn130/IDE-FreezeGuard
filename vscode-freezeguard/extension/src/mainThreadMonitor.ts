import { StallSnapshot } from './types';

export class MainThreadMonitor {
  private periodMs: number = 50;
  private stallThresholdMs: number = 100;
  private stallCount: number = 0;
  private longestStallMs: number = 0;
  private intervalId: NodeJS.Timeout | null = null;
  private lastProbeTime: number = Date.now();

  start(): void {
    if (this.intervalId) return;
    
    this.lastProbeTime = Date.now();
    
    this.intervalId = setInterval(() => {
      const now = Date.now();
      const expectedInterval = this.periodMs;
      const actualInterval = now - this.lastProbeTime;
      const delayMs = actualInterval - expectedInterval;
      
      // If the actual interval is significantly longer than expected,
      // it means the main thread was blocked
      if (delayMs >= this.stallThresholdMs) {
        this.stallCount++;
        this.longestStallMs = Math.max(this.longestStallMs, delayMs);
        console.log(`[MainThreadMonitor] Detected stall: ${delayMs}ms (expected ~${expectedInterval}ms, actual ${actualInterval}ms)`);
      }
      
      this.lastProbeTime = now;
    }, this.periodMs);
  }

  stop(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  snapshotAndReset(): StallSnapshot {
    const snapshot = {
      count: this.stallCount,
      longestMs: this.longestStallMs
    };
    
    this.stallCount = 0;
    this.longestStallMs = 0;
    
    return snapshot;
  }
}

export const monitor = new MainThreadMonitor();
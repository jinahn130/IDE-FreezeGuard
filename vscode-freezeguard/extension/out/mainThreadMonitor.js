"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.monitor = exports.MainThreadMonitor = void 0;
class MainThreadMonitor {
    constructor() {
        this.periodMs = 50;
        this.stallThresholdMs = 100;
        this.stallCount = 0;
        this.longestStallMs = 0;
        this.intervalId = null;
        this.lastProbeTime = Date.now();
    }
    start() {
        if (this.intervalId)
            return;
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
    stop() {
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
    }
    snapshotAndReset() {
        const snapshot = {
            count: this.stallCount,
            longestMs: this.longestStallMs
        };
        this.stallCount = 0;
        this.longestStallMs = 0;
        return snapshot;
    }
}
exports.MainThreadMonitor = MainThreadMonitor;
exports.monitor = new MainThreadMonitor();
//# sourceMappingURL=mainThreadMonitor.js.map
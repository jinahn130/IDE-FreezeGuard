"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.freezeGuardAction = void 0;
const vscode = require("vscode");
const mainThreadMonitor_1 = require("../mainThreadMonitor");
const eventSender_1 = require("../eventSender");
function getMemoryUsage() {
    return process.memoryUsage().heapUsed;
}
function formatBytes(bytes) {
    if (bytes === 0)
        return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}
function isMainThread() {
    // In VSCode extensions, we're typically on the main thread
    // This is a simplified check - in a real implementation you might need more sophisticated detection
    return true;
}
async function freezeGuardAction() {
    const heapBefore = getMemoryUsage();
    const startTime = Date.now();
    // Minimal "measure" action - just timing overhead
    const durationMs = Date.now() - startTime;
    const heapAfter = getMemoryUsage();
    const heapDelta = heapAfter - heapBefore;
    const stallSnapshot = mainThreadMonitor_1.monitor.snapshotAndReset();
    const threadLabel = isMainThread() ? 'MAIN' : 'WORKER';
    const event = {
        action: 'FreezeGuardAction',
        duration_ms: durationMs,
        thread: threadLabel,
        heap_delta_bytes: heapDelta,
        edt_stalls: stallSnapshot.count,
        edt_longest_stall_ms: stallSnapshot.longestMs,
        ts: new Date().toISOString()
    };
    try {
        await eventSender_1.EventSender.sendAsync(event);
    }
    catch (error) {
        console.warn('Failed to send telemetry:', error);
    }
    vscode.window.showInformationMessage(`FreezeGuardAction: ${durationMs.toFixed(1)} ms • ` +
        `heap ${formatBytes(heapBefore)} → ${formatBytes(heapAfter)} (Δ ${formatBytes(heapDelta)}) • ` +
        `stalls ${stallSnapshot.count} (longest ${stallSnapshot.longestMs.toFixed(0)} ms)`);
    console.log('FreezeGuardAction event sent:', event);
}
exports.freezeGuardAction = freezeGuardAction;
//# sourceMappingURL=freezeGuard.js.map
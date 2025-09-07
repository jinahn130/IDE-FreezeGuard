"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.backgroundFixAction = void 0;
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
async function backgroundFixAction() {
    const heapBefore = getMemoryUsage();
    const startTime = Date.now();
    // Show progress and run work in background (not blocking main thread)
    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: "Freeze Guard: Background Work",
        cancellable: true
    }, async (progress, token) => {
        progress.report({ increment: 0 });
        // Simulate work without blocking main thread
        for (let i = 0; i < 12; i++) {
            if (token.isCancellationRequested) {
                break;
            }
            await new Promise(resolve => setTimeout(resolve, 100));
            progress.report({ increment: 8.33 }); // 100/12 ≈ 8.33
        }
    });
    const durationMs = Date.now() - startTime;
    const heapAfter = getMemoryUsage();
    const heapDelta = heapAfter - heapBefore;
    const stallSnapshot = mainThreadMonitor_1.monitor.snapshotAndReset();
    const event = {
        action: 'BackgroundFixAction',
        duration_ms: durationMs,
        thread: 'WORKER',
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
    vscode.window.showInformationMessage(`BackgroundFixAction: ${durationMs.toFixed(1)} ms (WORKER) • ` +
        `heap Δ ${heapDelta} • stalls ${stallSnapshot.count} (longest ${stallSnapshot.longestMs.toFixed(0)} ms)`);
    console.log('BackgroundFixAction finished (no UI block)');
}
exports.backgroundFixAction = backgroundFixAction;
//# sourceMappingURL=backgroundFix.js.map
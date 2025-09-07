import * as vscode from 'vscode';
import { monitor } from '../mainThreadMonitor';
import { EventSender } from '../eventSender';
import { ActionEvent } from '../types';

function getMemoryUsage(): number {
  return process.memoryUsage().heapUsed;
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function isMainThread(): boolean {
  // In VSCode extensions, we're typically on the main thread
  // This is a simplified check - in a real implementation you might need more sophisticated detection
  return true;
}

export async function freezeGuardAction(): Promise<void> {
  const heapBefore = getMemoryUsage();
  const startTime = Date.now();

  // Minimal "measure" action - just timing overhead
  const durationMs = Date.now() - startTime;
  const heapAfter = getMemoryUsage();
  const heapDelta = heapAfter - heapBefore;

  const stallSnapshot = monitor.snapshotAndReset();
  const threadLabel = isMainThread() ? 'MAIN' : 'WORKER';
  
  const event: ActionEvent = {
    action: 'FreezeGuardAction',
    duration_ms: durationMs,
    thread: threadLabel as 'MAIN' | 'WORKER',
    heap_delta_bytes: heapDelta,
    edt_stalls: stallSnapshot.count,
    edt_longest_stall_ms: stallSnapshot.longestMs,
    ts: new Date().toISOString()
  };

  try {
    await EventSender.sendAsync(event);
  } catch (error) {
    console.warn('Failed to send telemetry:', error);
  }

  vscode.window.showInformationMessage(
    `FreezeGuardAction: ${durationMs.toFixed(1)} ms • ` +
    `heap ${formatBytes(heapBefore)} → ${formatBytes(heapAfter)} (Δ ${formatBytes(heapDelta)}) • ` +
    `stalls ${stallSnapshot.count} (longest ${stallSnapshot.longestMs.toFixed(0)} ms)`
  );

  console.log('FreezeGuardAction event sent:', event);
}
import * as vscode from 'vscode';
import { monitor } from '../mainThreadMonitor';
import { EventSender } from '../eventSender';
import { ActionEvent } from '../types';

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function getMemoryUsage(): number {
  // VSCode doesn't expose heap usage like Java, so we'll use process memory
  return process.memoryUsage().heapUsed;
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export async function badBlockingAction(): Promise<void> {
  const heapBefore = getMemoryUsage();
  const startTime = Date.now();

  // Intentionally block the main thread - this will cause UI freeze
  const blockTime = 1200; // 1.2 seconds
  const endTime = startTime + blockTime;
  while (Date.now() < endTime) {
    // Busy wait to simulate blocking work
  }

  const durationMs = Date.now() - startTime;
  const heapAfter = getMemoryUsage();
  const heapDelta = heapAfter - heapBefore;

  const stallSnapshot = monitor.snapshotAndReset();
  
  const event: ActionEvent = {
    action: 'BadBlockingAction',
    duration_ms: durationMs,
    thread: 'MAIN',
    heap_delta_bytes: heapDelta,
    edt_stalls: stallSnapshot.count,
    edt_longest_stall_ms: stallSnapshot.longestMs,
    ts: new Date().toISOString()
  };

  // Test connectivity and send event
  try {
    const statusCode = await EventSender.ping();
    vscode.window.showInformationMessage(`Freeze Guard: Collector /metrics HTTP ${statusCode}`);
    
    await EventSender.sendAsync(event);
  } catch (error) {
    console.warn('Failed to send telemetry:', error);
  }

  vscode.window.showWarningMessage(
    `BadBlockingAction: ${durationMs.toFixed(1)} ms (MAIN) • ` +
    `heap ${formatBytes(heapBefore)} → ${formatBytes(heapAfter)} (Δ ${formatBytes(heapDelta)}) • ` +
    `stalls ${stallSnapshot.count} (longest ${stallSnapshot.longestMs.toFixed(0)} ms)`
  );

  console.log('BadBlockingAction executed (UI deliberately blocked)');
}
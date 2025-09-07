export interface ActionEvent {
  action: string;
  duration_ms: number;
  thread: 'MAIN' | 'WORKER';
  heap_delta_bytes: number;
  edt_stalls: number;
  edt_longest_stall_ms: number;
  ts: string;
}

export interface StallSnapshot {
  count: number;
  longestMs: number;
}
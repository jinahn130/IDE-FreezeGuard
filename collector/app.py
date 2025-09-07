from fastapi import FastAPI, Response, Request
from fastapi.responses import PlainTextResponse, JSONResponse
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST
from datetime import datetime
from typing import Deque, Tuple
from collections import deque

from models import ActionEvent
from metrics import (
    action_duration_seconds,
    edt_stall_duration_seconds,
    edt_stalls_total,
    events_total,
    heap_delta_bytes,
)

app = FastAPI(title="Freeze Guard Collector")
RING: Deque[Tuple[datetime, ActionEvent]] = deque(maxlen=256)

@app.post("/ingest")
async def ingest(request: Request):
    raw = await request.body()
    print(f"[collector] RAW BODY: {raw!r}")  # <-- see exactly what the plugin sent

    # Try to parse JSON no matter what headers say
    try:
        data = await request.json()
    except Exception as ex:
        return JSONResponse(status_code=400, content={"ok": False, "error": f"json parse: {ex}", "raw": raw.decode("utf-8", "ignore")})

    # Validate with Pydantic (so fields are typed)
    try:
        ev = ActionEvent(**data)
    except Exception as ex:
        return JSONResponse(status_code=422, content={"ok": False, "error": f"model: {ex}", "data": data})

    # Metrics
    secs = ev.duration_ms / 1000.0
    stall_secs = max(0.0, ev.edt_longest_stall_ms) / 1000.0
    action_duration_seconds.labels(ev.action, ev.thread).observe(secs)
    if stall_secs > 0.0:
        edt_stall_duration_seconds.labels(ev.action).observe(stall_secs)
    if ev.edt_stalls > 0:
        edt_stalls_total.labels(ev.action).inc(ev.edt_stalls)
    events_total.labels(ev.action, ev.thread).inc()
    
    # Record heap delta (convert to absolute value for histogram)
    if ev.heap_delta_bytes != 0:
        heap_delta_bytes.labels(ev.action, ev.thread).observe(ev.heap_delta_bytes)

    RING.append((ev.ts, ev))
    return {"ok": True}

@app.get("/metrics")
def metrics():
    data = generate_latest()
    return Response(content=data, media_type=CONTENT_TYPE_LATEST)

@app.get("/report", response_class=PlainTextResponse)
def report():
    lines = []
    for ts, ev in list(RING)[-50:]:
        lines.append(
            f"{ts.isoformat()}  {ev.action:<20} {ev.thread}  "
            f"{ev.duration_ms:7.1f} ms  stalls={ev.edt_stalls} "
            f"longest={ev.edt_longest_stall_ms:5.1f} ms  heapΔ={ev.heap_delta_bytes}"
        )
    return "\n".join(lines) if lines else "(no events yet)"

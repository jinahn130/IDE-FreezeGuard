from pydantic import BaseModel
from typing import Literal
from datetime import datetime

class ActionEvent(BaseModel):
    action: str
    duration_ms: float
    thread: Literal["MAIN", "WORKER"]
    heap_delta_bytes: int = 0
    edt_stalls: int = 0
    edt_longest_stall_ms: float = 0.0
    ts: datetime
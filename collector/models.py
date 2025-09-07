from pydantic import BaseModel
from typing import Literal
from datetime import datetime

class ActionEvent(BaseModel):
    action: str
    duration_ms: float
    thread: Literal["EDT", "BGT", "MAIN", "WORKER"]  # EDT/BGT for IntelliJ, MAIN/WORKER for VS Code
    heap_delta_bytes: int = 0
    edt_stalls: int = 0
    edt_longest_stall_ms: float = 0.0
    ts: datetime

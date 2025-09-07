from prometheus_client import Counter, Histogram

# Seconds buckets tuned for UI actions (10ms .. 10s)
ACTION_DURATION_BUCKETS = (
    0.010, 0.025, 0.050, 0.100, 0.250, 0.500, 1.0, 2.0, 5.0, 10.0
)
STALL_DURATION_BUCKETS = (
    0.100, 0.250, 0.500, 1.0, 2.0, 5.0
)

action_duration_seconds = Histogram(
    "action_duration_seconds",
    "Action duration in seconds",
    ["action", "thread"],
    buckets=ACTION_DURATION_BUCKETS,
)

edt_stall_duration_seconds = Histogram(
    "edt_stall_duration_seconds",
    "Observed EDT stall (longest in event window), seconds",
    ["action"],
    buckets=STALL_DURATION_BUCKETS,
)

edt_stalls_total = Counter(
    "edt_stalls_total",
    "Count of EDT stall events observed",
    ["action"]
)

events_total = Counter(
    "events_total",
    "Number of events ingested",
    ["action", "thread"]
)

heap_delta_bytes = Histogram(
    "heap_delta_bytes",
    "Memory allocation delta in bytes per action",
    ["action", "thread"],
    buckets=(-50_000_000, -10_000_000, -1_000_000, -100_000, -10_000, 0, 10_000, 100_000, 1_000_000, 10_000_000, 50_000_000)
)

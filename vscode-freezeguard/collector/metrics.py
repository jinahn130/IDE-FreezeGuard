from prometheus_client import Counter, Histogram

# Seconds buckets tuned for UI actions (10ms .. 10s)
ACTION_DURATION_BUCKETS = (
    0.010, 0.025, 0.050, 0.100, 0.250, 0.500, 1.0, 2.0, 5.0, 10.0
)
STALL_DURATION_BUCKETS = (
    0.100, 0.250, 0.500, 1.0, 2.0, 5.0
)

action_duration_seconds = Histogram(
    "vscode_action_duration_seconds",
    "VSCode action duration in seconds",
    ["action", "thread"],
    buckets=ACTION_DURATION_BUCKETS,
)

edt_stall_duration_seconds = Histogram(
    "vscode_main_stall_duration_seconds",
    "Observed main thread stall (longest in event window), seconds",
    ["action"],
    buckets=STALL_DURATION_BUCKETS,
)

edt_stalls_total = Counter(
    "vscode_main_stalls_total",
    "Count of main thread stall events observed",
    ["action"]
)

events_total = Counter(
    "vscode_events_total",
    "Number of VSCode events ingested",
    ["action", "thread"]
)
# Runbook: Elevated Latency

## Symptoms
- p95/p99 latency significantly above baseline.
- `getSystemStatus` reports `ElevatedLatency` or `LatencyBudgetBurn` alerts.

## Likely Causes
- Database or downstream API slowness.
- Garbage collection pauses under memory pressure.
- Noisy-neighbor contention on shared infrastructure.

## Recommended Actions
1. Check whether latency correlates with a specific downstream dependency.
2. Review JVM GC logs / memory metrics if available.
3. Check for concurrent batch jobs or traffic spikes.
4. Consider enabling circuit breakers/timeouts for the slow dependency.

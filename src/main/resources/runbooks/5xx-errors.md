# Runbook: 5xx Errors on customer-api

## Symptoms
- Customers report HTTP 500/502/503 responses.
- Elevated error rate visible in `getSystemStatus` for `customer-api`.

## Likely Causes
- Downstream dependency timeout (payment-service or database connection pool exhaustion).
- Recent deployment introduced a regression.
- Thread pool saturation under load spike.

## Recommended Actions
1. Check active alerts and error rate via system status tool.
2. Correlate with recent deployments/config changes in the last 30 minutes.
3. Inspect connection pool metrics for downstream dependencies.
4. If caused by a recent deploy, roll back to the last known-good version.
5. Scale out replicas temporarily if load-related.

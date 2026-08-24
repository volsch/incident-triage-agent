# Runbook: Authentication Failures

## Symptoms
- Increased 401/403 responses.
- `auth-service` reporting degraded health or elevated latency.

## Likely Causes
- Expired or misconfigured signing keys/certificates.
- Token issuer or identity provider outage.
- Clock skew between services causing token validation failures.

## Recommended Actions
1. Verify signing key/certificate expiry dates.
2. Check identity provider status page / synthetic checks.
3. Confirm NTP sync across affected hosts.
4. Review recent auth-service configuration changes.

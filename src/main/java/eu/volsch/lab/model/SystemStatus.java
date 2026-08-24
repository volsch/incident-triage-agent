package eu.volsch.lab.model;

import java.util.List;

/**
 * Synthetic system status snapshot for a single service, as returned by the {@code getSystemStatus}
 * tool. Backed entirely by mock/hardcoded data.
 *
 * @param service the queried service name
 * @param healthy whether the service currently reports as healthy
 * @param errorRatePct synthetic error rate percentage over the last 10 minutes
 * @param latencyMs synthetic p95 latency in milliseconds
 * @param activeAlerts currently firing synthetic alerts
 */
public record SystemStatus(
    String service,
    boolean healthy,
    double errorRatePct,
    int latencyMs,
    List<String> activeAlerts) {

  /**
   * Compact constructor defensively copies {@code activeAlerts} into an immutable list so that
   * neither the caller nor this record can mutate state after construction.
   */
  public SystemStatus {
    activeAlerts = List.copyOf(activeAlerts);
  }
}

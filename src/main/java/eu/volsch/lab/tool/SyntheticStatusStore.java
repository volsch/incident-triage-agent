package eu.volsch.lab.tool;

import eu.volsch.lab.model.SystemStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Provides synthetic system status data for a small set of known services. There is no real cloud
 * or APM integration here — this is deliberately hardcoded mock data to keep the lab self-contained
 * and offline-friendly.
 */
@Component
class SyntheticStatusStore {

  private final Map<String, SystemStatus> statuses =
      Map.of(
          "customer-api",
              new SystemStatus(
                  "customer-api",
                  false,
                  47.5,
                  3200,
                  List.of("HighErrorRate:5xx", "LatencyBudgetBurn")),
          "payment-service", new SystemStatus("payment-service", true, 0.2, 180, List.of()),
          "auth-service",
              new SystemStatus("auth-service", false, 12.0, 950, List.of("ElevatedLatency")),
          "notification-service",
              new SystemStatus("notification-service", true, 0.0, 90, List.of()));

  /**
   * Looks up the synthetic status for a service.
   *
   * @param service the service name (case-insensitive, whitespace-trimmed)
   * @return the known synthetic status, or a healthy fallback with a {@code NoDataAvailable} alert
   *     if the service is not recognized
   */
  SystemStatus lookup(String service) {
    String key = service == null ? "" : service.trim().toLowerCase(Locale.ROOT);
    return statuses.getOrDefault(
        key, new SystemStatus(service, true, 0.0, 100, List.of("NoDataAvailable:unknownService")));
  }
}

package eu.volsch.lab.tool;

import eu.volsch.lab.model.SystemStatus;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes {@link #getSystemStatus(String)} as an {@code @Tool} that the LLM agent can call to fetch
 * synthetic metrics and active alerts for a service. Read-only: this tool cannot perform any
 * corrective or write action.
 */
@Component
public class SystemStatusTool {

  private final SyntheticStatusStore store;
  private final ToolCallRecorder recorder;

  /**
   * Creates the tool.
   *
   * @param store the synthetic status data source to query
   * @param recorder records the real result so it can be reported as verified evidence
   */
  public SystemStatusTool(SyntheticStatusStore store, ToolCallRecorder recorder) {
    this.store = store;
    this.recorder = recorder;
  }

  /**
   * Fetches synthetic system status for a named service. Callable by the LLM agent as a tool.
   *
   * @param service the service name to look up, e.g. "customer-api"
   * @return the synthetic status for the service
   */
  @Tool(
      description =
          "Fetches synthetic system status (health, error rate, latency, active alerts) for a named service.")
  public SystemStatus getSystemStatus(
      @ToolParam(description = "The service name to look up, e.g. 'customer-api'") String service) {
    SystemStatus status = store.lookup(service);
    recorder.record("getSystemStatus", service, describe(status));
    return status;
  }

  /**
   * Renders a status snapshot as a compact single line for the recorded evidence trail.
   *
   * @param status the status actually returned to the model
   * @return a human-readable one-line rendering
   */
  private static String describe(SystemStatus status) {
    return "healthy=%s, errorRatePct=%s, latencyMs=%d, activeAlerts=%s"
        .formatted(
            status.healthy(), status.errorRatePct(), status.latencyMs(), status.activeAlerts());
  }
}

package eu.volsch.lab.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SystemStatusTool} against the synthetic status data. */
class SystemStatusToolTest {

  private final ToolCallRecorder recorder = new ToolCallRecorder();
  private final SystemStatusTool tool = new SystemStatusTool(new SyntheticStatusStore(), recorder);

  @Test
  void returnsDegradedStatusForKnownUnhealthyService() {
    var status = tool.getSystemStatus("customer-api");

    assertThat(status.healthy()).isFalse();
    assertThat(status.errorRatePct()).isGreaterThan(0);
    assertThat(status.activeAlerts()).isNotEmpty();
  }

  @Test
  void returnsHealthyStatusForKnownHealthyService() {
    var status = tool.getSystemStatus("payment-service");

    assertThat(status.healthy()).isTrue();
    assertThat(status.activeAlerts()).isEmpty();
  }

  @Test
  void returnsFallbackStatusForUnknownService() {
    var status = tool.getSystemStatus("does-not-exist");

    assertThat(status.activeAlerts()).contains("NoDataAvailable:unknownService");
  }

  @Test
  void lookupIsCaseInsensitiveAndTrimsWhitespace() {
    var status = tool.getSystemStatus("  Customer-API  ");

    assertThat(status.healthy()).isFalse();
  }

  @Test
  void recordsTheRealMetricsAsEvidence() {
    tool.getSystemStatus("customer-api");

    assertThat(recorder.recordedCalls())
        .containsExactly(
            "getSystemStatus(customer-api) -> healthy=false, errorRatePct=47.5, latencyMs=3200, "
                + "activeAlerts=[HighErrorRate:5xx, LatencyBudgetBurn]");
  }
}

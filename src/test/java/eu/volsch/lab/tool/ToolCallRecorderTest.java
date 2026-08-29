package eu.volsch.lab.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ToolCallRecorder}'s per-run recording lifecycle. */
class ToolCallRecorderTest {

  private final ToolCallRecorder recorder = new ToolCallRecorder();

  @Test
  void recordsCallsInInvocationOrder() {
    recorder.start();
    recorder.recordCall("getSystemStatus", "customer-api", "healthy=false");
    recorder.recordCall("searchRunbook", "5xx", "no matches");

    assertThat(recorder.recordedCalls())
        .containsExactly(
            "getSystemStatus(customer-api) -> healthy=false", "searchRunbook(5xx) -> no matches");
  }

  @Test
  void startDiscardsEntriesLeftOverFromAnEarlierRun() {
    recorder.recordCall("getSystemStatus", "stale", "stale result");

    recorder.start();

    assertThat(recorder.recordedCalls()).isEmpty();
  }

  @Test
  void returnsAnEmptyListWhenNoToolWasCalled() {
    recorder.start();

    assertThat(recorder.recordedCalls()).isEmpty();
  }

  @Test
  void recordedCallsAreImmutable() {
    recorder.start();
    recorder.recordCall("searchRunbook", "5xx", "no matches");

    var calls = recorder.recordedCalls();
    recorder.recordCall("searchRunbook", "latency", "no matches");

    assertThat(calls).hasSize(1);
  }

  @Test
  void clearReleasesRecordedState() {
    recorder.start();
    recorder.recordCall("searchRunbook", "5xx", "no matches");

    recorder.clear();

    assertThat(recorder.recordedCalls()).isEmpty();
  }
}

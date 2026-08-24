package eu.volsch.lab.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ToolCallRecorder}'s per-run recording lifecycle. */
class ToolCallRecorderTest {

  private final ToolCallRecorder recorder = new ToolCallRecorder();

  @Test
  void recordsCallsInInvocationOrder() {
    recorder.start();
    recorder.record("getSystemStatus", "customer-api", "healthy=false");
    recorder.record("searchRunbook", "5xx", "no matches");

    assertThat(recorder.recordedCalls())
        .containsExactly(
            "getSystemStatus(customer-api) -> healthy=false", "searchRunbook(5xx) -> no matches");
  }

  @Test
  void startDiscardsEntriesLeftOverFromAnEarlierRun() {
    recorder.record("getSystemStatus", "stale", "stale result");

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
    recorder.record("searchRunbook", "5xx", "no matches");

    var calls = recorder.recordedCalls();
    recorder.record("searchRunbook", "latency", "no matches");

    assertThat(calls).hasSize(1);
  }

  @Test
  void clearReleasesRecordedState() {
    recorder.start();
    recorder.record("searchRunbook", "5xx", "no matches");

    recorder.clear();

    assertThat(recorder.recordedCalls()).isEmpty();
  }
}

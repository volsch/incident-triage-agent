package eu.volsch.lab.tool;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Records what the {@code @Tool} methods actually returned during a single triage request, so the
 * agent can report evidence that is guaranteed to be real.
 *
 * <p>Without this, the {@code evidence} field of the assessment would be whatever prose the model
 * chose to write — which a model can get wrong or invent outright. Recording the genuine tool
 * results lets {@code TriageAgentService} replace the model's account of its own investigation with
 * ground truth, which matters precisely because everything else in the response is
 * non-deterministic model output.
 *
 * <p>State is held per thread: Spring AI executes tool calls synchronously on the thread that
 * called {@code ChatClient}, so each in-flight request records into its own list. Callers must
 * bracket a run with {@link #start()} and {@link #clear()} (in a {@code finally} block) to avoid
 * leaking entries between requests handled by the same pooled thread.
 */
@Component
public class ToolCallRecorder {

  private final ThreadLocal<List<String>> calls = ThreadLocal.withInitial(ArrayList::new);

  /** Begins a new recording, discarding anything left over on this thread. */
  public void start() {
    calls.get().clear();
  }

  /**
   * Records one real tool invocation and its result.
   *
   * @param toolName the {@code @Tool} method that ran, e.g. {@code getSystemStatus}
   * @param argument the argument the model passed to it
   * @param result a human-readable rendering of what the tool actually returned
   */
  void record(String toolName, String argument, String result) {
    calls.get().add("%s(%s) -> %s".formatted(toolName, argument, result));
  }

  /**
   * Returns the tool calls recorded so far on the current thread.
   *
   * @return the recorded invocations, in call order; empty if the model called no tools
   */
  public List<String> recordedCalls() {
    return List.copyOf(calls.get());
  }

  /** Ends the recording and releases the thread-local state. */
  public void clear() {
    calls.remove();
  }
}

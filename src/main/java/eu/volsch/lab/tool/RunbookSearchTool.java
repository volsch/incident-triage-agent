package eu.volsch.lab.tool;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes {@link #searchRunbook(String)} as an {@code @Tool} performing basic keyword lookup across
 * local runbook markdown files. Not a vector database / RAG pipeline.
 */
@Component
public class RunbookSearchTool {

  private final RunbookRepository repository;
  private final ToolCallRecorder recorder;

  /**
   * Creates the tool.
   *
   * @param repository the runbook repository to search against
   * @param recorder records the real result so it can be reported as verified evidence
   */
  public RunbookSearchTool(RunbookRepository repository, ToolCallRecorder recorder) {
    this.repository = repository;
    this.recorder = recorder;
  }

  /**
   * Performs a keyword search across local runbooks. Callable by the LLM agent as a tool.
   *
   * @param query free-text keywords describing the symptom, e.g. "5xx errors" or "latency"
   * @return matching runbook excerpts, or an empty list if none match
   */
  @Tool(
      description =
          "Performs keyword search across local incident runbooks and returns matching excerpts.")
  public List<String> searchRunbook(
      @ToolParam(description = "Keywords describing the symptom, e.g. '5xx errors' or 'latency'")
          String query) {
    List<String> matches = repository.search(query);
    recorder.record("searchRunbook", query, Runbooks.summarize(matches));
    return matches;
  }
}

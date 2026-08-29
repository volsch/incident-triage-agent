package eu.volsch.lab.tool;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * Exposes {@link #searchRunbookSemantic(String)} as an {@code @Tool} performing embedding-based
 * similarity search over local runbook sections. Unlike {@link RunbookSearchTool}, this finds
 * conceptually related excerpts even when the query shares no exact keywords with the runbook text
 * — a minimal, dependency-free illustration of a RAG-style retrieval step, backed by an in-memory
 * {@link VectorStore} (no external vector database).
 *
 * <p>The vector store is indexed lazily, on first call, rather than at application startup — so
 * plain context startup (e.g. Spring's {@code @SpringBootTest} context-loads checks) never triggers
 * a network call to the embedding model; only an actual triage request that reaches this tool does.
 */
@Component
public class RunbookSemanticSearchTool {

  private static final int TOP_K = Runbooks.MAX_RESULTS;
  private static final double SIMILARITY_THRESHOLD = 0.5;

  private final VectorStore runbookVectorStore;
  private final RunbookVectorStoreInitializer initializer;
  private final ToolCallRecorder recorder;
  private boolean indexed = false;

  /**
   * Creates the tool.
   *
   * @param runbookVectorStore the in-memory vector store holding embedded runbook sections
   * @param initializer loads and chunks the local runbook markdown files into documents, used to
   *     populate the vector store on first use
   * @param recorder records the real result so it can be reported as verified evidence
   */
  public RunbookSemanticSearchTool(
      VectorStore runbookVectorStore,
      RunbookVectorStoreInitializer initializer,
      ToolCallRecorder recorder) {
    this.runbookVectorStore = runbookVectorStore;
    this.initializer = initializer;
    this.recorder = recorder;
  }

  /**
   * Performs an embedding-based similarity search across local runbook sections. Callable by the
   * LLM agent as a tool; complements {@link RunbookSearchTool#searchRunbook(String)} by retrieving
   * semantically related excerpts rather than requiring exact keyword overlap.
   *
   * @param query free-text description of the symptom or question, e.g. "customers can't log in"
   * @return the most semantically similar runbook excerpts, or an empty list if none clear the
   *     similarity threshold
   */
  @Tool(
      description =
          "Performs embedding-based semantic search across local incident runbooks and returns "
              + "the most conceptually similar excerpts, even without exact keyword overlap.")
  public List<String> searchRunbookSemantic(
      @ToolParam(
              description = "Free-text description of the symptom, e.g. 'customers can't log in'")
          String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    ensureIndexed();
    SearchRequest request =
        SearchRequest.builder()
            .query(query)
            .topK(TOP_K)
            .similarityThreshold(SIMILARITY_THRESHOLD)
            .build();
    List<Document> results = runbookVectorStore.similaritySearch(request);
    List<String> matches = results.stream().map(RunbookSemanticSearchTool::describe).toList();
    recorder.recordCall("searchRunbookSemantic", query, Runbooks.summarize(matches));
    return matches;
  }

  /**
   * Embeds and indexes the runbook sections into the vector store, exactly once, the first time
   * this tool is actually invoked. Synchronized on the whole method rather than using
   * double-checked locking: this tool is called at most a few times per triage request, so the
   * extra lock contention is negligible, and it keeps the logic simple to reason about (and to
   * unit-test) for what is intentionally a small demo, not a high-throughput production path.
   */
  private synchronized void ensureIndexed() {
    if (!indexed) {
      runbookVectorStore.add(initializer.loadRunbookDocuments());
      indexed = true;
    }
  }

  /**
   * Formats a matched document as a single-line excerpt with its source filename.
   *
   * @param document the matched runbook section
   * @return a single-line, filename-prefixed excerpt
   */
  private static String describe(Document document) {
    String filename =
        String.valueOf(document.getMetadata().getOrDefault(Runbooks.METADATA_FILENAME, "unknown"));
    return Runbooks.format(filename, document.getText());
  }
}

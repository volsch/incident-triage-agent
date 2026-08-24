package eu.volsch.lab.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Unit tests for {@link RunbookSemanticSearchTool}, verifying result formatting and that the vector
 * store is indexed lazily, exactly once, without depending on a real embedding model.
 */
class RunbookSemanticSearchToolTest {

  private final VectorStore vectorStore = mock(VectorStore.class);
  private final RunbookVectorStoreInitializer initializer =
      mock(RunbookVectorStoreInitializer.class);
  private final ToolCallRecorder recorder = new ToolCallRecorder();
  private final RunbookSemanticSearchTool tool =
      new RunbookSemanticSearchTool(vectorStore, initializer, recorder);

  @Test
  void returnsBlankListForBlankQueryWithoutIndexing() {
    List<String> results = tool.searchRunbookSemantic("   ");

    assertThat(results).isEmpty();
    verify(initializer, times(0)).loadRunbookDocuments();
  }

  @Test
  void returnsBlankListForNullQueryWithoutIndexing() {
    List<String> results = tool.searchRunbookSemantic(null);

    assertThat(results).isEmpty();
    verify(initializer, times(0)).loadRunbookDocuments();
  }

  @Test
  void truncatesExcerptsLongerThan300Characters() {
    when(initializer.loadRunbookDocuments()).thenReturn(List.of());
    String longText = "x".repeat(400);
    Document match = new Document(longText, Map.of("filename", "long.md"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(match));

    List<String> results = tool.searchRunbookSemantic("customers can't log in");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst()).hasSize("[long.md] ".length() + 300 + "...".length());
    assertThat(results.getFirst()).endsWith("...");
  }

  @Test
  void formatsDocumentWithNullTextAsEmptyExcerpt() {
    when(initializer.loadRunbookDocuments()).thenReturn(List.of());
    Document match = mock(Document.class);
    when(match.getMetadata()).thenReturn(Map.of("filename", "empty.md"));
    when(match.getText()).thenReturn(null);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(match));

    List<String> results = tool.searchRunbookSemantic("customers can't log in");

    assertThat(results).containsExactly("[empty.md] ");
  }

  @Test
  void formatsMatchedDocumentsWithFilenameAndExcerpt() {
    when(initializer.loadRunbookDocuments()).thenReturn(List.of());
    Document match =
        new Document("Check the connection pool metrics.", Map.of("filename", "5xx-errors.md"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(match));

    List<String> results = tool.searchRunbookSemantic("customers can't log in");

    assertThat(results).containsExactly("[5xx-errors.md] Check the connection pool metrics.");
  }

  @Test
  void recordsTheRealResultAsEvidence() {
    when(initializer.loadRunbookDocuments()).thenReturn(List.of());
    Document match =
        new Document("Check the connection pool metrics.", Map.of("filename", "5xx-errors.md"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(match));

    tool.searchRunbookSemantic("customers can't log in");

    assertThat(recorder.recordedCalls())
        .containsExactly(
            "searchRunbookSemantic(customers can't log in) -> "
                + "[5xx-errors.md] Check the connection pool metrics.");
  }

  @Test
  void recordsAnExplicitMarkerWhenNothingMatches() {
    when(initializer.loadRunbookDocuments()).thenReturn(List.of());
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    tool.searchRunbookSemantic("nothing similar");

    assertThat(recorder.recordedCalls())
        .containsExactly("searchRunbookSemantic(nothing similar) -> no matches");
  }

  @Test
  void indexesTheVectorStoreOnlyOnceAcrossMultipleCalls() {
    when(initializer.loadRunbookDocuments()).thenReturn(List.of());
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    tool.searchRunbookSemantic("first query");
    tool.searchRunbookSemantic("second query");

    verify(initializer, times(1)).loadRunbookDocuments();
    verify(vectorStore, times(1)).add(anyList());
  }
}

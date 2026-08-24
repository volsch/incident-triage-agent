package eu.volsch.lab.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Unit tests for {@link RunbookSearchTool} keyword matching against the bundled runbooks.
 *
 * <p>Several tests here guard against the failure mode that makes a naive keyword search worthless:
 * matching on stop words or on substrings, which would make every query return every runbook and
 * leave the model with no real signal (and nothing to distinguish this tool from semantic search).
 */
class RunbookSearchToolTest {

  private ToolCallRecorder recorder;
  private RunbookSearchTool tool;

  @BeforeEach
  void setUp() {
    RunbookRepository repository = new RunbookRepository(new PathMatchingResourcePatternResolver());
    recorder = new ToolCallRecorder();
    tool = new RunbookSearchTool(repository, recorder);
  }

  @Test
  void findsRunbookMatchingKeyword() {
    var results = tool.searchRunbook("5xx errors");

    assertThat(results).isNotEmpty();
    assertThat(results.getFirst()).contains("5xx-errors.md");
  }

  @Test
  void ranksTheRunbookMatchingMostKeywordsFirst() {
    var results = tool.searchRunbook("authentication signing keys expired");

    assertThat(results).isNotEmpty();
    assertThat(results.getFirst()).contains("auth-failures.md");
  }

  @Test
  void returnsSectionExcerptRatherThanTheTopOfTheFile() {
    var results = tool.searchRunbook("signing keys certificates");

    assertThat(results).isNotEmpty();
    assertThat(results.getFirst()).contains("##");
    assertThat(results.getFirst()).doesNotContain("# Runbook:");
  }

  @Test
  void returnsEmptyListForBlankQuery() {
    assertThat(tool.searchRunbook("")).isEmpty();
    assertThat(tool.searchRunbook(null)).isEmpty();
  }

  @Test
  void returnsEmptyListWhenNoRunbookMatches() {
    assertThat(tool.searchRunbook("totally-unrelated-keyword-xyz")).isEmpty();
  }

  @Test
  void ignoresQueriesConsistingOnlyOfStopWords() {
    assertThat(tool.searchRunbook("what has been the most of these")).isEmpty();
  }

  @Test
  void doesNotMatchEveryRunbookViaCommonWords() {
    var results = tool.searchRunbook("the database is on fire");

    assertThat(results).isNotEmpty();
    assertThat(results).allSatisfy(result -> assertThat(result).containsIgnoringCase("database"));
  }

  @Test
  void doesNotMatchKeywordsOccurringOnlyAsSubstrings() {
    assertThat(tool.searchRunbook("laten")).isEmpty();
    assertThat(tool.searchRunbook("rror")).isEmpty();
  }

  @Test
  void limitsResultsToKeepModelContextLean() {
    var results = tool.searchRunbook("service errors latency alerts customers deployment");

    assertThat(results).hasSizeLessThanOrEqualTo(Runbooks.MAX_RESULTS);
  }

  @Test
  void recordsTheRealResultAsEvidence() {
    var results = tool.searchRunbook("5xx errors");

    assertThat(recorder.recordedCalls()).hasSize(1);
    assertThat(recorder.recordedCalls().getFirst())
        .startsWith("searchRunbook(5xx errors) -> ")
        .contains(results.getFirst());
  }

  @Test
  void recordsAnExplicitMarkerWhenNothingMatches() {
    tool.searchRunbook("totally-unrelated-keyword-xyz");

    assertThat(recorder.recordedCalls())
        .containsExactly("searchRunbook(totally-unrelated-keyword-xyz) -> no matches");
  }
}

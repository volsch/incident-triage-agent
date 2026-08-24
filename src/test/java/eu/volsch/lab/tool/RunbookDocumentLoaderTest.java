package eu.volsch.lab.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Unit tests for {@link RunbookDocumentLoader}, verifying the bundled runbook markdown files are
 * split into one document chunk per {@code ##} section.
 */
class RunbookDocumentLoaderTest {

  private final ResourcePatternResolver resourceResolver =
      new PathMatchingResourcePatternResolver();
  private final RunbookDocumentLoader loader = new RunbookDocumentLoader();

  private List<Document> documents;

  @BeforeEach
  void setUp() {
    documents = loader.load(resourceResolver);
  }

  @Test
  void splitsEachRunbookIntoMultipleSectionChunks() {
    assertThat(documents).hasSizeGreaterThan(3);
  }

  @Test
  void everyChunkIsTaggedWithItsSourceFilename() {
    assertThat(documents)
        .allSatisfy(document -> assertThat(document.getMetadata()).containsKey("filename"));
  }

  @Test
  void chunksFromThe5xxRunbookCoverItsKnownSections() {
    List<String> sectionsFromFile =
        documents.stream()
            .filter(doc -> "5xx-errors.md".equals(doc.getMetadata().get("filename")))
            .map(Document::getText)
            .toList();

    assertThat(sectionsFromFile).isNotEmpty();
    assertThat(sectionsFromFile.stream().anyMatch(text -> text.contains("Likely Causes"))).isTrue();
  }

  @Test
  void skipsEmptyLeadingSectionAndFallsBackToBlankFilenameWhenUnnamed() throws IOException {
    // Content starting with "\n## " produces an empty leading chunk before the first section
    // heading; a resource with no filename (e.g. loaded from an in-memory byte array) must not
    // fail, falling back to an empty filename instead of null.
    Resource unnamedResource =
        new ByteArrayResource("\n## Section\nBody text.".getBytes(StandardCharsets.UTF_8));
    ResourcePatternResolver mockResolver = mock(ResourcePatternResolver.class);
    when(mockResolver.getResources(anyString())).thenReturn(new Resource[] {unnamedResource});

    List<Document> chunked = loader.load(mockResolver);

    assertThat(chunked).hasSize(1);
    assertThat(chunked.getFirst().getMetadata()).containsEntry("filename", "");
  }
}

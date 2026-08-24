package eu.volsch.lab.tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Splits local runbook markdown files into per-section {@link Document} chunks, suitable for
 * embedding into a vector store. Each chunk covers a single {@code ##} heading (e.g. "Symptoms",
 * "Likely Causes", "Recommended Actions") so that semantic search can retrieve focused excerpts
 * rather than whole-file matches.
 */
@Component
class RunbookDocumentLoader {

  /**
   * Loads every runbook markdown file on the classpath and splits each into section-level {@link
   * Document} chunks.
   *
   * @param resourceResolver resolver used to locate {@code classpath:runbooks/*.md} files
   * @return the chunked runbook documents, tagged with their source filename as metadata
   */
  List<Document> load(ResourcePatternResolver resourceResolver) {
    try {
      Resource[] resources = resourceResolver.getResources(Runbooks.CLASSPATH_PATTERN);
      List<Document> documents = new ArrayList<>();
      for (Resource resource : resources) {
        documents.addAll(chunk(resource));
      }
      return List.copyOf(documents);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load runbooks from classpath", e);
    }
  }

  /**
   * Splits a single runbook file's content into one {@link Document} per {@code ##} section.
   *
   * @param resource the runbook markdown resource to chunk
   * @return the section-level document chunks for this file
   * @throws IOException if the resource content cannot be read
   */
  private static List<Document> chunk(Resource resource) throws IOException {
    String content = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
    String filename = resource.getFilename();
    List<Document> chunks = new ArrayList<>();
    for (String section : Runbooks.splitSections(content)) {
      chunks.add(
          new Document(
              section, Map.of(Runbooks.METADATA_FILENAME, filename == null ? "" : filename)));
    }
    return chunks;
  }
}

package eu.volsch.lab.tool;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Public entry point used by {@link RunbookSemanticSearchTool} to load and chunk the local runbook
 * markdown files into embeddable {@link Document}s on first search, without widening the visibility
 * of the package-private {@link RunbookDocumentLoader} it delegates to. Public because it is
 * injected into the public constructor of {@link RunbookSemanticSearchTool}.
 */
@Component
public class RunbookVectorStoreInitializer {

  private final RunbookDocumentLoader documentLoader;
  private final ResourcePatternResolver resourceResolver;

  /**
   * Creates the initializer.
   *
   * @param documentLoader splits runbook files into section-level document chunks
   * @param resourceResolver resolver used to locate {@code classpath:runbooks/*.md} files
   */
  RunbookVectorStoreInitializer(
      RunbookDocumentLoader documentLoader, ResourcePatternResolver resourceResolver) {
    this.documentLoader = documentLoader;
    this.resourceResolver = resourceResolver;
  }

  /**
   * Loads and chunks every local runbook markdown file into embeddable documents.
   *
   * @return the section-level runbook document chunks, ready to be embedded into a vector store
   */
  public List<Document> loadRunbookDocuments() {
    return documentLoader.load(resourceResolver);
  }
}

package eu.volsch.lab.config;

import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the in-memory {@link VectorStore} used by the runbook semantic-search (RAG) tool, backed by
 * the Ollama {@code nomic-embed-text} embedding model. Embedding stays local regardless of which
 * chat model/profile is active, since it's lightweight and this is only a retrieval demo. This
 * deliberately uses {@link SimpleVectorStore} rather than an external vector database, keeping the
 * RAG demonstration dependency-free and in-memory, consistent with this lab's "no real
 * infrastructure" scope.
 */
@Configuration
public class VectorStoreConfig {

  /**
   * Builds the in-memory runbook vector store. Left empty at startup — {@link
   * eu.volsch.lab.tool.RunbookSemanticSearchTool} lazily embeds and indexes the runbook sections on
   * first use, so plain application-context startup (e.g. in unit tests) never triggers a network
   * call to the embedding model.
   *
   * @param embeddingModel the auto-configured Ollama embedding model used to vectorize runbook
   *     sections
   * @return an empty, in-memory {@link VectorStore}, populated lazily on first search
   */
  @Bean
  public VectorStore runbookVectorStore(OllamaEmbeddingModel embeddingModel) {
    return SimpleVectorStore.builder(embeddingModel).build();
  }
}

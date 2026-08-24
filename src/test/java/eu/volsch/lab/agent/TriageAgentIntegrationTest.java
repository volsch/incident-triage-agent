package eu.volsch.lab.agent;

import static org.assertj.core.api.Assertions.assertThat;

import eu.volsch.lab.model.IncidentAssessment;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full round-trip integration test that spins up a real Ollama server in a Docker container (via
 * Testcontainers), pulls a small open-source chat model and the {@code nomic-embed-text} embedding
 * model into it, and wires the actual Spring application context against it. Exercises the real
 * tool-calling + structured-output agent loop end-to-end, including the embedding-based {@code
 * searchRunbookSemantic} tool — no mocks.
 *
 * <p>Deliberately uses a small chat model ({@code qwen2.5:1.5b}) to keep the container fast to
 * start and the model quick to pull/run, since correctness of the wiring — not model quality — is
 * what this test verifies. Requires a local Docker (or compatible) runtime; tagged "integration" so
 * it is excluded from the default {@code ./mvnw test} run and only executed via {@code ./mvnw test
 * -Pintegration-test}.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
class TriageAgentIntegrationTest {

  private static final String MODEL_NAME = "qwen2.5:1.5b";
  private static final String EMBEDDING_MODEL_NAME = "nomic-embed-text";

  @Container
  static final OllamaContainer OLLAMA =
      new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"));

  @DynamicPropertySource
  static void ollamaProperties(DynamicPropertyRegistry registry)
      throws IOException, InterruptedException {
    OLLAMA.execInContainer("ollama", "pull", MODEL_NAME);
    OLLAMA.execInContainer("ollama", "pull", EMBEDDING_MODEL_NAME);
    registry.add("spring.ai.ollama.base-url", OLLAMA::getEndpoint);
    registry.add("spring.ai.ollama.chat.model", () -> MODEL_NAME);
    registry.add("spring.ai.ollama.embedding.model", () -> EMBEDDING_MODEL_NAME);
  }

  @Autowired private TriageAgentService triageAgentService;

  @Test
  void triageProducesStructuredAssessmentUsingRealOllamaModel() {
    IncidentAssessment assessment =
        triageAgentService.triage("customer-api returning 5xx errors for the past 10 minutes");

    assertThat(assessment).isNotNull();
    assertThat(assessment.severity()).isNotNull();
    assertThat(assessment.incidentSummary()).isNotBlank();
  }
}

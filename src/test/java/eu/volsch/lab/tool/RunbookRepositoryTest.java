package eu.volsch.lab.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Unit tests for {@link RunbookRepository} using synthetic, in-memory runbooks, so that ranking,
 * excerpt handling and failure behaviour can be asserted independently of the bundled runbook text.
 */
class RunbookRepositoryTest {

  private static RunbookRepository repositoryOf(String... markdownFiles) throws IOException {
    Resource[] resources = new Resource[markdownFiles.length];
    for (int i = 0; i < markdownFiles.length; i++) {
      String filename = "runbook-" + i + ".md";
      resources[i] =
          new ByteArrayResource(markdownFiles[i].getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
              return filename;
            }
          };
    }
    ResourcePatternResolver resourceResolver = mock(ResourcePatternResolver.class);
    when(resourceResolver.getResources(anyString())).thenReturn(resources);
    return new RunbookRepository(resourceResolver);
  }

  @Test
  void returnsShortExcerptWithoutTruncationForShortContent() throws IOException {
    RunbookRepository repository = repositoryOf("Short 5xx runbook body.");

    assertThat(repository.search("5xx")).containsExactly("[runbook-0.md] Short 5xx runbook body.");
  }

  @Test
  void truncatesLongSectionsToKeepModelContextLean() throws IOException {
    RunbookRepository repository = repositoryOf("latency " + "x".repeat(400));

    var results = repository.search("latency");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst()).endsWith("...");
  }

  @Test
  void returnsOnlyTheMatchingSectionOfARunbook() throws IOException {
    RunbookRepository repository =
        repositoryOf(
            """
            # Runbook: Example

            ## Symptoms
            Users report timeouts.

            ## Recommended Actions
            Restart the connection pool.
            """);

    assertThat(repository.search("pool"))
        .containsExactly("[runbook-0.md] ## Recommended Actions Restart the connection pool.");
  }

  @Test
  void ranksSectionsMatchingMoreKeywordsFirst() throws IOException {
    RunbookRepository repository =
        repositoryOf(
            "## One\nOnly latency here.",
            "## Two\nBoth latency and timeouts here.",
            "## Three\nNothing relevant.");

    var results = repository.search("latency timeouts");

    assertThat(results).hasSize(2);
    assertThat(results.getFirst()).contains("runbook-1.md");
  }

  @Test
  void matchesAreCaseInsensitive() throws IOException {
    RunbookRepository repository = repositoryOf("## Symptoms\nElevated LATENCY observed.");

    assertThat(repository.search("Latency")).hasSize(1);
  }

  @Test
  void matchesHyphenatedServiceNames() throws IOException {
    RunbookRepository repository = repositoryOf("## Symptoms\nErrors from customer-api upstream.");

    assertThat(repository.search("customer-api")).hasSize(1);
  }

  @Test
  void handlesResourcesWithoutFilename() throws IOException {
    Resource resource =
        new ByteArrayResource("## Symptoms\nElevated latency.".getBytes(StandardCharsets.UTF_8));
    ResourcePatternResolver resourceResolver = mock(ResourcePatternResolver.class);
    when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {resource});

    RunbookRepository repository = new RunbookRepository(resourceResolver);

    assertThat(repository.search("latency")).containsExactly("[] ## Symptoms Elevated latency.");
  }

  @Test
  void failsFastWhenRunbooksCannotBeListed() throws IOException {
    ResourcePatternResolver resourceResolver = mock(ResourcePatternResolver.class);
    when(resourceResolver.getResources(anyString())).thenThrow(new IOException("boom"));

    assertThatThrownBy(() -> new RunbookRepository(resourceResolver))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Failed to load runbooks");
  }

  @Test
  void failsFastWhenARunbookCannotBeRead() throws IOException {
    Resource unreadable = mock(Resource.class);
    when(unreadable.getContentAsByteArray()).thenThrow(new IOException("boom"));
    ResourcePatternResolver resourceResolver = mock(ResourcePatternResolver.class);
    when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] {unreadable});

    assertThatThrownBy(() -> new RunbookRepository(resourceResolver))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Failed to read runbook");
  }
}

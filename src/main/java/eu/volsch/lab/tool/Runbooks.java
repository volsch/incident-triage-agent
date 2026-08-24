package eu.volsch.lab.tool;

import java.util.Arrays;
import java.util.List;

/**
 * Shared constants and helpers for the local runbook tools, keeping the classpath location, section
 * chunking, document metadata key and excerpt formatting identical across the keyword-based ({@link
 * RunbookSearchTool}) and embedding-based ({@link RunbookSemanticSearchTool}) retrieval paths.
 *
 * <p>Both tools deliberately search the <em>same corpus split into the same chunks</em>, so the
 * only difference between them is the retrieval strategy (exact keyword matching vs. embedding
 * similarity). That makes the two genuinely comparable — which is the point of offering both.
 */
final class Runbooks {

  /** Maximum number of runbook sections either search tool returns for a single query. */
  static final int MAX_RESULTS = 3;

  /** Classpath location pattern of the bundled runbook markdown files. */
  static final String CLASSPATH_PATTERN = "classpath:runbooks/*.md";

  /** Document metadata key under which a chunk's source runbook filename is stored. */
  static final String METADATA_FILENAME = "filename";

  /** Maximum excerpt length, in characters, handed to the LLM before truncation. */
  private static final int MAX_EXCERPT_LENGTH = 300;

  private Runbooks() {
    // Utility class, not instantiable.
  }

  /**
   * Reduces runbook markdown to a single-line, length-capped excerpt suitable for passing to an LLM
   * as tool-call evidence. Whitespace is collapsed so markdown layout doesn't waste model context,
   * and the length cap keeps a single tool result from crowding out the rest of the conversation.
   *
   * @param content the runbook content to condense, may be {@code null}
   * @return a single-line excerpt, truncated with a trailing ellipsis if it exceeds the length cap
   */
  static String excerpt(String content) {
    String oneLine = (content == null ? "" : content).replaceAll("\\s+", " ").trim();
    return oneLine.length() > MAX_EXCERPT_LENGTH
        ? oneLine.substring(0, MAX_EXCERPT_LENGTH) + "..."
        : oneLine;
  }

  /**
   * Formats a runbook excerpt together with its source filename, as returned to the LLM by both
   * runbook tools.
   *
   * @param filename the source runbook filename
   * @param content the runbook content to condense, may be {@code null}
   * @return a single-line, filename-prefixed excerpt
   */
  static String format(String filename, String content) {
    return "[%s] %s".formatted(filename, excerpt(content));
  }

  /**
   * Renders a list of retrieval hits as a single line for the recorded evidence trail.
   *
   * @param matches the excerpts actually returned to the model
   * @return the joined excerpts, or an explicit "no matches" marker if there were none
   */
  static String summarize(List<String> matches) {
    return matches.isEmpty() ? "no matches" : String.join(" | ", matches);
  }

  /**
   * Splits runbook markdown into per-section chunks, one per {@code ##} heading (e.g. "Symptoms",
   * "Likely Causes", "Recommended Actions"), so that retrieval returns a focused, self-contained
   * excerpt instead of the top of whichever file happened to match.
   *
   * @param content the full runbook markdown content
   * @return the non-empty, stripped section chunks, in document order
   */
  static List<String> splitSections(String content) {
    return Arrays.stream(content.split("(?=\\n## )"))
        .map(String::strip)
        .filter(section -> !section.isEmpty())
        .toList();
  }
}

package eu.volsch.lab.tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Repository;

/**
 * Loads local runbook markdown files at startup and performs simple, dependency-free keyword
 * matching over their sections. This intentionally avoids embeddings — it is a lexical lookup, and
 * exists to contrast with the embedding-based {@link RunbookSemanticSearchTool}.
 *
 * <p>To make that contrast meaningful, matching is deliberately <em>selective</em> rather than
 * naive: queries are tokenized into whole words, common English stop words are dropped, and matches
 * are anchored to word boundaries. A naive {@code content.contains(token)} implementation would
 * match "in" inside "instance" and "the" inside "there", so every query would return every runbook
 * — which would make this tool useless and its comparison with semantic search meaningless.
 */
@Repository
class RunbookRepository {

  /** A single runbook section: its source filename and the section's markdown text. */
  private record Section(String filename, String text) {}

  /** A section together with the number of distinct query keywords it matched. */
  private record ScoredSection(Section section, long score) {}

  /**
   * Words carrying no retrieval signal. Dropping them is what stops a query like "the database is
   * on fire" from matching every runbook merely because they all contain the word "the".
   */
  private static final Set<String> STOP_WORDS =
      Set.of(
          "about", "after", "again", "all", "also", "and", "any", "are", "been", "before", "being",
          "but", "can", "could", "did", "does", "during", "for", "from", "get", "getting", "had",
          "has", "have", "how", "into", "its", "just", "last", "more", "most", "not", "now", "only",
          "our", "out", "over", "past", "per", "see", "seeing", "seen", "should", "since", "some",
          "still", "than", "that", "the", "their", "then", "there", "these", "they", "this",
          "those", "under", "very", "was", "were", "what", "when", "where", "why", "will", "with",
          "would", "you", "your");

  /**
   * Tokens shorter than this ("in", "on", "is", "we") are almost always noise or match far too
   * broadly, so they are ignored entirely.
   */
  private static final int MIN_KEYWORD_LENGTH = 3;

  private final List<Section> sections;

  /**
   * Creates the repository, eagerly loading all runbook markdown files from the classpath and
   * splitting them into sections.
   *
   * @param resourceResolver resolver used to locate {@code classpath:runbooks/*.md} files
   */
  RunbookRepository(ResourcePatternResolver resourceResolver) {
    this.sections = loadSections(resourceResolver);
  }

  /**
   * Loads every runbook markdown file found on the classpath and splits each into sections.
   *
   * @param resourceResolver resolver used to locate the runbook resources
   * @return the loaded runbook sections
   * @throws UncheckedIOException if the resources cannot be listed or read
   */
  private static List<Section> loadSections(ResourcePatternResolver resourceResolver) {
    try {
      Resource[] resources = resourceResolver.getResources(Runbooks.CLASSPATH_PATTERN);
      return Arrays.stream(resources).flatMap(resource -> readSections(resource).stream()).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load runbooks from classpath", e);
    }
  }

  /**
   * Reads a single runbook resource as UTF-8 text and splits it into sections.
   *
   * @param resource the runbook markdown resource to read
   * @return the sections of this runbook
   * @throws UncheckedIOException if the resource content cannot be read
   */
  private static List<Section> readSections(Resource resource) {
    try {
      String content = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
      String filename = resource.getFilename() == null ? "" : resource.getFilename();
      return Runbooks.splitSections(content).stream()
          .map(text -> new Section(filename, text))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read runbook " + resource.getFilename(), e);
    }
  }

  /**
   * Returns the runbook sections matching the most distinct keywords from the query, best match
   * first. Only whole-word matches of meaningful (non-stop-word) keywords count, and at most {@link
   * Runbooks#MAX_RESULTS} sections are returned, to keep the model's context lean.
   *
   * @param query free-text keywords describing the symptom, e.g. "5xx errors"
   * @return matching runbook excerpts ranked by match count, or an empty list if the query is
   *     blank, consists only of stop words, or matches nothing
   */
  List<String> search(String query) {
    List<Pattern> keywords = compileKeywords(query);
    if (keywords.isEmpty()) {
      return List.of();
    }
    return sections.stream()
        .map(section -> new ScoredSection(section, countMatches(section.text(), keywords)))
        .filter(scored -> scored.score() > 0)
        .sorted(Comparator.comparingLong(ScoredSection::score).reversed())
        .limit(Runbooks.MAX_RESULTS)
        .map(scored -> Runbooks.format(scored.section().filename(), scored.section().text()))
        .toList();
  }

  /**
   * Reduces a raw query to distinct whole-word matchers for the keywords worth searching for,
   * discarding punctuation, very short tokens and stop words.
   *
   * @param query the raw query, may be {@code null} or blank
   * @return one compiled whole-word pattern per meaningful keyword, possibly empty
   */
  private static List<Pattern> compileKeywords(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+"))
        .map(RunbookRepository::stripHyphens)
        .filter(token -> token.length() >= MIN_KEYWORD_LENGTH)
        .filter(token -> !STOP_WORDS.contains(token))
        .distinct()
        .map(RunbookRepository::wholeWordPattern)
        .toList();
  }

  /**
   * Removes any leading and trailing hyphens left over after tokenizing, so that a token like
   * {@code "-api-"} is matched as {@code "api"}.
   *
   * @param token the raw token, possibly wrapped in hyphens
   * @return the token with leading and trailing hyphens stripped
   */
  private static String stripHyphens(String token) {
    int start = 0;
    int end = token.length();
    while (start < end && token.charAt(start) == '-') {
      start++;
    }
    while (end > start && token.charAt(end - 1) == '-') {
      end--;
    }
    return token.substring(start, end);
  }

  /**
   * Builds a matcher for a keyword occurring as a standalone word, so that "in" does not match
   * "instance" and "log" does not match "logic". Uses explicit look-around rather than {@code \b}
   * because runbook text and service names contain hyphens (e.g. {@code customer-api}).
   *
   * @param keyword the lower-cased keyword to match
   * @return a compiled whole-word pattern for the keyword
   */
  private static Pattern wholeWordPattern(String keyword) {
    return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])");
  }

  /**
   * Counts how many distinct keywords occur as whole words in the given section text.
   *
   * @param text the runbook section text to search
   * @param keywords the compiled whole-word keyword patterns
   * @return the number of distinct keywords found
   */
  private static long countMatches(String text, List<Pattern> keywords) {
    String haystack = text.toLowerCase(Locale.ROOT);
    return keywords.stream().filter(keyword -> keyword.matcher(haystack).find()).count();
  }
}

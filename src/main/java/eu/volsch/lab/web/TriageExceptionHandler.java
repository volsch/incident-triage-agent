package eu.volsch.lab.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * Translates infrastructure failures from the triage flow into clean HTTP responses instead of a
 * bare 500 with a full stack trace.
 */
@RestControllerAdvice
public class TriageExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(TriageExceptionHandler.class);

  /**
   * Handles the case where the configured AI model backend (e.g. a local Ollama server) cannot be
   * reached, returning HTTP 503 with a concise, actionable message rather than propagating the
   * connection stack trace.
   *
   * @param ex the connectivity failure raised while calling the model backend
   * @return an RFC 7807 problem detail describing the unreachable backend
   */
  @ExceptionHandler(ResourceAccessException.class)
  public ProblemDetail handleModelBackendUnavailable(ResourceAccessException ex) {
    log.warn("AI model backend unreachable: {}", ex.getMessage());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The AI model backend is unreachable. Ensure the Ollama server is running and reachable"
                + " at the configured base URL (spring.ai.ollama.base-url / OLLAMA_BASE_URL).");
    problem.setTitle("Model backend unavailable");
    return problem;
  }
}

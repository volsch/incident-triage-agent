package eu.volsch.lab.model;

import java.util.List;

/**
 * Structured assessment returned by the triage agent after gathering evidence via tool calls. This
 * is the JSON shape the LLM output is mapped into.
 *
 * @param severity overall severity classification
 * @param incidentSummary concise human-readable summary of the incident
 * @param evidence facts gathered from tool calls (system status, runbook hits)
 * @param recommendedNextSteps ordered list of suggested remediation/investigation actions
 */
public record IncidentAssessment(
    Severity severity,
    String incidentSummary,
    List<String> evidence,
    List<String> recommendedNextSteps) {

  /**
   * Compact constructor defensively copies the list parameters into immutable lists so that neither
   * the caller nor this record can mutate state after construction. Missing lists are treated as
   * empty rather than rejected, because the model is free to omit fields from its JSON response and
   * a triage result should not fail on that alone.
   */
  public IncidentAssessment {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    recommendedNextSteps =
        recommendedNextSteps == null ? List.of() : List.copyOf(recommendedNextSteps);
  }

  /**
   * Returns a copy of this assessment carrying the given evidence, leaving all other fields
   * untouched. Used to replace the model's own account of its investigation with the recorded
   * results of the tool calls that actually ran.
   *
   * @param verifiedEvidence the real, recorded tool results
   * @return a new assessment with the supplied evidence
   */
  public IncidentAssessment withEvidence(List<String> verifiedEvidence) {
    return new IncidentAssessment(
        severity, incidentSummary, verifiedEvidence, recommendedNextSteps);
  }
}

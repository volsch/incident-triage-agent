package eu.volsch.lab.web;

import eu.volsch.lab.agent.TriageAgentService;
import eu.volsch.lab.model.IncidentAssessment;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the incident triage agent over HTTP. POST a raw incident description and receive a
 * structured, LLM-generated assessment back.
 */
@RestController
public class TriageController {

  private final TriageAgentService triageAgentService;

  /**
   * Creates the controller.
   *
   * @param triageAgentService the service that performs the actual triage
   */
  public TriageController(TriageAgentService triageAgentService) {
    this.triageAgentService = triageAgentService;
  }

  /**
   * Submits a raw incident description for triage and returns the structured assessment.
   *
   * @param request the incident description to triage
   * @return HTTP 200 with the structured {@link IncidentAssessment} in the body
   */
  @PostMapping("/api/triage")
  public ResponseEntity<IncidentAssessment> triage(@Valid @RequestBody TriageRequest request) {
    IncidentAssessment assessment = triageAgentService.triage(request.description());
    return ResponseEntity.ok(assessment);
  }
}

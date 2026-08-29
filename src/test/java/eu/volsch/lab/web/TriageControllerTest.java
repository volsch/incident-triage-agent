package eu.volsch.lab.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.volsch.lab.agent.TriageAgentService;
import eu.volsch.lab.model.IncidentAssessment;
import eu.volsch.lab.model.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

/** {@code @WebMvcTest} slice test for {@link TriageController}, with the agent service mocked. */
@WebMvcTest(TriageController.class)
class TriageControllerTest {

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private TriageAgentService triageAgentService;

  @Test
  void returnsStructuredAssessmentFromAgent() throws Exception {
    IncidentAssessment assessment =
        new IncidentAssessment(
            Severity.HIGH,
            "customer-api elevated 5xx error rate",
            List.of("error rate 47.5%", "runbook: 5xx-errors.md"),
            List.of("Check recent deployments", "Inspect connection pool"));
    when(triageAgentService.triage(anyString())).thenReturn(assessment);

    mockMvc
        .perform(
            post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TriageRequest("customer-api returning 5xx errors"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.severity").value("HIGH"))
        .andExpect(jsonPath("$.incidentSummary").value("customer-api elevated 5xx error rate"))
        .andExpect(jsonPath("$.evidence.length()").value(2))
        .andExpect(jsonPath("$.recommendedNextSteps.length()").value(2));
  }

  @Test
  void rejectsBlankDescription() throws Exception {
    mockMvc
        .perform(
            post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TriageRequest(""))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsServiceUnavailableWhenModelBackendIsDown() throws Exception {
    when(triageAgentService.triage(anyString()))
        .thenThrow(new ResourceAccessException("Connection refused"));

    mockMvc
        .perform(
            post("/api/triage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TriageRequest("customer-api returning 5xx errors"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.title").value("Model backend unavailable"));
  }
}

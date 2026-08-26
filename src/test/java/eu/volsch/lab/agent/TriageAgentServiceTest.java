package eu.volsch.lab.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.volsch.lab.model.IncidentAssessment;
import eu.volsch.lab.model.Severity;
import eu.volsch.lab.tool.RunbookSearchTool;
import eu.volsch.lab.tool.RunbookSemanticSearchTool;
import eu.volsch.lab.tool.SystemStatusTool;
import eu.volsch.lab.tool.ToolCallRecorder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Unit test for {@link TriageAgentService}, mocking the {@link ChatClient} fluent API.
 *
 * <p>Beyond the happy path, these tests pin down the guarantee the service exists to provide: the
 * {@code evidence} it reports is the recorded result of tool calls that really happened, never the
 * model's own (potentially invented) account of them.
 */
@ExtendWith(MockitoExtension.class)
class TriageAgentServiceTest {

  private static final String FINDINGS = "getSystemStatus reported an elevated 5xx error rate";

  @Mock private ChatClient chatClient;
  @Mock private ChatClient.ChatClientRequestSpec requestSpec;
  @Mock private ChatClient.CallResponseSpec callResponseSpec;
  @Mock private SystemStatusTool systemStatusTool;
  @Mock private RunbookSearchTool runbookSearchTool;
  @Mock private RunbookSemanticSearchTool runbookSemanticSearchTool;
  @Mock private ToolCallRecorder toolCallRecorder;

  /** Stubs the fluent chain up to (but excluding) the terminal call. */
  private void stubPromptChain() {
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
  }

  /**
   * Stubs the whole two-phase fluent chain: the investigation call returns prose, the assessment
   * call returns the given structured response.
   */
  private TriageAgentService serviceReturning(IncidentAssessment modelResponse) {
    stubPromptChain();
    when(requestSpec.call()).thenReturn(callResponseSpec);
    when(callResponseSpec.content()).thenReturn(FINDINGS);
    when(callResponseSpec.entity(IncidentAssessment.class)).thenReturn(modelResponse);
    return newService();
  }

  private TriageAgentService newService() {
    return new TriageAgentService(
        chatClient,
        systemStatusTool,
        runbookSearchTool,
        runbookSemanticSearchTool,
        toolCallRecorder);
  }

  @Test
  void triageDrivesToolCallingPromptAndMapsStructuredResponse() {
    IncidentAssessment modelResponse =
        new IncidentAssessment(
            Severity.HIGH,
            "customer-api elevated 5xx error rate",
            List.of(),
            List.of("Check recent deployments"));
    TriageAgentService service = serviceReturning(modelResponse);
    when(toolCallRecorder.recordedCalls()).thenReturn(List.of());

    IncidentAssessment actual =
        service.triage("customer-api returning 5xx errors for the past 10 minutes");

    assertThat(actual.severity()).isEqualTo(Severity.HIGH);
    assertThat(actual.incidentSummary()).isEqualTo("customer-api elevated 5xx error rate");
    assertThat(actual.recommendedNextSteps()).containsExactly("Check recent deployments");
    verify(requestSpec)
        .tools((Object) systemStatusTool, runbookSearchTool, runbookSemanticSearchTool);
    verify(requestSpec).user("customer-api returning 5xx errors for the past 10 minutes");
  }

  @Test
  void investigatesWithToolsBeforeAskingForTheStructuredAssessment() {
    TriageAgentService service =
        serviceReturning(
            new IncidentAssessment(Severity.LOW, "summary", List.of(), List.of("step")));
    when(toolCallRecorder.recordedCalls()).thenReturn(List.of());

    service.triage("customer-api returning 5xx errors");

    // Structured-output instructions suppress tool calling when both are requested in one call,
    // so the tools must run in their own prose call before the assessment is mapped.
    InOrder inOrder = inOrder(requestSpec, callResponseSpec);
    inOrder
        .verify(requestSpec)
        .tools((Object) systemStatusTool, runbookSearchTool, runbookSemanticSearchTool);
    inOrder.verify(callResponseSpec).content();
    inOrder.verify(callResponseSpec).entity(IncidentAssessment.class);
    verify(requestSpec, times(1)).tools(any(Object[].class));
  }

  @Test
  void passesTheGatheredFindingsIntoTheAssessmentPrompt() {
    TriageAgentService service =
        serviceReturning(
            new IncidentAssessment(Severity.LOW, "summary", List.of(), List.of("step")));
    when(toolCallRecorder.recordedCalls()).thenReturn(List.of());

    service.triage("customer-api is failing");

    ArgumentCaptor<String> userMessages = ArgumentCaptor.forClass(String.class);
    verify(requestSpec, times(2)).user(userMessages.capture());
    assertThat(userMessages.getAllValues().get(0)).isEqualTo("customer-api is failing");
    assertThat(userMessages.getAllValues().get(1))
        .contains(FINDINGS)
        .contains("customer-api is failing");
  }

  @Test
  void replacesModelAuthoredEvidenceWithTheRealToolResults() {
    IncidentAssessment modelResponse =
        new IncidentAssessment(
            Severity.CRITICAL,
            "customer-api is down",
            List.of("the production database has been deleted"),
            List.of("Restore from backup"));
    TriageAgentService service = serviceReturning(modelResponse);
    when(toolCallRecorder.recordedCalls())
        .thenReturn(List.of("getSystemStatus(customer-api) -> healthy=false"));

    IncidentAssessment actual = service.triage("customer-api is failing");

    assertThat(actual.evidence())
        .containsExactly("getSystemStatus(customer-api) -> healthy=false")
        .doesNotContain("the production database has been deleted");
  }

  @Test
  void reportsEmptyEvidenceWhenTheModelCalledNoTools() {
    IncidentAssessment modelResponse =
        new IncidentAssessment(
            Severity.LOW,
            "nothing to see here",
            List.of("I checked the metrics myself"),
            List.of("Keep monitoring"));
    TriageAgentService service = serviceReturning(modelResponse);
    when(toolCallRecorder.recordedCalls()).thenReturn(List.of());

    assertThat(service.triage("everything looks fine").evidence()).isEmpty();
  }

  @Test
  void bracketsEachRunWithAFreshRecording() {
    TriageAgentService service =
        serviceReturning(
            new IncidentAssessment(Severity.LOW, "summary", List.of(), List.of("step")));
    when(toolCallRecorder.recordedCalls()).thenReturn(List.of());

    service.triage("customer-api is failing");

    verify(toolCallRecorder).start();
    verify(toolCallRecorder).clear();
  }

  @Test
  void releasesRecordedStateEvenWhenTheModelCallFails() {
    stubPromptChain();
    when(requestSpec.call()).thenThrow(new IllegalStateException("model unavailable"));
    TriageAgentService service = newService();

    assertThatThrownBy(() -> service.triage("customer-api is failing"))
        .isInstanceOf(IllegalStateException.class);

    verify(toolCallRecorder).clear();
  }

  @Test
  void failsClearlyWhenTheModelResponseCannotBeMapped() {
    TriageAgentService service = serviceReturning(null);

    assertThatThrownBy(() -> service.triage("customer-api is failing"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("could not be mapped");

    verify(toolCallRecorder).clear();
  }
}

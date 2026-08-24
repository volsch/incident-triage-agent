package eu.volsch.lab.agent;

import eu.volsch.lab.model.IncidentAssessment;
import eu.volsch.lab.tool.RunbookSearchTool;
import eu.volsch.lab.tool.RunbookSemanticSearchTool;
import eu.volsch.lab.tool.SystemStatusTool;
import eu.volsch.lab.tool.ToolCallRecorder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the tool-calling triage agent: hands the raw incident description to the LLM
 * together with the available {@code @Tool}-annotated methods, lets Spring AI manage the
 * tool-calling loop, and maps the final model response into a structured {@link
 * IncidentAssessment}.
 *
 * <p>This is advisory only: the agent is read-only and cannot perform any corrective infrastructure
 * action. Output is non-deterministic and intended for human review.
 */
@Service
public class TriageAgentService {

  private static final String SYSTEM_PROMPT =
      """
            You are an incident triage assistant for a software operations team.
            Given a raw incident description, use the available tools to gather evidence:
            - getSystemStatus(service): fetch synthetic metrics and active alerts for a service.
            - searchRunbook(query): look up relevant local runbook excerpts by exact keyword match.
              Pass only meaningful keywords (service names, symptoms, error codes) — common words
              are ignored.
            - searchRunbookSemantic(query): look up relevant local runbook excerpts by semantic
              (embedding-based) similarity; prefer this when the incident wording does not share
              exact keywords with the runbooks, or to double-check searchRunbook's results.
            Identify the affected service(s) from the description, call the tools as needed,
            then produce a structured assessment.

            Classify severity using these definitions, based on what the tools actually reported:
            - CRITICAL: service is down or unusable for most users; error rate above 25%.
            - HIGH: major degradation with clear user impact; error rate 5-25%, or latency
              several times the normal baseline, or an active error-rate alert.
            - MEDIUM: noticeable but contained degradation; error rate 1-5%, or elevated
              latency without widespread failures.
            - LOW: little or no user impact; healthy metrics, or a purely informational report.
            If the tools report no data for a service, do not assume the worst: prefer LOW or
            MEDIUM and say explicitly that status data was unavailable.

            Keep incidentSummary to one or two factual sentences, and make recommendedNextSteps
            concrete, ordered, and drawn from the runbooks you retrieved.
            Leave the evidence field empty: it is filled in automatically from the actual tool
            results, so never invent, paraphrase or restate tool output there.
            You are strictly advisory: never claim to have taken any corrective action.
            """;

  private final ChatClient chatClient;
  private final SystemStatusTool systemStatusTool;
  private final RunbookSearchTool runbookSearchTool;
  private final RunbookSemanticSearchTool runbookSemanticSearchTool;
  private final ToolCallRecorder toolCallRecorder;

  /**
   * Creates the triage agent service.
   *
   * @param chatClient the chat client used to drive the tool-calling conversation
   * @param systemStatusTool the {@code getSystemStatus} tool made available to the model
   * @param runbookSearchTool the {@code searchRunbook} keyword-search tool made available to the
   *     model
   * @param runbookSemanticSearchTool the {@code searchRunbookSemantic} embedding-based search tool
   *     made available to the model
   * @param toolCallRecorder captures what the tools actually returned, used to ground the reported
   *     evidence
   */
  public TriageAgentService(
      ChatClient chatClient,
      SystemStatusTool systemStatusTool,
      RunbookSearchTool runbookSearchTool,
      RunbookSemanticSearchTool runbookSemanticSearchTool,
      ToolCallRecorder toolCallRecorder) {
    this.chatClient = chatClient;
    this.systemStatusTool = systemStatusTool;
    this.runbookSearchTool = runbookSearchTool;
    this.runbookSemanticSearchTool = runbookSemanticSearchTool;
    this.toolCallRecorder = toolCallRecorder;
  }

  /**
   * Runs the tool-calling triage loop for a raw incident description and maps the model's final
   * response into a structured {@link IncidentAssessment}.
   *
   * <p>The returned {@code evidence} is not the model's own account of its investigation but the
   * recorded results of the tool calls that genuinely happened, so it cannot be hallucinated. The
   * severity, summary and next steps remain model-generated and therefore advisory.
   *
   * @param incidentDescription the raw, free-text incident description
   * @return the structured assessment produced by the LLM agent, with verified evidence
   * @throws IllegalStateException if the model's response could not be mapped to an assessment,
   *     which a weaker or non-tool-calling model can genuinely produce
   */
  public IncidentAssessment triage(String incidentDescription) {
    toolCallRecorder.start();
    try {
      IncidentAssessment assessment =
          chatClient
              .prompt()
              .system(SYSTEM_PROMPT)
              .user(incidentDescription)
              .tools(systemStatusTool, runbookSearchTool, runbookSemanticSearchTool)
              .call()
              .entity(IncidentAssessment.class);
      if (assessment == null) {
        throw new IllegalStateException(
            "The model returned a response that could not be mapped to an IncidentAssessment");
      }
      return assessment.withEvidence(toolCallRecorder.recordedCalls());
    } finally {
      toolCallRecorder.clear();
    }
  }
}

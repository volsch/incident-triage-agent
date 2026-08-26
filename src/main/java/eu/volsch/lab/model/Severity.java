package eu.volsch.lab.model;

/**
 * Coarse incident severity classification used in the structured triage output. The thresholds
 * below mirror the rubric given to the model in {@code TriageAgentService}'s assessment system
 * prompt — keep the two in sync, otherwise the enum documents one scale while the model applies
 * another.
 */
public enum Severity {

  /** Little or no user impact: healthy metrics, or a purely informational report. */
  LOW,

  /** Noticeable but contained degradation: roughly 1-5% errors, or elevated latency. */
  MEDIUM,

  /** Major degradation with clear user impact: roughly 5-25% errors, or an error-rate alert. */
  HIGH,

  /** Service down or unusable for most users: above roughly 25% errors. */
  CRITICAL
}

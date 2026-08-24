package eu.volsch.lab.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for POST /api/triage.
 *
 * @param description the raw, free-text incident description submitted by the user
 */
public record TriageRequest(@NotBlank String description) {}

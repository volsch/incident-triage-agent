package eu.volsch.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the incident-triage-agent application. */
@SpringBootApplication
public class IncidentTriageAgentApplication {

  /**
   * Boots the Spring application context.
   *
   * @param args standard command-line arguments, forwarded to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(IncidentTriageAgentApplication.class, args);
  }
}

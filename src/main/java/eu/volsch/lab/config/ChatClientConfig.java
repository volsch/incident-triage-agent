package eu.volsch.lab.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the {@link ChatClient} used by the triage agent. Ollama (running the open-source Qwen3
 * model locally) is the default, offline-friendly model provider for this lab. Activate the
 * "openai" Spring profile to swap in an OpenAI-compatible model instead, demonstrating Spring AI's
 * model-agnostic ChatClient abstraction without touching any application code.
 */
@Configuration
public class ChatClientConfig {

  /**
   * Builds the default {@link ChatClient}, backed by a locally running Ollama server. Active
   * whenever the {@code openai} profile is not set.
   *
   * @param chatModel the auto-configured Ollama chat model
   * @return a {@link ChatClient} wrapping the Ollama model
   */
  @Bean
  @Profile("!openai")
  public ChatClient ollamaChatClient(OllamaChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }

  /**
   * Builds an OpenAI-compatible {@link ChatClient}, demonstrating that swapping model providers
   * requires no changes to {@link eu.volsch.lab.agent.TriageAgentService}. Active only when the
   * {@code openai} profile is set.
   *
   * @param chatModel the auto-configured OpenAI chat model
   * @return a {@link ChatClient} wrapping the OpenAI model
   */
  @Bean
  @Profile("openai")
  public ChatClient openAiChatClient(OpenAiChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }
}

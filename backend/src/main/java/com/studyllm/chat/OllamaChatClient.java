package com.studyllm.chat;

import com.studyllm.ai.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Local Ollama chat/generation (qwen3:8b by default), called via {@code /api/generate}. */
@Component
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaChatClient {

  private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);

  private record GenerateRequest(String model, String prompt, boolean stream) {}

  private record GenerateResponse(String response) {}

  private final RestClient restClient;
  private final OllamaProperties properties;

  public OllamaChatClient(OllamaProperties properties) {
    this.properties = properties;
    this.restClient = RestClient.create(properties.baseUrl());
  }

  /** Sends a prompt to Ollama's non-streaming generate endpoint and returns the trimmed answer. */
  public String generate(String prompt) {
    try {
      GenerateResponse response =
          restClient
              .post()
              .uri("/api/generate")
              .body(new GenerateRequest(properties.chatModel(), prompt, false))
              .retrieve()
              .body(GenerateResponse.class);
      if (response == null || response.response() == null) {
        throw new IllegalStateException("Ollama returned no response for the chat prompt");
      }
      return response.response().trim();
    } catch (RestClientException e) {
      log.error("Chat generation call to Ollama ({}) failed", properties.chatModel(), e);
      throw e;
    }
  }
}

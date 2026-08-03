package com.studyllm.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Local Ollama embeddings (nomic-embed-text, 768 dims), shared by source ingestion and chat
 * retrieval — the single seam where embedding generation happens (research.md §3).
 */
@Component
@EnableConfigurationProperties(OllamaProperties.class)
public class EmbeddingClient {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

  private record EmbedRequest(String model, String prompt) {}

  private record EmbedResponse(float[] embedding) {}

  private final RestClient restClient;
  private final OllamaProperties properties;

  public EmbeddingClient(OllamaProperties properties) {
    this.properties = properties;
    this.restClient = RestClient.create(properties.baseUrl());
  }

  public float[] embed(String text) {
    try {
      EmbedResponse response =
          restClient
              .post()
              .uri("/api/embeddings")
              .body(new EmbedRequest(properties.embeddingModel(), text))
              .retrieve()
              .body(EmbedResponse.class);
      if (response == null || response.embedding() == null) {
        throw new IllegalStateException("Ollama returned no embedding for the given text");
      }
      return response.embedding();
    } catch (RestClientException e) {
      log.error("Embedding call to Ollama ({}) failed", properties.embeddingModel(), e);
      throw e;
    }
  }
}

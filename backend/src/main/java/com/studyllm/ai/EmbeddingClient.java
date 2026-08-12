package com.studyllm.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

  // Without these, an Ollama that accepts the connection but never answers (hung server, dropped
  // packets) blocks the calling thread forever — and since embedding runs inside source ingestion,
  // that leaves the source stuck in PROCESSING with no error and no way out but a restart.
  // Embedding is a small, fast model, so a couple of minutes is already far past normal.
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofMinutes(2);

  private final RestClient restClient;
  private final OllamaProperties properties;

  public EmbeddingClient(OllamaProperties properties) {
    this.properties = properties;
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }

  /** Requests a 768-dim embedding vector for the given text from Ollama; throws on any failure. */
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

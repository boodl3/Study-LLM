package com.studyllm.chat;

import com.studyllm.ai.OllamaProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Local Ollama chat/generation (qwen3:8b by default), streamed token-by-token via {@code /api/generate}. */
@Component
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaChatClient {

  private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private record GenerateRequest(
      String model, String prompt, boolean stream, boolean think, String keep_alive) {}

  private record GenerateChunk(String response, boolean done) {}

  private final HttpClient httpClient;
  private final OllamaProperties properties;

  public OllamaChatClient(OllamaProperties properties) {
    this.properties = properties;
    this.httpClient = HttpClient.newHttpClient();
  }

  /**
   * Streams a prompt to Ollama's generate endpoint, invoking {@code onToken} for each piece of
   * generated text as it arrives, and returns the full concatenated answer once generation ends.
   */
  public String generateStreaming(String prompt, Consumer<String> onToken) {
    // think=false skips qwen3's hidden reasoning tokens (pure latency for a grounded-answer
    // prompt); keep_alive keeps the model resident so it isn't reloaded from disk every request.
    String body =
        MAPPER.writeValueAsString(new GenerateRequest(properties.chatModel(), prompt, true, false, "30m"));
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.baseUrl() + "/api/generate"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMinutes(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpResponse<java.util.stream.Stream<String>> response;
    try {
      response = httpClient.send(request, BodyHandlers.ofLines());
    } catch (IOException e) {
      log.error("Chat generation call to Ollama ({}) failed", properties.chatModel(), e);
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while streaming chat generation", e);
    }

    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Ollama returned status " + response.statusCode() + " for chat generation");
    }

    StringBuilder full = new StringBuilder();
    response
        .body()
        .filter(line -> !line.isBlank())
        .forEach(
            line -> {
              GenerateChunk chunk = MAPPER.readValue(line, GenerateChunk.class);
              if (chunk.response() != null && !chunk.response().isEmpty()) {
                full.append(chunk.response());
                onToken.accept(chunk.response());
              }
            });
    return full.toString().trim();
  }
}

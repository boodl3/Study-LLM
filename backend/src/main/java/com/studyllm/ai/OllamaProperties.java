package com.studyllm.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studyllm.ollama")
public record OllamaProperties(
    String baseUrl, String chatModel, String embeddingModel, String visionModel, int ocrConcurrency) {}

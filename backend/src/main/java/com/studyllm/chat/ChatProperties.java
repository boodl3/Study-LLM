package com.studyllm.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studyllm.chat")
public record ChatProperties(
    int retrievalTopK, double relevanceMaxDistance, int retrievalNeighborRadius) {}

package com.studyllm.chat.dto;

import com.studyllm.chat.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    ChatMessage.Role role,
    String content,
    List<CitedSourceDto> citedSources,
    boolean notFoundInSources,
    Instant createdAt) {}

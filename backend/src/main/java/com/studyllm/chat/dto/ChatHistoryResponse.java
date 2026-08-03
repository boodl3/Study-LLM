package com.studyllm.chat.dto;

import java.util.List;

public record ChatHistoryResponse(List<ChatMessageResponse> messages) {}

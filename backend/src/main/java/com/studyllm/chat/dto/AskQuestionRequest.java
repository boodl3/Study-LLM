package com.studyllm.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record AskQuestionRequest(@NotBlank String question) {}

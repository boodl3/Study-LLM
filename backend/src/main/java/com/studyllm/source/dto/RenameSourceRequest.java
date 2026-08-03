package com.studyllm.source.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameSourceRequest(@NotBlank String filename) {}

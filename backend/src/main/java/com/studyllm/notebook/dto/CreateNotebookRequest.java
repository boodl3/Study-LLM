package com.studyllm.notebook.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNotebookRequest(@NotBlank String title) {}

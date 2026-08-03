package com.studyllm.notebook.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameNotebookRequest(@NotBlank String title) {}

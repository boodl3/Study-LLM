package com.studyllm.source.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameFolderRequest(@NotBlank String folderName) {}

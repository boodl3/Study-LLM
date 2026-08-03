package com.studyllm.notebook.dto;

import java.time.Instant;
import java.util.UUID;

public record NotebookDto(UUID id, String title, int sourceCount, Instant lastActiveAt) {}

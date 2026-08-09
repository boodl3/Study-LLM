package com.studyllm.source.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ReorderSourcesRequest(@NotEmpty List<UUID> orderedIds) {}

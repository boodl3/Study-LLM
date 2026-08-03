package com.studyllm.source.dto;

import com.studyllm.source.Source;
import java.time.Instant;
import java.util.UUID;

public record SourceDto(
    UUID id,
    String filename,
    Source.FileType fileType,
    Source.ProcessingStatus status,
    String failureReason,
    Instant uploadedAt) {}

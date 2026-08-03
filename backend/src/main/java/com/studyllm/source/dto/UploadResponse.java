package com.studyllm.source.dto;

import com.studyllm.source.Source;
import java.util.UUID;

public record UploadResponse(UUID id, String filename, Source.ProcessingStatus status) {}

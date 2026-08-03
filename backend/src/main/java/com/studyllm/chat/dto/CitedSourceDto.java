package com.studyllm.chat.dto;

import java.util.UUID;

public record CitedSourceDto(UUID sourceId, String filename, String sectionLabel) {}

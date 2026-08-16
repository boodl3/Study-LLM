package com.studyllm.source.dto;

import jakarta.validation.constraints.NotBlank;

public record AddWebsiteRequest(@NotBlank String url, String folder) {}

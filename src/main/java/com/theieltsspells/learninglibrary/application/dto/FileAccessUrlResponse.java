package com.theieltsspells.learninglibrary.application.dto;

import java.time.OffsetDateTime;

public record FileAccessUrlResponse(String url, OffsetDateTime expiresAt) {}

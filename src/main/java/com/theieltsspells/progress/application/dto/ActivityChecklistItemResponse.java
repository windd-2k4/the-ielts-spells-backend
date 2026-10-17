package com.theieltsspells.progress.application.dto;

import java.util.UUID;

public record ActivityChecklistItemResponse(UUID id, String label, String description,
        short displayOrder, boolean required) {}

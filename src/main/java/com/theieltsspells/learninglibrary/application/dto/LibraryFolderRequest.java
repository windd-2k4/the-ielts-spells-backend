package com.theieltsspells.learninglibrary.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LibraryFolderRequest(
        @NotBlank @Size(max = 120) String name,
        UUID courseId
) {}

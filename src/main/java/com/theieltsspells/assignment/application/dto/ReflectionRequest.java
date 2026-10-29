package com.theieltsspells.assignment.application.dto;

import jakarta.validation.constraints.Size;

public record ReflectionRequest(@Size(max = 4000) String reflection) {}

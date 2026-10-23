package com.theieltsspells.writingevaluation.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WritingTeacherDraftRequest(
        @Size(max = 50000) String editedText,
        @Size(max = 200000) String editedHtml,
        @Size(max = 500) List<@Valid WritingTeacherAnnotation> annotations
) { }

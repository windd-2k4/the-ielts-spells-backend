package com.theieltsspells.writingevaluation.application.dto;

import jakarta.validation.constraints.Size;

/** Portable inline correction/comment metadata shared by management and student views. */
public record WritingTeacherAnnotation(
        String id,
        Integer start,
        Integer end,
        @Size(max = 2000) String selectedText,
        @Size(max = 2000) String replacement,
        @Size(max = 2000) String comment,
        String criterion,
        String kind,
        String severity
) { }

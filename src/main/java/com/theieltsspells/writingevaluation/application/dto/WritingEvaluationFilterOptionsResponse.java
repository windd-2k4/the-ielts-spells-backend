package com.theieltsspells.writingevaluation.application.dto;

import java.util.List;
import java.util.UUID;

public record WritingEvaluationFilterOptionsResponse(
        long totalCount,
        long selfPracticeCount,
        long courseCount,
        List<CourseOption> courses
) {
    public record CourseOption(
            UUID id,
            String code,
            String name,
            String status,
            long studentCount,
            long evaluationCount,
            long awaitingTeacherCount
    ) { }
}

package com.theieltsspells.shared.application;

import java.util.Map;
import java.util.UUID;

/** Published synchronously inside the submission transaction; contains only persisted source data. */
public record WritingSubmittedEvent(UUID responseId, String taskType, String essayText,
                                    Map<String, Object> taskContext) {
    public WritingSubmittedEvent {
        taskContext = Map.copyOf(taskContext);
    }
}

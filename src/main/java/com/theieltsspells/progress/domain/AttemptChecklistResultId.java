package com.theieltsspells.progress.domain;

import com.theieltsspells.shared.persistence.enums.*;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AttemptChecklistResultId implements Serializable {
    private UUID attemptId;
    private UUID checklistItemId;
}

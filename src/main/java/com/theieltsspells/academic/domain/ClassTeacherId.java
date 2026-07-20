package com.theieltsspells.academic.domain;

import com.theieltsspells.shared.persistence.enums.*;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ClassTeacherId implements Serializable {
    private UUID classId;
    private UUID teacherId;
}

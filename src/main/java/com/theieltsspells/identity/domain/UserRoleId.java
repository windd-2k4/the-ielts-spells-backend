package com.theieltsspells.identity.domain;

import com.theieltsspells.shared.persistence.enums.*;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserRoleId implements Serializable {
    private UUID userId;
    private AppRole role;
}

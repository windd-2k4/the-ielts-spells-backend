package com.theieltsspells.identity;

import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Public identity API for other modules that need to validate a student reference. */
@Service
@RequiredArgsConstructor
public class StudentAccessService {
    private final StudentProfileRepository students;

    public boolean exists(UUID studentId) {
        return students.existsById(studentId);
    }
}

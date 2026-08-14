package com.theieltsspells.identity.application;

import com.theieltsspells.identity.infrastructure.persistence.StudentDirectoryProjection;
import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentQueryServiceTest {
    @Test
    void normalizesQueryAndMapsDirectoryData() {
        var repository = mock(StudentProfileRepository.class);
        var pageable = PageRequest.of(0, 8);
        var id = UUID.randomUUID();
        var student = mock(StudentDirectoryProjection.class);
        when(student.getId()).thenReturn(id);
        when(student.getFullName()).thenReturn("Nguyễn Minh Anh");
        when(student.getEmail()).thenReturn("minhanh@example.com");
        when(student.getPhone()).thenReturn("0346 953 600");
        when(student.getStudentCode()).thenReturn("JUNE2-022");
        when(student.getActive()).thenReturn(true);
        when(student.getLifecycleStatus()).thenReturn("ACTIVE");
        when(student.getEnrollmentCount()).thenReturn(1L);
        when(repository.searchDirectory("0346 953", "0346953", null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(student), pageable, 1));

        var result = new StudentQueryService(repository).search(" 0346 953 ", null, null, pageable);

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.fullName()).isEqualTo("Nguyễn Minh Anh");
            assertThat(item.studentCode()).isEqualTo("JUNE2-022");
            assertThat(item.lifecycleStatus().name()).isEqualTo("ACTIVE");
        });
        verify(repository).searchDirectory("0346 953", "0346953", null, null, pageable);
    }
}

package com.theieltsspells.identity.application;

import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.domain.StudentProfile;
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
    void normalizesQueryAndMapsProfileData() {
        var repository = mock(StudentProfileRepository.class);
        var pageable = PageRequest.of(0, 8);
        var profile = new Profile();
        var id = UUID.randomUUID();
        profile.setId(id);
        profile.setFullName("Nguyễn Minh Anh");
        profile.setEmail("minhanh@example.com");
        profile.setPhone("0346 953 600");
        var student = new StudentProfile();
        student.setUserId(id);
        student.setStudentCode("JUNE2-022");
        student.setUserRef(profile);
        when(repository.searchActiveStudents("0346 953", "0346953", pageable))
                .thenReturn(new PageImpl<>(List.of(student), pageable, 1));

        var result = new StudentQueryService(repository).search(" 0346 953 ", pageable);

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.fullName()).isEqualTo("Nguyễn Minh Anh");
            assertThat(item.studentCode()).isEqualTo("JUNE2-022");
        });
        verify(repository).searchActiveStudents("0346 953", "0346953", pageable);
    }
}

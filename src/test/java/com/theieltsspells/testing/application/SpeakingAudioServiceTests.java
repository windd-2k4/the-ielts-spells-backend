package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeakingAudioServiceTests {
    @Mock JdbcTemplate jdbc;
    @Mock FileStorage storage;
    @Mock WebmAudioInspector inspector;
    private SpeakingAudioService service;

    @BeforeEach
    void setUp() {
        service = new SpeakingAudioService(jdbc, storage, inspector);
        ReflectionTestUtils.setField(service, "maxUploadBytes", 10_485_760L);
        ReflectionTestUtils.setField(service, "maxDurationSeconds", 180L);
        ReflectionTestUtils.setField(service, "storageBucket", "speaking-audio-private");
    }

    @Test
    @SuppressWarnings("unchecked")
    void storesOwnedInProgressAttemptUnderServerGeneratedKey() {
        UUID attemptId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "../../untrusted.exe", "audio/webm", new byte[]{1});
        when(jdbc.query(anyString(), any(RowMapper.class), eq(attemptId), eq(studentId)))
                .thenReturn(List.of("IN_PROGRESS"));
        when(inspector.inspect(eq(file), eq(10_485_760L), any()))
                .thenReturn(new WebmAudioInspector.Inspection("audio/webm", 1, 1_000));

        var result = service.upload(attemptId, "part-1-q1", file, studentId);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).storeInBucket(eq("speaking-audio-private"), key.capture(), eq(file));
        assertThat(key.getValue()).matches("speaking-attempts/" + attemptId + "/[0-9a-f-]+[.]webm");
        assertThat(key.getValue()).doesNotContain("untrusted");
        assertThat(result.storageKey()).isEqualTo(key.getValue());
        assertThat(result.durationMs()).isEqualTo(1_000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsAnAttemptNotOwnedByTheStudentBeforeInspectingOrStoring() {
        UUID attemptId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[]{1});
        when(jdbc.query(anyString(), any(RowMapper.class), eq(attemptId), eq(studentId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.upload(attemptId, "part-1-q1", file, studentId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(inspector, storage);
    }

    @Test
    void rejectsAStorageKeyFromAnotherAttemptOrStudent() {
        UUID attemptId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        String key = "speaking-attempts/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".webm";
        when(jdbc.query(anyString(), any(RowMapper.class), eq(attemptId), eq(studentId), eq(key), eq("part-1-q1")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.assertOwnedStorageKey(attemptId, studentId, key, "part-1-q1"))
                .isInstanceOf(BusinessRuleException.class);
    }
}

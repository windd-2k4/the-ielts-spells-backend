package com.theieltsspells.progress.presentation;

import com.theieltsspells.progress.application.StudentVocabularyApplicationService;
import com.theieltsspells.progress.application.dto.CreateVocabularyTopicRequest;
import com.theieltsspells.progress.application.dto.SaveVocabularyCardRequest;
import com.theieltsspells.progress.application.dto.UpdateVocabularyMasteryRequest;
import com.theieltsspells.progress.application.dto.VocabularyNotebookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/vocabulary")
@RequiredArgsConstructor
@Tag(name = "Student - Vocabulary flashcards")
@SecurityRequirement(name = "bearerAuth")
public class StudentVocabularyController {
    private final StudentVocabularyApplicationService service;

    @GetMapping
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy sổ từ vựng và tiến độ flashcard của học viên")
    public VocabularyNotebookResponse notebook(@AuthenticationPrincipal Jwt jwt) {
        return service.notebook(studentId(jwt));
    }

    @PostMapping("/topics")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public VocabularyNotebookResponse.Topic createTopic(
            @Valid @RequestBody CreateVocabularyTopicRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.createTopic(studentId(jwt), request);
    }

    @PostMapping("/cards")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    @Operation(summary = "Thêm từ đã chọn vào flashcard")
    public VocabularyNotebookResponse.Card addCard(
            @Valid @RequestBody SaveVocabularyCardRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.addCard(studentId(jwt), request);
    }

    @PutMapping("/cards/{cardId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public VocabularyNotebookResponse.Card updateCard(
            @PathVariable UUID cardId,
            @Valid @RequestBody SaveVocabularyCardRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updateCard(studentId(jwt), cardId, request);
    }

    @PatchMapping("/cards/{cardId}/mastery")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public VocabularyNotebookResponse.Card updateMastery(
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateVocabularyMasteryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updateMastery(studentId(jwt), cardId, request);
    }

    @DeleteMapping("/cards/{cardId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public ResponseEntity<Void> deleteCard(@PathVariable UUID cardId, @AuthenticationPrincipal Jwt jwt) {
        service.deleteCard(studentId(jwt), cardId);
        return ResponseEntity.noContent().build();
    }

    private UUID studentId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}


package com.theieltsspells.progress.presentation;

import com.theieltsspells.progress.application.CuratedVocabularyApplicationService;
import com.theieltsspells.progress.application.dto.CuratedVocabularyDeckDetailResponse;
import com.theieltsspells.progress.application.dto.CuratedVocabularyDeckSummaryResponse;
import com.theieltsspells.progress.application.dto.CuratedVocabularyItemResponse;
import com.theieltsspells.progress.application.dto.CuratedVocabularySessionResponse;
import com.theieltsspells.progress.application.dto.UpdateCuratedVocabularyProgressRequest;
import com.theieltsspells.progress.application.dto.VocabularyNotebookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/curated-vocabulary")
@RequiredArgsConstructor
@Tag(name = "Student - Curated Vocabulary Hub")
@SecurityRequirement(name = "bearerAuth")
public class StudentCuratedVocabularyController {

    private final CuratedVocabularyApplicationService service;

    @GetMapping("/decks")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy danh sách các bộ từ vựng tuyển chọn kèm tiến độ học viên")
    public List<CuratedVocabularyDeckSummaryResponse> getDecks(@AuthenticationPrincipal Jwt jwt) {
        return service.getDecks(studentId(jwt));
    }

    @GetMapping("/decks/{deckId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy chi tiết danh sách từ trong một bộ từ vựng")
    public CuratedVocabularyDeckDetailResponse getDeckDetail(
            @PathVariable UUID deckId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.getDeckDetail(studentId(jwt), deckId);
    }

    @GetMapping("/decks/{deckId}/session")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Bắt đầu lượt học 10 từ của bộ từ vựng")
    public CuratedVocabularySessionResponse startStudySession(
            @PathVariable UUID deckId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.startStudySession(studentId(jwt), deckId);
    }

    @PatchMapping("/decks/{deckId}/items/{itemId}/progress")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    @Operation(summary = "Cập nhật tiến độ thuộc từ vựng trong session")
    public CuratedVocabularyItemResponse updateProgress(
            @PathVariable UUID deckId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCuratedVocabularyProgressRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updateProgress(studentId(jwt), deckId, itemId, request);
    }

    @PostMapping("/items/{itemId}/save-to-notebook")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    @Operation(summary = "Lưu từ vựng tuyển chọn vào sổ từ cá nhân của học viên")
    public VocabularyNotebookResponse.Card saveToNotebook(
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.saveToPersonalNotebook(studentId(jwt), itemId);
    }

    private UUID studentId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

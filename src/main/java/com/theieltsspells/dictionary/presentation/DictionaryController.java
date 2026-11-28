package com.theieltsspells.dictionary.presentation;

import com.theieltsspells.dictionary.application.DictionaryApplicationService;
import com.theieltsspells.dictionary.application.dto.DictionaryLookupResponse;
import com.theieltsspells.dictionary.application.dto.DictionarySuggestItemResponse;
import com.theieltsspells.dictionary.application.dto.LookupHistoryItemResponse;
import com.theieltsspells.dictionary.application.dto.SaveDictionaryWordRequest;
import com.theieltsspells.dictionary.application.dto.SavedWordResponse;
import com.theieltsspells.dictionary.application.dto.TranslateTextRequest;
import com.theieltsspells.dictionary.application.dto.TranslateTextResponse;
import com.theieltsspells.dictionary.domain.DictionaryDirection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dictionary")
@RequiredArgsConstructor
@Tag(name = "Dictionary & Translation")
@SecurityRequirement(name = "bearerAuth")
public class DictionaryController {

    private final DictionaryApplicationService service;

    @GetMapping("/suggest")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Gợi ý từ vựng 2 chiều Anh-Việt / Việt-Anh khi đang gõ")
    public List<DictionarySuggestItemResponse> suggest(
            @RequestParam("q") String query,
            @RequestParam(name = "direction", defaultValue = "AUTO") DictionaryDirection direction,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return service.suggest(query, direction, limit);
    }

    @GetMapping("/lookup")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Tra chi tiết từ vựng 2 chiều kèm IPA, nghĩa, ví dụ và trạng thái sổ từ")
    public DictionaryLookupResponse lookup(
            @RequestParam("word") String word,
            @RequestParam(name = "direction", defaultValue = "AUTO") DictionaryDirection direction,
            @AuthenticationPrincipal Jwt jwt) {
        return service.lookup(userId(jwt), word, direction);
    }

    @PostMapping("/translate")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Dịch câu/đoạn văn 2 chiều Anh-Việt qua LibreTranslate hoặc AI IELTS")
    public TranslateTextResponse translate(
            @Valid @RequestBody TranslateTextRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.translate(userId(jwt), request);
    }

    @PostMapping("/save-to-notebook")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lưu nhanh từ vừa tra vào Sổ từ vựng cá nhân")
    public SavedWordResponse saveToNotebook(
            @Valid @RequestBody SaveDictionaryWordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.saveToNotebook(userId(jwt), request);
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử tra từ & dịch nghĩa gần đây")
    public List<LookupHistoryItemResponse> history(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        return service.history(userId(jwt), limit);
    }

    @GetMapping("/offline-bundle/meta")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thông tin phiên bản gói từ điển SQLite Offline")
    public Map<String, Object> offlineBundleMeta() {
        return service.getOfflineBundleMeta();
    }

    @GetMapping("/offline-bundle")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tải gói SQLite từ điển offline cho thiết bị di động")
    public ResponseEntity<Resource> downloadOfflineBundle() {
        Resource resource = service.getOfflineSqliteBundle();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ielts_dict.sqlite\"")
                .body(resource);
    }

    private static UUID userId(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null ? UUID.fromString(jwt.getSubject()) : null;
    }
}

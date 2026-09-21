package com.theieltsspells.testing.presentation.admin;

import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.CrawlHubApplicationService;
import com.theieltsspells.testing.application.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/crawl-hub")
@RequiredArgsConstructor
@Tag(name = "Admin - Crawl Hub")
@SecurityRequirement(name = "bearerAuth")
public class CrawlHubAdminController {

    private final CrawlHubApplicationService crawlHubService;

    // -------------------------------------------------------------------------
    // CRAWL SOURCES
    // -------------------------------------------------------------------------

    @GetMapping("/sources")
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public List<CrawlSourceDto> listSources() {
        return crawlHubService.listSources();
    }

    @GetMapping("/sources/{id}")
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public CrawlSourceDto getSource(@PathVariable UUID id) {
        return crawlHubService.getSource(id);
    }

    @PostMapping("/sources")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public ResponseEntity<CrawlSourceDto> createSource(@Valid @RequestBody SaveCrawlSourceRequest request) {
        var result = crawlHubService.createSource(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/crawl-hub/sources/" + result.id())).body(result);
    }

    @PutMapping("/sources/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public CrawlSourceDto updateSource(@PathVariable UUID id, @Valid @RequestBody SaveCrawlSourceRequest request) {
        return crawlHubService.updateSource(id, request);
    }

    @DeleteMapping("/sources/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public ResponseEntity<Void> deleteSource(@PathVariable UUID id) {
        crawlHubService.deleteSource(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sources/{id}/trigger")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public CrawlSourceDto triggerCrawl(@PathVariable UUID id) {
        return crawlHubService.triggerCrawl(id);
    }

    // -------------------------------------------------------------------------
    // RAW CRAWLED TESTS
    // -------------------------------------------------------------------------

    @GetMapping("/raw-tests")
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public PageResponse<RawCrawledTestDto> listRawTests(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) SkillType skill,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return crawlHubService.listRawTests(query, skill, status, sourceId, page, size);
    }

    @GetMapping("/raw-tests/{id}")
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public RawCrawledTestDto getRawTest(@PathVariable UUID id) {
        return crawlHubService.getRawTest(id);
    }

    @PostMapping("/raw-tests/{id}/parse")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public RawCrawledTestDto parseRawTest(
            @PathVariable UUID id,
            @RequestParam(required = false) AiParserProvider provider,
            @RequestParam(required = false) String teacherInstructions
    ) {
        return crawlHubService.parseRawTest(id, provider, teacherInstructions);
    }

    @PostMapping("/raw-tests/{id}/import")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public RawCrawledTestDto importRawTestToTestBank(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return crawlHubService.importRawTestToTestBank(id, UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/raw-tests/{id}/status")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public RawCrawledTestDto updateRawTestStatus(
            @PathVariable UUID id,
            @RequestParam String status
    ) {
        return crawlHubService.updateRawTestStatus(id, status);
    }
}

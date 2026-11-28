package com.theieltsspells.cms.presentation;

import com.theieltsspells.cms.application.CmsContentApplicationService;
import com.theieltsspells.learninglibrary.application.LearningLibraryApplicationService;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
class PublicContentController {

    private final CmsContentApplicationService cms;
    private final LearningLibraryApplicationService library;

    @GetMapping("/home")
    Map<String, Object> home() {
        var response = new java.util.LinkedHashMap<String, Object>();
        response.putAll(Map.of(
                "brand", "The IELTS Spells",
                "tagline", "Cast the Spells, Claim the Band",
                "featuredCourses", List.of(
                        Map.of("name", "IELTS Kickstart", "entryLevel", "0–3.0"),
                        Map.of("name", "IELTS Stepping Stone", "entryLevel", "4.0+")
                )
        ));
        cms.publishedPage("trang-chu").ifPresent(page -> response.put("landingPage", page));
        return response;
    }

    @GetMapping("/landing-media/{fileId}")
    ResponseEntity<InputStreamResource> landingMedia(@PathVariable UUID fileId) {
        String reference = "/admin/library/files/" + fileId + "/content";
        boolean published = cms.publishedPage("trang-chu")
                .map(page -> containsReference(page.content(), reference))
                .orElse(false);
        if (!published) throw new ResourceNotFoundException("Ảnh không thuộc nội dung landing page đã xuất bản");
        var content = library.openResourceFile(fileId);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(content.mimeType()); }
        catch (IllegalArgumentException ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content.inputStream()));
    }

    private boolean containsReference(Object node, String reference) {
        if (node instanceof String text) return text.equals(reference);
        if (node instanceof Map<?, ?> map) return map.values().stream().anyMatch(value -> containsReference(value, reference));
        if (node instanceof Iterable<?> values) {
            for (Object value : values) if (containsReference(value, reference)) return true;
        }
        return false;
    }
}

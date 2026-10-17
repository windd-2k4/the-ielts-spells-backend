package com.theieltsspells.cms.presentation;

import com.theieltsspells.cms.application.CmsContentApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
class PublicContentController {

    private final CmsContentApplicationService cms;

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
}

package com.theieltsspells.cms.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
class PublicContentController {

    @GetMapping("/home")
    Map<String, Object> home() {
        return Map.of(
                "brand", "The IELTS Spells",
                "tagline", "Cast the Spells, Claim the Band",
                "featuredCourses", List.of(
                        Map.of("name", "IELTS Kickstart", "entryLevel", "0–3.0"),
                        Map.of("name", "IELTS Stepping Stone", "entryLevel", "4.0+")
                )
        );
    }
}

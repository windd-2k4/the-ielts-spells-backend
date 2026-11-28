package com.theieltsspells.aimentor.presentation;

import com.theieltsspells.aimentor.application.AiMentorApplicationService;
import com.theieltsspells.aimentor.application.dto.AiMentorChatRequest;
import com.theieltsspells.aimentor.application.dto.AiMentorChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-mentor")
@RequiredArgsConstructor
@Tag(name = "AI Mentor Companion")
public class AiMentorController {

    private final AiMentorApplicationService service;

    @PostMapping("/chat")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Trò chuyện song ngữ cá nhân hóa cùng Spells Mentor AI")
    public AiMentorChatResponse chat(@Valid @RequestBody AiMentorChatRequest request) {
        return service.chat(request);
    }
}

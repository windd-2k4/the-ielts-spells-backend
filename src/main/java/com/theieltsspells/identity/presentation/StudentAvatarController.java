package com.theieltsspells.identity.presentation;

import com.theieltsspells.identity.application.ProfileAvatarApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StudentAvatarController {
    private final ProfileAvatarApplicationService avatarService;

    @GetMapping("/api/v1/public/student-avatars/{filename}")
    public ResponseEntity<InputStreamResource> avatar(@PathVariable String filename) {
        var content = avatarService.openStudent(filename);
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .body(new InputStreamResource(content.inputStream()));
    }
}

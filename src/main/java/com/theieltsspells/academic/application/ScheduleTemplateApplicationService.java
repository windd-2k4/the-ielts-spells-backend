package com.theieltsspells.academic.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.ScheduleTemplate;
import com.theieltsspells.academic.infrastructure.persistence.ScheduleTemplateRepository;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleTemplateApplicationService {
    private final ScheduleTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public List<ScheduleTemplateResponse> list(String skillPair) {
        return repository.findBySkillPairAndActiveTrueOrderByCreatedAtAsc(skillPair).stream().map(this::response).toList();
    }

    @Transactional
    public ScheduleTemplateResponse create(UpsertScheduleTemplateRequest request) {
        var value = new ScheduleTemplate();
        apply(value, request);
        return response(repository.save(value));
    }

    @Transactional
    public ScheduleTemplateResponse update(UUID id, UpsertScheduleTemplateRequest request) {
        var value = find(id);
        apply(value, request);
        return response(repository.save(value));
    }

    @Transactional
    public void delete(UUID id) {
        var value = find(id);
        value.setActive(false);
        repository.save(value);
    }

    private void apply(ScheduleTemplate value, UpsertScheduleTemplateRequest request) {
        value.setName(request.name().trim());
        value.setSkillPair(request.skillPair().name());
        value.setDescription(request.description());
        try { value.setDefinitionJson(objectMapper.writeValueAsString(request.entries())); }
        catch (Exception exception) { throw new IllegalArgumentException("Không thể lưu định nghĩa lộ trình", exception); }
    }

    private ScheduleTemplate find(UUID id) {
        return repository.findById(id).filter(ScheduleTemplate::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lộ trình mẫu"));
    }

    private ScheduleTemplateResponse response(ScheduleTemplate value) {
        try {
            var entries = objectMapper.readValue(value.getDefinitionJson(), new TypeReference<List<ScheduleTemplateEntryRequest>>() {});
            return new ScheduleTemplateResponse(value.getId(), value.getName(),
                    com.theieltsspells.shared.persistence.enums.SkillPair.valueOf(value.getSkillPair()),
                    value.getDescription(), entries);
        } catch (Exception exception) { throw new IllegalStateException("Lộ trình mẫu không hợp lệ", exception); }
    }
}

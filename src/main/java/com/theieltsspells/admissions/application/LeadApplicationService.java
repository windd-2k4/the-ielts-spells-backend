package com.theieltsspells.admissions.application;

import com.theieltsspells.admissions.application.dto.ConvertLeadRequest;
import com.theieltsspells.admissions.application.dto.LeadResponse;
import com.theieltsspells.admissions.application.dto.UpdateLeadStatusRequest;
import com.theieltsspells.admissions.domain.Lead;
import com.theieltsspells.admissions.infrastructure.persistence.LeadRepository;
import com.theieltsspells.identity.StudentAccessService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.LeadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeadApplicationService {
    private static final Map<LeadStatus, Set<LeadStatus>> TRANSITIONS = Map.of(
            LeadStatus.NEW, EnumSet.of(LeadStatus.CONTACTED, LeadStatus.QUALIFIED, LeadStatus.LOST),
            LeadStatus.CONTACTED, EnumSet.of(LeadStatus.QUALIFIED, LeadStatus.LOST),
            LeadStatus.QUALIFIED, EnumSet.of(LeadStatus.CONVERTED, LeadStatus.LOST),
            LeadStatus.CONVERTED, EnumSet.noneOf(LeadStatus.class),
            LeadStatus.LOST, EnumSet.noneOf(LeadStatus.class));

    private final LeadRepository leads;
    private final StudentAccessService students;

    public Page<LeadResponse> list(String rawQuery, LeadStatus status, Pageable pageable) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String phoneQuery = query.replaceAll("[^0-9+]", "");
        return leads.search(query, phoneQuery, status, pageable).map(this::toResponse);
    }

    @Transactional
    public LeadResponse updateStatus(UUID id, UpdateLeadStatusRequest request) {
        Lead lead = find(id);
        if (lead.getStatus() == request.status()) return toResponse(lead);
        if (!TRANSITIONS.getOrDefault(lead.getStatus(), Set.of()).contains(request.status())) {
            throw new BusinessRuleException("Không thể chuyển trạng thái khách tư vấn từ " + lead.getStatus() + " sang " + request.status());
        }
        lead.setStatus(request.status());
        return toResponse(leads.save(lead));
    }

    @Transactional
    public LeadResponse convert(UUID id, ConvertLeadRequest request) {
        Lead lead = find(id);
        if (lead.getStatus() != LeadStatus.QUALIFIED) {
            throw new BusinessRuleException("Chỉ khách tư vấn đã đủ điều kiện mới có thể chuyển thành học viên");
        }
        if (!students.exists(request.studentId())) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ học viên để liên kết");
        }
        lead.setConvertedStudentId(request.studentId());
        lead.setStatus(LeadStatus.CONVERTED);
        return toResponse(leads.save(lead));
    }

    private Lead find(UUID id) {
        return leads.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách tư vấn"));
    }

    private LeadResponse toResponse(Lead lead) {
        return new LeadResponse(lead.getId(), lead.getFullName(), lead.getPhone(), lead.getEmail(), lead.getCurrentBand(),
                lead.getTargetBand(), lead.getInterestedCourseId(), lead.getPreferredContactAt(), lead.getSource(),
                lead.getStatus(), lead.getConvertedStudentId(), lead.getCreatedAt(), lead.getUpdatedAt());
    }
}

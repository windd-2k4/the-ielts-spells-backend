package com.theieltsspells.admissions.infrastructure.persistence;

import com.theieltsspells.admissions.domain.Lead;
import com.theieltsspells.shared.persistence.enums.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    @Query(value = """
            select lead from Lead lead
            where (:status is null or lead.status = :status)
              and (:query = '' or lower(lead.fullName) like lower(concat('%', :query, '%'))
                   or lower(coalesce(lead.email, '')) like lower(concat('%', :query, '%'))
                   or replace(replace(replace(coalesce(lead.phone, ''), ' ', ''), '.', ''), '-', '') like concat('%', :phoneQuery, '%'))
            """, countQuery = """
            select count(lead) from Lead lead
            where (:status is null or lead.status = :status)
              and (:query = '' or lower(lead.fullName) like lower(concat('%', :query, '%'))
                   or lower(coalesce(lead.email, '')) like lower(concat('%', :query, '%'))
                   or replace(replace(replace(coalesce(lead.phone, ''), ' ', ''), '.', ''), '-', '') like concat('%', :phoneQuery, '%'))
            """)
    Page<Lead> search(@Param("query") String query,
                      @Param("phoneQuery") String phoneQuery,
                      @Param("status") LeadStatus status,
                      Pageable pageable);
}

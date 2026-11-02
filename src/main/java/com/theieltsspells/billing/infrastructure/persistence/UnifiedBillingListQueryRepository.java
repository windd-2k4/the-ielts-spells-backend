package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.InvoiceStatus;
import com.theieltsspells.billing.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UnifiedBillingListQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public BillingRowPage findPage(
            String query,
            OrderStatus orderStatus,
            Set<InvoiceStatus> invoiceStatuses,
            UUID courseId,
            OffsetDateTime fromTime,
            OffsetDateTime toTime,
            boolean unissuedOnly,
            int pageSize,
            long offset
    ) {
        StringBuilder orderFilters = new StringBuilder(" WHERE 1 = 1");
        StringBuilder standaloneFilters = new StringBuilder(" WHERE ei.order_id IS NULL");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", pageSize)
                .addValue("offset", offset);

        if (orderStatus != null) {
            orderFilters.append(" AND o.status::text = :orderStatus");
            params.addValue("orderStatus", orderStatus.name());
            if (orderStatus != OrderStatus.PAID) {
                standaloneFilters.append(" AND 1 = 0");
            }
        }
        if (courseId != null) {
            orderFilters.append(" AND o.course_id = :courseId");
            standaloneFilters.append(" AND 1 = 0");
            params.addValue("courseId", courseId);
        }
        if (fromTime != null) {
            orderFilters.append(" AND o.created_at >= :fromTime");
            standaloneFilters.append(" AND COALESCE(pt.created_at, ei.created_at) >= :fromTime");
            params.addValue("fromTime", fromTime);
        }
        if (toTime != null) {
            orderFilters.append(" AND o.created_at < :toTime");
            standaloneFilters.append(" AND COALESCE(pt.created_at, ei.created_at) < :toTime");
            params.addValue("toTime", toTime);
        }
        if (query != null && !query.isBlank()) {
            orderFilters.append("""
                     AND LOWER(CONCAT_WS(' ',
                         o.order_code, o.customer_name, o.customer_email, o.customer_phone,
                         li.reference_code, li.invoice_number, li.cqt_code,
                         li.buyer_name, li.buyer_email, li.buyer_tax_code, li.buyer_legal_name
                     )) LIKE :query
                    """);
            standaloneFilters.append("""
                     AND LOWER(CONCAT_WS(' ',
                         'qr-tinh', 'static-' || COALESCE(pt.sepay_transaction_id, ''),
                         pt.order_code, pt.transfer_content, pt.payer_name,
                         ei.reference_code, ei.invoice_number, ei.cqt_code, ei.lookup_code,
                         ei.buyer_name, ei.buyer_email, ei.buyer_tax_code, ei.buyer_legal_name
                     )) LIKE :query
                    """);
            params.addValue("query", "%" + query.trim().toLowerCase() + "%");
        }
        if (invoiceStatuses != null && !invoiceStatuses.isEmpty()) {
            List<String> statusNames = invoiceStatuses.stream().map(Enum::name).toList();
            orderFilters.append(" AND li.status::text IN (:invoiceStatuses)");
            standaloneFilters.append(" AND ei.status::text IN (:invoiceStatuses)");
            params.addValue("invoiceStatuses", statusNames);
        }
        if (unissuedOnly) {
            orderFilters.append(" AND o.status::text = 'PAID' AND li.id IS NULL");
            standaloneFilters.append(" AND 1 = 0");
        }

        String cte = """
                WITH latest_invoice AS (
                    SELECT DISTINCT ON (order_id)
                        id, order_id, status, reference_code, invoice_number, cqt_code,
                        buyer_name, buyer_email, buyer_tax_code, buyer_legal_name
                    FROM electronic_invoices
                    WHERE order_id IS NOT NULL
                    ORDER BY order_id, created_at DESC
                ), unified AS (
                    SELECT 'ORDER' AS row_type, o.id AS row_id, li.id AS invoice_id, o.created_at AS sort_at
                    FROM orders o
                    LEFT JOIN latest_invoice li ON li.order_id = o.id
                """ + orderFilters + """
                    UNION ALL
                    SELECT 'INVOICE' AS row_type, ei.id AS row_id, ei.id AS invoice_id,
                           COALESCE(pt.created_at, ei.created_at) AS sort_at
                    FROM electronic_invoices ei
                    LEFT JOIN payment_transactions pt ON pt.id = ei.payment_transaction_id
                """ + standaloneFilters + "\n)\n";

        Long total = jdbc.queryForObject(cte + "SELECT COUNT(*) FROM unified", params, Long.class);
        List<BillingRowRef> rows = jdbc.query(
                cte + """
                        SELECT row_type, row_id, invoice_id
                        FROM unified
                        ORDER BY sort_at DESC NULLS LAST, row_id
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                (rs, rowNum) -> new BillingRowRef(
                        rs.getString("row_type"),
                        rs.getObject("row_id", UUID.class),
                        rs.getObject("invoice_id", UUID.class)
                )
        );
        return new BillingRowPage(rows, total != null ? total : 0L);
    }

    public record BillingRowRef(String rowType, UUID rowId, UUID invoiceId) {
        public boolean isOrder() {
            return "ORDER".equals(rowType);
        }
    }

    public record BillingRowPage(List<BillingRowRef> rows, long totalElements) {
    }
}

package com.theieltsspells.billing.application;

import com.theieltsspells.academic.application.BillingCourseQueryService;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.InvoiceStatus;
import com.theieltsspells.billing.domain.Order;
import com.theieltsspells.billing.application.dto.OrderAdminDto;
import com.theieltsspells.billing.infrastructure.persistence.*;
import com.theieltsspells.identity.application.StudentAccountApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderApplicationServiceTests {

    @Test
    void delegatesDraftFilteringAndPaginationToPostgres() {
        Fixture fixture = new Fixture();
        Order order = order("KH-DRAFT");
        ElectronicInvoice invoice = invoice(order.getId(), InvoiceStatus.DRAFT);
        fixture.page(order, invoice);

        var result = fixture.service.searchOrders(
                null, null, InvoiceStatus.DRAFT, null, null, null,
                true, false, PageRequest.of(0, 25));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<InvoiceStatus>> statuses = ArgumentCaptor.forClass(Set.class);
        verify(fixture.query).findPage(isNull(), isNull(), statuses.capture(), isNull(), isNull(), isNull(),
                eq(false), eq(25), eq(0L));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(InvoiceStatus.DRAFT, InvoiceStatus.ISSUING);
        assertThat(result.getTotalElements()).isOne();
        assertThat(result.getContent()).extracting(OrderAdminDto::orderCode).containsExactly("KH-DRAFT");
    }

    @Test
    void sendsUnissuedFilterToPostgres() {
        Fixture fixture = new Fixture();
        when(fixture.query.findPage(any(), any(), anySet(), any(), any(), any(), eq(true), eq(25), eq(0L)))
                .thenReturn(new UnifiedBillingListQueryRepository.BillingRowPage(List.of(), 0));

        var result = fixture.service.searchOrders(
                null, null, null, null, null, null,
                false, true, PageRequest.of(0, 25));

        assertThat(result).isEmpty();
        verify(fixture.query).findPage(isNull(), isNull(), eq(Set.of()), isNull(), isNull(), isNull(),
                eq(true), eq(25), eq(0L));
    }

    private static final class Fixture {
        final OrderRepository orders = mock(OrderRepository.class);
        final ElectronicInvoiceRepository invoices = mock(ElectronicInvoiceRepository.class);
        final BillingCourseQueryService courses = mock(BillingCourseQueryService.class);
        final AccountActivationTokenRepository tokens = mock(AccountActivationTokenRepository.class);
        final UnifiedBillingListQueryRepository query = mock(UnifiedBillingListQueryRepository.class);
        final OrderApplicationService service = new OrderApplicationService(
                orders, courses, mock(StudentAccountApplicationService.class),
                mock(BillingSettingRepository.class), invoices, tokens, query);

        Fixture() {
            when(courses.findByIds(anySet())).thenReturn(Map.of());
            when(tokens.findByOrderIdIn(anyCollection())).thenReturn(List.of());
        }

        void page(Order order, ElectronicInvoice invoice) {
            when(query.findPage(any(), any(), anySet(), any(), any(), any(), anyBoolean(), anyInt(), anyLong()))
                    .thenReturn(new UnifiedBillingListQueryRepository.BillingRowPage(
                            List.of(new UnifiedBillingListQueryRepository.BillingRowRef("ORDER", order.getId(), invoice.getId())), 1));
            when(orders.findAllById(any())).thenReturn(List.of(order));
            when(invoices.findAllWithPaymentTransactionByIdIn(anyList())).thenReturn(List.of(invoice));
        }
    }

    private static Order order(String code) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode(code);
        order.setCourseId(UUID.randomUUID());
        order.setCustomerName("Học viên");
        order.setCustomerEmail("student@example.com");
        order.setAmount(new BigDecimal("2500000"));
        order.setCreatedAt(OffsetDateTime.now());
        return order;
    }

    private static ElectronicInvoice invoice(UUID orderId, InvoiceStatus status) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(orderId);
        invoice.setStatus(status);
        return invoice;
    }
}

package com.theieltsspells.billing.application;

import com.theieltsspells.academic.application.EnrollmentApplicationService;
import com.theieltsspells.billing.application.dto.ActivateAccountRequest;
import com.theieltsspells.billing.domain.AccountActivationToken;
import com.theieltsspells.billing.domain.Order;
import com.theieltsspells.billing.infrastructure.persistence.AccountActivationTokenRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.identity.application.StudentAccount;
import com.theieltsspells.identity.application.StudentAccountApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountActivationServiceTests {

    @Mock AccountActivationTokenRepository tokens;
    @Mock OrderRepository orders;
    @Mock com.theieltsspells.academic.application.BillingCourseQueryService courses;
    @Mock StudentAccountApplicationService studentAccounts;
    @Mock EnrollmentApplicationService enrollments;

    private AccountActivationService service;

    @BeforeEach
    void setUp() {
        service = new AccountActivationService(tokens, orders, courses, studentAccounts, enrollments);
    }

    @Test
    void paidGuestIsReservedAndEnrolledWhileWaitingForActivation() {
        UUID userId = UUID.randomUUID();
        Order order = paidGuestOrder();
        when(studentAccounts.reserveOrCreate("student@example.com", "Nguyen Van A"))
                .thenReturn(new StudentAccount(userId, "Nguyen Van A"));
        when(tokens.save(any(AccountActivationToken.class))).thenAnswer(call -> call.getArgument(0));

        String rawToken = service.preparePendingActivation(order);

        assertThat(rawToken).hasSize(64);
        assertThat(order.getUserId()).isEqualTo(userId);
        verify(enrollments).enrollSafely(order.getCourseId(), userId,
                "Chờ học viên kích hoạt tài khoản sau thanh toán đơn KH2610041234");
        verify(tokens).save(any(AccountActivationToken.class));
    }

    @Test
    void activationSetsSupabasePasswordAndGrantsActiveCourseAccess() {
        UUID userId = UUID.randomUUID();
        Order order = paidGuestOrder();
        order.setUserId(userId);
        AccountActivationToken token = new AccountActivationToken();
        token.setOrderId(order.getId());
        token.setUserId(userId);
        token.setCustomerEmail("student@example.com");
        token.setCustomerName("Nguyen Van A");
        token.setTokenHash("valid-token");
        token.setExpiresAt(OffsetDateTime.now().plusDays(1));
        token.setCreatedAt(OffsetDateTime.now());

        when(tokens.findByTokenHash("valid-token")).thenReturn(Optional.of(token));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(studentAccounts.activateOrCreate("student@example.com", "Nguyen Van A", "StrongPass123"))
                .thenReturn(new StudentAccount(userId, "Nguyen Van A"));

        var response = service.activateAccount(new ActivateAccountRequest(
                "valid-token", "StrongPass123", "StrongPass123"));

        assertThat(response.success()).isTrue();
        assertThat(token.isUsed()).isTrue();
        verify(enrollments).grantPaidAccess(order.getCourseId(), userId,
                "Kích hoạt tự động sau thanh toán đơn hàng KH2610041234");
    }

    private Order paidGuestOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("KH2610041234");
        order.setCourseId(UUID.randomUUID());
        order.setCustomerEmail("student@example.com");
        order.setCustomerName("Nguyen Van A");
        return order;
    }
}

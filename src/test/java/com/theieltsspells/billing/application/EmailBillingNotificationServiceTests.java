package com.theieltsspells.billing.application;

import com.theieltsspells.academic.application.BillingCourseQueryService;
import com.theieltsspells.billing.domain.AccountActivationToken;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.Order;
import com.theieltsspells.billing.infrastructure.persistence.AccountActivationTokenRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailBillingNotificationServiceTests {

    @Test
    void invoiceEmailIncludesActivationLinkForWaitingStudent() throws Exception {
        BillingCourseQueryService courses = mock(BillingCourseQueryService.class);
        AccountActivationTokenRepository tokens = mock(AccountActivationTokenRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailBillingNotificationService service = new EmailBillingNotificationService(courses, tokens);
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "billing@example.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://theieltsspells.io.vn");

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("KH2610051234");
        order.setCourseId(UUID.randomUUID());
        order.setCustomerName("Nguyen Van A");
        order.setCustomerEmail("student@example.com");

        AccountActivationToken token = new AccountActivationToken();
        token.setTokenHash("activation-token");
        token.setExpiresAt(OffsetDateTime.now().plusDays(1));
        when(tokens.findByOrderId(order.getId())).thenReturn(Optional.of(token));

        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setInvoiceNumber("1");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendInvoiceIssuedEmailNow(order, invoice);

        String html = message.getContent().toString();
        assertThat(html)
                .contains("KÍCH HOẠT TÀI KHOẢN")
                .contains("https://theieltsspells.io.vn/student/activate?token=activation-token");
    }
}

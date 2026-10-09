package com.theieltsspells.identity.infrastructure;

import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffInvitationEmailServiceTests {
    @Test
    void sendsBrandedInvitationWithActivationLink() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StaffInvitationEmailService service = new StaffInvitationEmailService();
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "hello@example.com");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.send("teacher@example.com", "Nguyễn Thị Thu", AppRole.ADMIN,
                "https://project.supabase.co/auth/v1/verify?token=test");

        String html = message.getContent().toString();
        assertThat(message.getSubject()).isEqualTo("Bạn được mời gia nhập The IELTS Spells");
        assertThat(html)
                .contains("The IELTS Spells")
                .contains("Nguyễn Thị Thu")
                .contains("Chủ doanh nghiệp / Quản trị vi&ecirc;n")
                .contains("https://project.supabase.co/auth/v1/verify?token=test")
                .contains("#C85F78");
    }
}

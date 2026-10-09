package com.theieltsspells.identity.infrastructure;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class StaffInvitationEmailService {
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.access-approval.mail-from:}")
    private String fromEmail;

    public void send(String email, String fullName, AppRole role, String actionLink) {
        if (mailSender == null || fromEmail.isBlank())
            throw new BusinessRuleException("SMTP chưa được cấu hình để gửi lời mời nhân sự");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, "The IELTS Spells");
            helper.setTo(email);
            helper.setSubject("Bạn được mời gia nhập The IELTS Spells");
            helper.setText(template(fullName, roleLabel(role), actionLink), true);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new BusinessRuleException("Không thể gửi email lời mời. Vui lòng kiểm tra cấu hình SMTP");
        }
    }

    private String template(String fullName, String role, String actionLink) {
        String name = HtmlUtils.htmlEscape(fullName);
        String safeRole = HtmlUtils.htmlEscape(role);
        String link = HtmlUtils.htmlEscape(actionLink);
        return """
                <!doctype html>
                <html lang="vi"><body style="margin:0;background:#F7F5F4;font-family:Arial,'Segoe UI',sans-serif;color:#292528">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F7F5F4;padding:32px 12px">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#FFFFFF;border:1px solid #DED7DA;border-radius:22px;overflow:hidden">
                        <tr><td style="height:8px;background:#C85F78"></td></tr>
                        <tr><td style="padding:34px 38px 12px">
                          <div style="font-size:22px;font-weight:700;letter-spacing:-.4px;color:#AD4C64">The IELTS Spells</div>
                          <div style="margin-top:5px;font-size:13px;color:#6F676C">Learn with clarity. Grow with confidence.</div>
                        </td></tr>
                        <tr><td style="padding:18px 38px 38px">
                          <div style="display:inline-block;padding:7px 11px;border-radius:999px;background:#F7E5EA;color:#AD4C64;font-size:12px;font-weight:700">LỜI MỜI NHÂN SỰ</div>
                          <h1 style="margin:20px 0 14px;font-size:28px;line-height:1.25;letter-spacing:-.6px">Chào %s,</h1>
                          <p style="margin:0 0 18px;font-size:16px;line-height:1.65;color:#4F494D">Bạn được mời tham gia không gian quản trị của The IELTS Spells với vai trò <strong style="color:#292528">%s</strong>.</p>
                          <p style="margin:0 0 26px;font-size:16px;line-height:1.65;color:#4F494D">Hãy xác nhận lời mời và hoàn tất hồ sơ để bắt đầu làm việc cùng đội ngũ.</p>
                          <a href="%s" style="display:inline-block;background:#AD4C64;color:#FFFFFF;text-decoration:none;font-size:15px;font-weight:700;padding:14px 24px;border-radius:12px">Kích hoạt tài khoản</a>
                          <div style="margin-top:28px;padding:18px;border-radius:12px;background:#F2ECEE;color:#6F676C;font-size:13px;line-height:1.6">
                            Liên kết có hiệu lực trong 48 giờ và chỉ dành cho địa chỉ email này. Nếu bạn không mong đợi lời mời, có thể bỏ qua email.
                          </div>
                        </td></tr>
                        <tr><td style="padding:20px 38px;border-top:1px solid #DED7DA;color:#8A8085;font-size:12px;line-height:1.6">The IELTS Spells · Đồng hành cùng hành trình IELTS của bạn</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(name, safeRole, link);
    }

    private String roleLabel(AppRole role) {
        return switch (role) {
            case ADMIN -> "Chủ doanh nghiệp / Quản trị viên";
            case TEACHER -> "Giáo viên";
            case ADMISSIONS -> "Tuyển sinh";
            case SOCIAL_MEDIA -> "Social Media";
            case STUDENT_SUPPORT -> "Hỗ trợ học viên";
            default -> role.name();
        };
    }
}

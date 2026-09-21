package com.theieltsspells.billing.application;

import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.Order;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;

@Slf4j
@Service
public class EmailBillingNotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private CourseRepository courseRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${spring.mail.username:noreply@theieltsspells.com}")
    private String fromEmail;

    @Async
    public void sendPaymentSuccessAndInvoiceEmail(Order order, ElectronicInvoice invoice, String activationToken) {
        Course course = courseRepository.findById(order.getCourseId()).orElse(null);
        String courseTitle = course != null ? course.getName() : "Khóa học IELTS";
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        String formattedAmount = currencyFormat.format(order.getAmount());

        boolean isGuest = (activationToken != null && !activationToken.isBlank());
        String activationUrl = isGuest ? String.format("%s/activate?token=%s", frontendUrl, activationToken) : null;
        String loginUrl = String.format("%s/login", frontendUrl);

        String subject = String.format("[The IELTS Spells] Xác nhận thanh toán & Kích hoạt khóa học: %s (Kèm Hóa đơn điện tử)", courseTitle);

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family: Arial, sans-serif; max-width: 620px; margin: 0 auto; color: #1e293b; line-height: 1.6;\">");
        html.append("<div style=\"background: linear-gradient(135deg, #4f46e5, #3730a3); padding: 24px; border-radius: 12px 12px 0 0; text-align: center; color: white;\">");
        html.append("<h1 style=\"margin: 0; font-size: 22px;\">The IELTS Spells</h1>");
        html.append("<p style=\"margin: 6px 0 0; opacity: 0.9; font-size: 14px;\">Biên nhận thanh toán & Hóa đơn điện tử</p>");
        html.append("</div>");

        html.append("<div style=\"padding: 24px; background: #ffffff; border: 1px solid #e2e8f0; border-top: none;\">");
        html.append(String.format("<p>Xin chào <strong>%s</strong>,</p>", order.getCustomerName()));
        html.append(String.format("<p>Cảm ơn bạn đã đăng ký khóa học <strong>%s</strong> tại The IELTS Spells. Chúng tôi đã nhận được khoản thanh toán <strong>%s</strong> (Mã đơn: <code>%s</code>).</p>", courseTitle, formattedAmount, order.getOrderCode()));

        // Call to action: Activation or Login
        html.append("<div style=\"background: #f8fafc; border: 1.5px solid #cbd5e1; border-radius: 8px; padding: 18px; margin: 20px 0; text-align: center;\">");
        if (isGuest) {
            html.append("<h3 style=\"margin-top: 0; color: #1e1b4b;\">🚀 BƯỚC TIẾP THEO: KÍCH HOẠT TÀI KHOẢN</h3>");
            html.append("<p style=\"font-size: 14px; color: #475569;\">Khóa học đã được thêm sẵn vào tài khoản của bạn. Hãy tạo mật khẩu để bắt đầu học ngay:</p>");
            html.append(String.format("<a href=\"%s\" style=\"display: inline-block; background: #4f46e5; color: #ffffff; padding: 12px 28px; font-size: 15px; font-weight: bold; text-decoration: none; border-radius: 6px; margin-top: 10px;\">KÍCH HOẠT TÀI KHOẢN & VÀO HỌC</a>", activationUrl));
            html.append("<p style=\"font-size: 12px; color: #94a3b8; margin-top: 12px;\">Đường link này dành riêng cho bạn và có hiệu lực trong 7 ngày.</p>");
        } else {
            html.append("<h3 style=\"margin-top: 0; color: #1e1b4b;\">🎉 KHÓA HỌC ĐÃ SẴN SÀNG</h3>");
            html.append("<p style=\"font-size: 14px; color: #475569;\">Khóa học đã được liên kết với tài khoản hiện tại của bạn. Bấm bên dưới để vào học:</p>");
            html.append(String.format("<a href=\"%s\" style=\"display: inline-block; background: #16a34a; color: #ffffff; padding: 12px 28px; font-size: 15px; font-weight: bold; text-decoration: none; border-radius: 6px; margin-top: 10px;\">ĐĂNG NHẬP VÀO HỌC NGAY</a>", loginUrl));
        }
        html.append("</div>");

        // Electronic Invoice Section
        if (invoice != null && invoice.getCqtCode() != null) {
            html.append("<div style=\"border-top: 2px dashed #e2e8f0; padding-top: 18px; margin-top: 24px;\">");
            html.append("<h4 style=\"margin: 0 0 10px; color: #0f172a;\">🧾 HÓA ĐƠN ĐIỆN TỬ CÓ MÃ CƠ QUAN THUẾ</h4>");
            html.append("<table style=\"width: 100%; font-size: 13px; border-collapse: collapse;\">");
            html.append(String.format("<tr><td style=\"color: #64748b; padding: 4px 0;\">Số hóa đơn:</td><td><strong>%s</strong> (Mẫu: %s)</td></tr>", invoice.getInvoiceNumber(), invoice.getInvoiceTemplate()));
            html.append(String.format("<tr><td style=\"color: #64748b; padding: 4px 0;\">Mã Cơ quan Thuế:</td><td><code style=\"background: #f1f5f9; padding: 2px 4px; border-radius: 4px;\">%s</code></td></tr>", invoice.getCqtCode()));
            html.append(String.format("<tr><td style=\"color: #64748b; padding: 4px 0;\">Mã tra cứu SePay:</td><td><code>%s</code></td></tr>", invoice.getLookupCode()));
            html.append("</table>");

            if (invoice.getLookupUrl() != null) {
                html.append(String.format("<p style=\"margin-top: 12px; font-size: 13px;\">👉 Tra cứu trực tuyến tại: <a href=\"%s\" target=\"_blank\">%s</a></p>", invoice.getLookupUrl(), invoice.getLookupUrl()));
            }
            html.append("</div>");
        }

        html.append("<p style=\"font-size: 13px; color: #64748b; margin-top: 30px; text-align: center;\">Nếu bạn cần hỗ trợ, vui lòng liên hệ hotline hoặc gửi email về support@theieltsspells.com.</p>");
        html.append("</div>");
        html.append("</div>");

        // Try sending via JavaMailSender
        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(order.getCustomerEmail());
                helper.setSubject(subject);
                helper.setText(html.toString(), true);
                mailSender.send(message);
                log.info("Đã gửi email xác nhận thanh toán & hóa đơn tới {}", order.getCustomerEmail());
                return;
            } catch (Exception ex) {
                log.warn("Không thể gửi email qua SMTP: {}. Ghi log nội dung để kiểm tra.", ex.getMessage());
            }
        }

        // Fallback logger for dev/staging
        log.info("========== [EMAIL NOTIFICATION MOCK] ==========");
        log.info("TO: {}", order.getCustomerEmail());
        log.info("SUBJECT: {}", subject);
        if (activationUrl != null) log.info("ACTIVATION LINK: {}", activationUrl);
        if (invoice != null) log.info("INVOICE CQT: {}, LOOKUP URL: {}", invoice.getCqtCode(), invoice.getLookupUrl());
        log.info("================================================");
    }
}

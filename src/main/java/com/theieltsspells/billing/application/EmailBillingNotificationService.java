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

    /**
     * Gửi email xác nhận thanh toán thành công & kích hoạt khóa học (gửi ngay sau khi thanh toán được ghi nhận)
     */
    @Async
    public void sendPaymentSuccessEmail(Order order, String activationToken) {
        Course course = courseRepository.findById(order.getCourseId()).orElse(null);
        String courseTitle = course != null ? course.getName() : "Khóa học IELTS";
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        String formattedAmount = currencyFormat.format(order.getAmount());

        boolean isGuest = (activationToken != null && !activationToken.isBlank());
        String activationUrl = isGuest ? String.format("%s/activate?token=%s", frontendUrl, activationToken) : null;
        String loginUrl = String.format("%s/login", frontendUrl);

        String subject = String.format("[The IELTS Spells] Xác nhận thanh toán thành công khóa học: %s", courseTitle);

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family: Arial, sans-serif; max-width: 620px; margin: 0 auto; color: #1e293b; line-height: 1.6;\">");
        html.append("<div style=\"background: linear-gradient(135deg, #4f46e5, #3730a3); padding: 24px; border-radius: 12px 12px 0 0; text-align: center; color: white;\">");
        html.append("<h1 style=\"margin: 0; font-size: 22px;\">The IELTS Spells</h1>");
        html.append("<p style=\"margin: 6px 0 0; opacity: 0.9; font-size: 14px;\">Biên nhận thanh toán khóa học</p>");
        html.append("</div>");

        html.append("<div style=\"padding: 24px; background: #ffffff; border: 1px solid #e2e8f0; border-top: none;\">");
        html.append(String.format("<p>Xin chào <strong>%s</strong>,</p>", order.getCustomerName()));
        html.append(String.format("<p>Cảm ơn bạn đã đăng ký khóa học <strong>%s</strong> tại The IELTS Spells. Chúng tôi đã nhận được khoản thanh toán <strong>%s</strong> (Mã đơn: <code>%s</code>).</p>", courseTitle, formattedAmount, order.getOrderCode()));

        // Call to action
        html.append("<div style=\"background: #f8fafc; border: 1.5px solid #cbd5e1; border-radius: 8px; padding: 18px; margin: 20px 0; text-align: center;\">");
        if (isGuest) {
            html.append("<h3 style=\"margin-top: 0; color: #1e1b4b;\">🚀 BƯỚC TIẾP THEO: KÍCH HOẠT TÀI KHOẢN</h3>");
            html.append("<p style=\"font-size: 14px; color: #475569;\">Khóa học đã được tạo sẵn trên hệ thống. Bạn chỉ cần kích hoạt mật khẩu để vào học ngay:</p>");
            html.append(String.format("<a href=\"%s\" style=\"display: inline-block; background: #4f46e5; color: #ffffff; padding: 12px 28px; font-size: 15px; font-weight: bold; text-decoration: none; border-radius: 6px; margin-top: 10px;\">KÍCH HOẠT TÀI KHOẢN & VÀO HỌC</a>", activationUrl));
            html.append("<p style=\"font-size: 12px; color: #94a3b8; margin-top: 12px;\">Đường link kích hoạt dành riêng cho bạn và có hiệu lực trong 7 ngày.</p>");
        } else {
            html.append("<h3 style=\"margin-top: 0; color: #1e1b4b;\">🎉 KHÓA HỌC ĐÃ SẴN SÀNG</h3>");
            html.append("<p style=\"font-size: 14px; color: #475569;\">Khóa học đã được liên kết với tài khoản của bạn. Bấm bên dưới để bắt đầu học tập:</p>");
            html.append(String.format("<a href=\"%s\" style=\"display: inline-block; background: #16a34a; color: #ffffff; padding: 12px 28px; font-size: 15px; font-weight: bold; text-decoration: none; border-radius: 6px; margin-top: 10px;\">ĐĂNG NHẬP VÀO HỌC NGAY</a>", loginUrl));
        }
        html.append("</div>");

        html.append("<p style=\"font-size: 13px; color: #64748b; margin-top: 30px; text-align: center;\">Hóa đơn điện tử hợp lệ (nếu yêu cầu) sẽ được gửi tới bạn ngay khi Cơ quan Thuế cấp mã.</p>");
        html.append("</div>");
        html.append("</div>");

        sendHtmlEmail(order.getCustomerEmail(), subject, html.toString());
    }

    /**
     * Gửi email Hóa đơn điện tử chính thức (CHỈ gửi khi hóa đơn đã ở trạng thái ISSUED)
     */
    @Async
    public void sendInvoiceIssuedEmail(Order order, ElectronicInvoice invoice) {
        if (invoice == null || invoice.getInvoiceNumber() == null) {
            log.warn("Bỏ qua gửi email hóa đơn vì thông tin hóa đơn chưa hoàn tất cho đơn {}", order.getOrderCode());
            return;
        }

        Course course = courseRepository.findById(order.getCourseId()).orElse(null);
        String courseTitle = course != null ? course.getName() : "Khóa học IELTS";

        String targetEmail = (order.getInvoiceEmail() != null && !order.getInvoiceEmail().isBlank())
                ? order.getInvoiceEmail()
                : order.getCustomerEmail();

        String subject = String.format("[The IELTS Spells] Hóa đơn điện tử số %s (Ký hiệu %s) - Đơn %s",
                invoice.getInvoiceNumber(),
                invoice.getInvoiceSeries() != null ? invoice.getInvoiceSeries() : "",
                order.getOrderCode());

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family: Arial, sans-serif; max-width: 620px; margin: 0 auto; color: #1e293b; line-height: 1.6;\">");
        html.append("<div style=\"background: linear-gradient(135deg, #059669, #047857); padding: 24px; border-radius: 12px 12px 0 0; text-align: center; color: white;\">");
        html.append("<h1 style=\"margin: 0; font-size: 22px;\">The IELTS Spells</h1>");
        html.append("<p style=\"margin: 6px 0 0; opacity: 0.9; font-size: 14px;\">Hóa đơn điện tử có mã của Cơ quan Thuế</p>");
        html.append("</div>");

        html.append("<div style=\"padding: 24px; background: #ffffff; border: 1px solid #e2e8f0; border-top: none;\">");
        html.append(String.format("<p>Kính gửi Quý khách <strong>%s</strong>,</p>",
                (order.getInvoiceCompanyName() != null && !order.getInvoiceCompanyName().isBlank())
                        ? order.getInvoiceCompanyName() : order.getCustomerName()));

        html.append(String.format("<p>The IELTS Spells xin gửi tới Quý khách thông tin Hóa đơn điện tử cho khóa học <strong>%s</strong>:</p>", courseTitle));

        // Invoice Table
        html.append("<div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; margin: 18px 0;\">");
        html.append("<table style=\"width: 100%; font-size: 13px; border-collapse: collapse;\">");
        html.append(String.format("<tr><td style=\"color: #64748b; padding: 6px 0; width: 35%%;\">Mã đơn hàng:</td><td><strong>%s</strong></td></tr>", order.getOrderCode()));
        html.append(String.format("<tr><td style=\"color: #64748b; padding: 6px 0;\">Số hóa đơn:</td><td><strong style=\"color: #059669; font-size: 16px;\">%s</strong></td></tr>", invoice.getInvoiceNumber()));
        if (invoice.getInvoiceSeries() != null) {
            html.append(String.format("<tr><td style=\"color: #64748b; padding: 6px 0;\">Ký hiệu:</td><td><strong>%s</strong></td></tr>", invoice.getInvoiceSeries()));
        }
        if (invoice.getCqtCode() != null) {
            html.append(String.format("<tr><td style=\"color: #64748b; padding: 6px 0;\">Mã CQT:</td><td><code style=\"background: #e2e8f0; padding: 2px 6px; border-radius: 4px;\">%s</code></td></tr>", invoice.getCqtCode()));
        }
        if (invoice.getLookupCode() != null) {
            html.append(String.format("<tr><td style=\"color: #64748b; padding: 6px 0;\">Mã tra cứu SePay:</td><td><code>%s</code></td></tr>", invoice.getLookupCode()));
        }
        html.append("</table>");
        html.append("</div>");

        // Action Buttons
        html.append("<div style=\"text-align: center; margin: 24px 0;\">");
        if (invoice.getPdfUrl() != null && !invoice.getPdfUrl().isBlank()) {
            html.append(String.format("<a href=\"%s\" target=\"_blank\" style=\"display: inline-block; background: #2563eb; color: #ffffff; padding: 10px 22px; font-size: 14px; font-weight: bold; text-decoration: none; border-radius: 6px; margin: 4px;\">TẢI HÓA ĐƠN PDF</a> ", invoice.getPdfUrl()));
        }
        if (invoice.getXmlUrl() != null && !invoice.getXmlUrl().isBlank()) {
            html.append(String.format("<a href=\"%s\" target=\"_blank\" style=\"display: inline-block; background: #475569; color: #ffffff; padding: 10px 22px; font-size: 14px; font-weight: bold; text-decoration: none; border-radius: 6px; margin: 4px;\">TẢI FILE GỐC XML</a>", invoice.getXmlUrl()));
        }
        html.append("</div>");

        html.append("<p style=\"font-size: 12px; color: #94a3b8; margin-top: 24px; text-align: center;\">Hóa đơn điện tử được khởi tạo và phát hành tự động qua cổng SePay eInvoice tuân thủ Nghị định 123/2020/NĐ-CP và Thông tư 78/2021/TT-BTC.</p>");
        html.append("</div>");
        html.append("</div>");

        sendHtmlEmail(targetEmail, subject, html.toString());
    }

    /**
     * Backward-compatible dual sender
     */
    @Async
    public void sendPaymentSuccessAndInvoiceEmail(Order order, ElectronicInvoice invoice, String activationToken) {
        sendPaymentSuccessEmail(order, activationToken);
        if (invoice != null && invoice.getCqtCode() != null) {
            sendInvoiceIssuedEmail(order, invoice);
        }
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlBody) {
        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(message);
                log.info("Đã gửi email thành công tới {}", toEmail);
                return;
            } catch (Exception ex) {
                log.error("Gửi email billing qua SMTP thất bại; cần retry/cảnh báo vận hành: {}", ex.getMessage(), ex);
                return;
            }
        }
        log.error("JavaMailSender chưa được cấu hình; email billing không được gửi");
    }
}

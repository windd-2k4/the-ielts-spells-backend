package com.theieltsspells.billing.application;

import com.theieltsspells.academic.application.EnrollmentApplicationService;
import com.theieltsspells.academic.application.dto.EnrollStudentRequest;
import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.billing.application.dto.ActivateAccountRequest;
import com.theieltsspells.billing.application.dto.ActivateAccountResponse;
import com.theieltsspells.billing.application.dto.VerifyActivationTokenResponse;
import com.theieltsspells.billing.domain.AccountActivationToken;
import com.theieltsspells.billing.domain.Order;
import com.theieltsspells.billing.infrastructure.persistence.AccountActivationTokenRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.domain.UserRole;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.UserRoleRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.AppRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountActivationService {

    private final AccountActivationTokenRepository tokenRepository;
    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final ProfileRepository profileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRoleRepository userRoleRepository;
    private final EnrollmentApplicationService enrollmentService;
    private final JdbcTemplate jdbc;

    @Transactional
    public String generateActivationToken(Order order) {
        // Generate a 64-character unique token
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        AccountActivationToken token = new AccountActivationToken();
        token.setOrderId(order.getId());
        token.setUserId(order.getUserId());
        token.setCustomerEmail(order.getCustomerEmail());
        token.setCustomerName(order.getCustomerName());
        token.setTokenHash(rawToken);
        token.setExpiresAt(OffsetDateTime.now().plusDays(7));
        token.setCreatedAt(OffsetDateTime.now());

        tokenRepository.save(token);
        return rawToken;
    }

    public VerifyActivationTokenResponse verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return new VerifyActivationTokenResponse(false, "", "", null, "", "Mã kích hoạt không hợp lệ");
        }

        Optional<AccountActivationToken> tokenOpt = tokenRepository.findByTokenHash(rawToken.trim());
        if (tokenOpt.isEmpty()) {
            return new VerifyActivationTokenResponse(false, "", "", null, "", "Không tìm thấy liên kết kích hoạt hoặc đã hết hạn");
        }

        AccountActivationToken token = tokenOpt.get();
        if (token.isUsed()) {
            return new VerifyActivationTokenResponse(false, token.getCustomerName(), token.getCustomerEmail(), null, "", "Liên kết kích hoạt này đã được sử dụng trước đó");
        }

        if (token.isExpired()) {
            return new VerifyActivationTokenResponse(false, token.getCustomerName(), token.getCustomerEmail(), null, "", "Liên kết kích hoạt đã hết hạn (quá 7 ngày)");
        }

        Order order = orderRepository.findById(token.getOrderId()).orElse(null);
        Course course = order != null ? courseRepository.findById(order.getCourseId()).orElse(null) : null;
        String courseTitle = course != null ? course.getName() : "Khóa học IELTS";

        return new VerifyActivationTokenResponse(
                true,
                token.getCustomerName(),
                token.getCustomerEmail(),
                order != null ? order.getCourseId() : null,
                courseTitle,
                "Liên kết hợp lệ. Vui lòng tạo mật khẩu để hoàn tất."
        );
    }

    @Transactional
    public ActivateAccountResponse activateAccount(ActivateAccountRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessRuleException("Mật khẩu xác nhận không khớp");
        }

        AccountActivationToken token = tokenRepository.findByTokenHash(request.token().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Liên kết kích hoạt không hợp lệ hoặc không tồn tại"));

        if (token.isUsed()) {
            throw new BusinessRuleException("Liên kết kích hoạt này đã được sử dụng");
        }

        if (token.isExpired()) {
            throw new BusinessRuleException("Liên kết kích hoạt đã quá hạn");
        }

        Order order = orderRepository.findById(token.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng liên kết"));

        String normalizedEmail = token.getCustomerEmail().trim().toLowerCase(Locale.ROOT);
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Ensure User / Profile exists
        Profile profile = profileRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        UUID userId;

        if (profile == null) {
            userId = UUID.randomUUID();

            // Insert into auth.users (compatibility with local or supabase auth)
            jdbc.update("insert into auth.users (id) values (?) on conflict (id) do nothing", userId);

            profile = new Profile();
            profile.setId(userId);
            profile.setFullName(token.getCustomerName());
            profile.setEmail(normalizedEmail);
            profile.setIsActive(true);
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            profileRepository.save(profile);

            // Assign STUDENT role
            UserRole studentRole = new UserRole();
            studentRole.setUserId(userId);
            studentRole.setRole(AppRole.STUDENT);
            studentRole.setAssignedAt(now);
            userRoleRepository.save(studentRole);

            // Ensure Student Profile
            String studentCode = "HV" + System.currentTimeMillis() % 1000000;
            studentProfileRepository.ensureProfile(userId, studentCode);
        } else {
            userId = profile.getId();
            profile.setIsActive(true);
            profile.setUpdatedAt(now);
            profileRepository.save(profile);
        }

        // 2. Mark token as used
        token.setUsedAt(now);
        token.setUserId(userId);
        tokenRepository.save(token);

        // 3. Update order with the user id
        order.setUserId(userId);
        orderRepository.save(order);

        // 4. Enroll student into the course
        try {
            enrollmentService.enroll(new EnrollStudentRequest(
                    order.getCourseId(),
                    userId,
                    "Kích hoạt tự động sau thanh toán đơn hàng " + order.getOrderCode()
            ));
        } catch (Exception ex) {
            log.warn("Học viên đã được ghi danh hoặc lỗi ghi danh: {}", ex.getMessage());
        }

        Course course = courseRepository.findById(order.getCourseId()).orElse(null);

        return new ActivateAccountResponse(
                true,
                "Kích hoạt tài khoản thành công! Khóa học đã sẵn sàng.",
                userId,
                normalizedEmail,
                profile.getFullName(),
                order.getCourseId(),
                "/student/courses/" + order.getCourseId()
        );
    }
}

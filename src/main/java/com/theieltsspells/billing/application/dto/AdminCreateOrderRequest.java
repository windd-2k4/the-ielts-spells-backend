package com.theieltsspells.billing.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminCreateOrderRequest(
        @NotNull(message = "Khóa học không được để trống")
        UUID courseId,

        @NotBlank(message = "Họ và tên học viên không được để trống")
        String fullName,

        @NotBlank(message = "Email học viên không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email,

        String phone,

        BigDecimal amount,

        Integer expiresInHours,

        String notes
) {}

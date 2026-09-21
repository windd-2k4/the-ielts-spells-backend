package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.InvoiceBuyerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutRequest(
        @NotNull(message = "Mã khóa học không được để trống")
        UUID courseId,

        @NotBlank(message = "Họ và tên không được để trống")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        String email,

        String phone,

        Boolean invoiceRequired,

        InvoiceBuyerType buyerType,

        String invoiceCompanyName,

        String invoiceTaxCode,

        String invoiceAddress,

        String invoiceEmail
) {}

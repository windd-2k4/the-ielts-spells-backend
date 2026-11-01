package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.InvoiceBuyerType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualInvoiceRequest(
        @NotBlank(message = "Tên người mua không được để trống")
        String buyerName,

        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
        BigDecimal amount,

        @NotNull(message = "Ngày tiền vào không được để trống")
        @PastOrPresent(message = "Ngày tiền vào không được ở tương lai")
        LocalDate paidDate,

        @NotNull(message = "Loại người mua không được để trống")
        InvoiceBuyerType buyerType,

        @Email(message = "Email người mua không đúng định dạng")
        String buyerEmail,

        String companyName,
        String taxCode,
        String address,
        String note
) {
}

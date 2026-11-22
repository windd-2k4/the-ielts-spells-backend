package com.theieltsspells.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStudentProfileRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 160, message = "Họ và tên không được vượt quá 160 ký tự")
        String fullName,
        @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
        String phone
) {}

package com.theieltsspells.identity.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record UpdateStudentRequest(
        @NotBlank String fullName,
        @Email String email,
        String phone,
        @DecimalMin("0.0") @DecimalMax("9.0") BigDecimal targetBand,
        LocalDate dateOfBirth,
        String address,
        Map<String, Object> emergencyContact,
        String notes
) {}

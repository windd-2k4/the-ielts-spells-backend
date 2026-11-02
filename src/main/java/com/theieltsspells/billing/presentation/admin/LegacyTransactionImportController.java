package com.theieltsspells.billing.presentation.admin;

import com.theieltsspells.billing.application.LegacyTransactionImportService;
import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.ImportResult;
import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.Preview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/billing/legacy-imports")
@Tag(name = "Admin - Legacy Billing Imports")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('admin')")
public class LegacyTransactionImportController {

    private final LegacyTransactionImportService importService;

    public LegacyTransactionImportController(LegacyTransactionImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    @Operation(summary = "Xem trước giao dịch cũ từ Excel, không ghi dữ liệu")
    public ResponseEntity<Preview> preview(
            @RequestParam MultipartFile file,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period
    ) {
        return ResponseEntity.ok(importService.preview(file, period));
    }

    @PostMapping("/import")
    @Operation(summary = "Tạo giao dịch lịch sử và hóa đơn nháp từ các dòng đã chọn")
    public ResponseEntity<ImportResult> importDrafts(
            @RequestParam MultipartFile file,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
            @RequestParam List<String> rowKeys,
            Authentication authentication
    ) {
        return ResponseEntity.ok(importService.importDrafts(file, period, rowKeys, authentication.getName()));
    }
}

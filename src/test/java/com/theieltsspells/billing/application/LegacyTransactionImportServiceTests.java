package com.theieltsspells.billing.application;

import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.Preview;
import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.RowStatus;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.PaymentTransaction;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyTransactionImportServiceTests {

    @Test
    void previewFiltersPeriodAndBlocksRowsWithoutPaymentDate() throws Exception {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        ElectronicInvoiceService invoices = mock(ElectronicInvoiceService.class);
        when(transactions.findBySepayTransactionIdIn(any())).thenReturn(List.of());
        LegacyTransactionImportService service = new LegacyTransactionImportService(transactions, invoices);

        Preview preview = service.preview(workbook(), YearMonth.of(2026, 10));

        assertThat(preview.totalRows()).isEqualTo(3);
        assertThat(preview.readyRows()).isEqualTo(1);
        assertThat(preview.warningRows()).isEqualTo(1);
        assertThat(preview.blockedRows()).isEqualTo(1);
        assertThat(preview.totalAmount()).isEqualByComparingTo("6499999.00");
        assertThat(preview.warnings()).contains("Thiếu STT: 118");
        assertThat(preview.rows()).anySatisfy(row -> {
            assertThat(row.sequenceNumber()).isEqualTo("117");
            assertThat(row.status()).isEqualTo(RowStatus.BLOCKED);
            assertThat(row.messages()).contains("Thiếu ngày tiền vào tài khoản");
        });
        assertThat(preview.rows()).noneMatch(row -> LocalDate.of(2026, 9, 30).equals(row.paidDate()));
    }

    @Test
    void importCreatesHistoricalTransactionAndQueuesDraftOnlyForSelectedRows() throws Exception {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        ElectronicInvoiceService invoices = mock(ElectronicInvoiceService.class);
        when(transactions.findBySepayTransactionIdIn(any())).thenReturn(List.of());
        AtomicInteger saved = new AtomicInteger();
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(new UUID(0, saved.incrementAndGet()));
            return transaction;
        });
        when(invoices.initOrGetDraftInvoice(any(), any())).thenAnswer(invocation -> {
            ElectronicInvoice invoice = new ElectronicInvoice();
            invoice.setId(UUID.randomUUID());
            return invoice;
        });
        LegacyTransactionImportService service = new LegacyTransactionImportService(transactions, invoices);
        MockMultipartFile file = workbook();
        Preview preview = service.preview(file, YearMonth.of(2026, 10));
        String selected = preview.rows().stream()
                .filter(row -> "119".equals(row.sequenceNumber()))
                .findFirst()
                .orElseThrow()
                .rowKey();

        var result = service.importDrafts(file, YearMonth.of(2026, 10), List.of(selected), "admin-user");

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.totalAmount()).isEqualByComparingTo("2500000.00");
        verify(transactions).save(any(PaymentTransaction.class));
        verify(invoices).initOrGetDraftInvoice(any(PaymentTransaction.class), org.mockito.ArgumentMatchers.eq("admin-user"));
    }

    private MockMultipartFile workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("T10");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("STT");
            header.createCell(1).setCellValue("Tên HV");
            header.createCell(2).setCellValue("Học phí đã đóng");
            header.createCell(3).setCellValue("Ngày chốt");
            header.createCell(4).setCellValue("Ghi chú");

            addRow(sheet, 1, 116, "Học viên tháng 9", 3_000_000, LocalDate.of(2026, 9, 30));
            addRow(sheet, 2, 117, "Trang Pham", 1_000_000, null);
            addRow(sheet, 3, 119, "Học viên A", 2_500_000, LocalDate.of(2026, 10, 30));
            addRow(sheet, 4, 120, "Anh Thuw", 3_999_999, LocalDate.of(2026, 10, 29));

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "transactions.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private void addRow(
            org.apache.poi.ss.usermodel.Sheet sheet,
            int rowIndex,
            int sequence,
            String name,
            double amount,
            LocalDate date
    ) {
        var row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(sequence);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(amount);
        if (date != null) {
            row.createCell(3).setCellValue(date);
        }
    }
}

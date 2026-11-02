package com.theieltsspells.billing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class LegacyTransactionImportDtos {

    private LegacyTransactionImportDtos() {
    }

    public enum RowStatus {
        READY,
        WARNING,
        BLOCKED,
        IMPORTED
    }

    public record Row(
            String rowKey,
            String sheetName,
            int rowNumber,
            String sequenceNumber,
            String payerName,
            BigDecimal amount,
            LocalDate paidDate,
            String note,
            RowStatus status,
            List<String> messages
    ) {
    }

    public record Preview(
            String fileName,
            String fileHash,
            YearMonth period,
            int totalRows,
            int readyRows,
            int warningRows,
            int blockedRows,
            int alreadyImportedRows,
            BigDecimal totalAmount,
            List<String> warnings,
            List<Row> rows
    ) {
    }

    public record ImportedDraft(
            String rowKey,
            UUID transactionId,
            UUID invoiceId
    ) {
    }

    public record ImportResult(
            int createdCount,
            int skippedCount,
            BigDecimal totalAmount,
            List<ImportedDraft> drafts
    ) {
    }
}

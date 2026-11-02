package com.theieltsspells.billing.application;

import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.ImportResult;
import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.ImportedDraft;
import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.Preview;
import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.Row;
import com.theieltsspells.billing.application.dto.LegacyTransactionImportDtos.RowStatus;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.PaymentTransaction;
import com.theieltsspells.billing.domain.PaymentTransactionStatus;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class LegacyTransactionImportService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private final PaymentTransactionRepository transactionRepository;
    private final ElectronicInvoiceService invoiceService;

    public LegacyTransactionImportService(
            PaymentTransactionRepository transactionRepository,
            ElectronicInvoiceService invoiceService
    ) {
        this.transactionRepository = transactionRepository;
        this.invoiceService = invoiceService;
    }

    @Transactional(readOnly = true)
    public Preview preview(MultipartFile file, YearMonth period) {
        ParsedFile parsed = parse(file, period);
        Map<String, PaymentTransaction> imported = findImported(parsed.period(), parsed.rows());
        List<Row> rows = parsed.rows().stream()
                .map(row -> imported.containsKey(externalId(parsed.period(), row))
                        ? withStatus(row, RowStatus.IMPORTED, "Dòng này đã được nhập trước đó")
                        : row)
                .toList();
        return summarize(parsed, rows);
    }

    @Transactional
    public ImportResult importDrafts(
            MultipartFile file,
            YearMonth period,
            List<String> selectedRowKeys,
            String actor
    ) {
        if (selectedRowKeys == null || selectedRowKeys.isEmpty()) {
            throw new BusinessRuleException("Hãy chọn ít nhất một giao dịch hợp lệ để tạo hóa đơn nháp");
        }

        ParsedFile parsed = parse(file, period);
        Set<String> selected = new HashSet<>(selectedRowKeys);
        Map<String, PaymentTransaction> imported = findImported(parsed.period(), parsed.rows());
        List<ImportedDraft> drafts = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int skipped = 0;

        for (Row row : parsed.rows()) {
            if (!selected.contains(row.rowKey())) {
                continue;
            }
            if (row.status() == RowStatus.BLOCKED) {
                skipped++;
                continue;
            }

            String externalId = externalId(parsed.period(), row);
            if (imported.containsKey(externalId)) {
                skipped++;
                continue;
            }

            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setGateway("LEGACY_IMPORT");
            transaction.setSepayTransactionId(externalId);
            transaction.setOrderCode("LEGACY-" + period.toString().replace("-", "") + "-"
                    + (row.sequenceNumber() == null || row.sequenceNumber().isBlank() ? row.rowNumber() : row.sequenceNumber().trim()));
            transaction.setAmountIn(row.amount());
            transaction.setAccumulatedAmount(row.amount());
            transaction.setTransferContent("Đóng học phí đào tạo IELTS");
            transaction.setPayerName(row.payerName());
            transaction.setStatus(PaymentTransactionStatus.STANDALONE_PAYMENT);
            transaction.setReconciliationNote("Nhập từ Excel " + row.sheetName() + " dòng " + row.rowNumber());
            transaction.setRawPayload(rawPayload(parsed, row, actor));
            transaction.setCreatedAt(row.paidDate().atStartOfDay(VIETNAM_ZONE).toOffsetDateTime());
            transaction = transactionRepository.save(transaction);

            ElectronicInvoice invoice = invoiceService.initOrGetDraftInvoice(transaction, actor);
            drafts.add(new ImportedDraft(row.rowKey(), transaction.getId(), invoice.getId()));
            totalAmount = totalAmount.add(row.amount());
        }

        if (drafts.isEmpty() && skipped == 0) {
            throw new BusinessRuleException("Các dòng được chọn không còn tồn tại trong file tải lên");
        }
        return new ImportResult(drafts.size(), skipped, totalAmount, drafts);
    }

    private Preview summarize(ParsedFile parsed, List<Row> rows) {
        int ready = 0;
        int warning = 0;
        int blocked = 0;
        int alreadyImported = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Row row : rows) {
            switch (row.status()) {
                case READY -> ready++;
                case WARNING -> warning++;
                case BLOCKED -> blocked++;
                case IMPORTED -> alreadyImported++;
            }
            if (row.status() != RowStatus.BLOCKED) {
                total = total.add(row.amount() == null ? BigDecimal.ZERO : row.amount());
            }
        }
        return new Preview(
                parsed.fileName(),
                parsed.fileHash(),
                parsed.period(),
                rows.size(),
                ready,
                warning,
                blocked,
                alreadyImported,
                total,
                parsed.warnings(),
                rows
        );
    }

    private Map<String, PaymentTransaction> findImported(YearMonth period, List<Row> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<String> ids = rows.stream().map(row -> externalId(period, row)).toList();
        Map<String, PaymentTransaction> result = new HashMap<>();
        for (PaymentTransaction transaction : transactionRepository.findBySepayTransactionIdIn(ids)) {
            result.put(transaction.getSepayTransactionId(), transaction);
        }
        return result;
    }

    private ParsedFile parse(MultipartFile file, YearMonth period) {
        validate(file, period);
        try {
            byte[] bytes = file.getBytes();
            String fileHash = sha256(bytes);
            List<Row> rows = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Set<Integer> sequenceNumbers = new HashSet<>();
            Pattern sheetPattern = Pattern.compile("(?i)^T\\s*0?" + period.getMonthValue() + "(?:\\s*\\(\\d+\\))?$");

            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                boolean foundSheet = false;

                for (Sheet sheet : workbook) {
                    if (!sheetPattern.matcher(sheet.getSheetName().trim()).matches()) {
                        continue;
                    }
                    foundSheet = true;
                    Header header = findHeader(sheet, formatter, evaluator);
                    if (header == null) {
                        warnings.add("Không đọc được tiêu đề ở sheet " + sheet.getSheetName());
                        continue;
                    }
                    parseSheet(sheet, header, period, formatter, evaluator, rows, sequenceNumbers);
                }

                if (!foundSheet) {
                    throw new BusinessRuleException("Không tìm thấy sheet T" + period.getMonthValue() + " trong file Excel");
                }
            }

            List<Integer> sorted = sequenceNumbers.stream().sorted().toList();
            if (!sorted.isEmpty()) {
                List<Integer> missing = new ArrayList<>();
                for (int number = sorted.getFirst(); number <= sorted.getLast(); number++) {
                    if (!sequenceNumbers.contains(number)) {
                        missing.add(number);
                    }
                }
                if (!missing.isEmpty()) {
                    warnings.add("Thiếu STT: " + String.join(", ", missing.stream().map(String::valueOf).toList()));
                }
            }
            rows.sort(Comparator.comparing(Row::paidDate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Row::sheetName)
                    .thenComparingInt(Row::rowNumber));
            return new ParsedFile(safeFileName(file.getOriginalFilename()), fileHash, period, warnings, rows);
        } catch (BusinessRuleException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessRuleException("Không đọc được file Excel: " + exception.getMessage());
        }
    }

    private void parseSheet(
            Sheet sheet,
            Header header,
            YearMonth period,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            List<Row> rows,
            Set<Integer> sequenceNumbers
    ) {
        for (int index = header.rowIndex() + 1; index <= sheet.getLastRowNum(); index++) {
            org.apache.poi.ss.usermodel.Row excelRow = sheet.getRow(index);
            if (excelRow == null) {
                continue;
            }

            String sequence = text(excelRow, header.sequenceColumn(), formatter, evaluator);
            Integer sequenceNumber = integer(sequence);
            if (sequenceNumber != null) {
                sequenceNumbers.add(sequenceNumber);
            }
            String payerName = cleanText(text(excelRow, header.nameColumn(), formatter, evaluator));
            BigDecimal amount = amount(excelRow.getCell(header.amountColumn(), MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter, evaluator);
            LocalDate paidDate = date(excelRow.getCell(header.dateColumn(), MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter, evaluator);
            String note = header.noteColumn() == null ? null : cleanText(text(excelRow, header.noteColumn(), formatter, evaluator));

            if (payerName.isBlank() && amount == null && paidDate == null) {
                continue;
            }
            if (paidDate != null && !YearMonth.from(paidDate).equals(period)) {
                continue;
            }

            List<String> messages = new ArrayList<>();
            RowStatus status = RowStatus.READY;
            if (payerName.isBlank()) {
                messages.add("Thiếu tên người mua");
                status = RowStatus.BLOCKED;
            }
            if (amount == null || amount.signum() <= 0) {
                messages.add("Số tiền không hợp lệ");
                status = RowStatus.BLOCKED;
            }
            if (paidDate == null) {
                messages.add("Thiếu ngày tiền vào tài khoản");
                status = RowStatus.BLOCKED;
            }
            if (status != RowStatus.BLOCKED && sequenceNumber == null) {
                messages.add("Thiếu STT");
                status = RowStatus.WARNING;
            }
            if (status != RowStatus.BLOCKED && amount.remainder(BigDecimal.valueOf(1000)).signum() != 0) {
                messages.add("Số tiền không tròn 1.000 đồng, cần kiểm tra lại");
                status = RowStatus.WARNING;
            }

            rows.add(new Row(
                    sheet.getSheetName() + "#" + (index + 1),
                    sheet.getSheetName(),
                    index + 1,
                    sequence,
                    payerName,
                    amount,
                    paidDate,
                    note,
                    status,
                    List.copyOf(messages)
            ));
        }
    }

    private Header findHeader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int limit = Math.min(sheet.getLastRowNum(), 30);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= limit; rowIndex++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) {
                columns.put(normalizeHeader(formatter.formatCellValue(cell, evaluator)), cell.getColumnIndex());
            }
            Integer name = columns.get("tenhv");
            Integer amount = columns.get("hocphidadong");
            Integer date = columns.get("ngaychot");
            if (name != null && amount != null && date != null) {
                return new Header(rowIndex, columns.getOrDefault("stt", 0), name, amount, date, columns.get("ghichu"));
            }
        }
        return null;
    }

    private void validate(MultipartFile file, YearMonth period) {
        if (period == null) {
            throw new BusinessRuleException("Thiếu tháng cần nhập");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Hãy chọn file Excel cần nhập");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessRuleException("File Excel không được vượt quá 10 MB");
        }
        String name = safeFileName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BusinessRuleException("Chỉ hỗ trợ file Excel .xlsx hoặc .xls");
        }
    }

    private Map<String, Object> rawPayload(ParsedFile parsed, Row row, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "LEGACY_EXCEL_IMPORT");
        payload.put("fileName", parsed.fileName());
        payload.put("fileHash", parsed.fileHash());
        payload.put("sheet", row.sheetName());
        payload.put("row", row.rowNumber());
        payload.put("stt", row.sequenceNumber());
        payload.put("paidDate", row.paidDate().toString());
        payload.put("importedBy", actor == null ? "unknown" : actor);
        if (row.note() != null && !row.note().isBlank()) payload.put("note", row.note());
        return payload;
    }

    private Row withStatus(Row row, RowStatus status, String message) {
        List<String> messages = new ArrayList<>(row.messages());
        messages.add(message);
        return new Row(row.rowKey(), row.sheetName(), row.rowNumber(), row.sequenceNumber(), row.payerName(),
                row.amount(), row.paidDate(), row.note(), status, List.copyOf(messages));
    }

    private String externalId(YearMonth period, Row row) {
        String stableKey = row.sequenceNumber() != null && !row.sequenceNumber().isBlank()
                ? row.sequenceNumber().trim()
                : row.payerName() + "|" + row.amount() + "|" + row.paidDate();
        return sha256(("LEGACY_IMPORT|" + period + "|" + stableKey).getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Không tạo được mã kiểm tra file", exception);
        }
    }

    private String text(org.apache.poi.ss.usermodel.Row row, int column, DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(column, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private String cleanText(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)_x0008_", "")
                .replaceAll("[\\p{Cc}\\p{Cf}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeHeader(String value) {
        return Normalizer.normalize(cleanText(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]", "");
    }

    private Integer integer(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replaceAll("[^0-9.-]", "")).intValueExact();
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal amount(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String value = formatter.formatCellValue(cell, evaluator).replaceAll("[^0-9,.-]", "");
        if (value.isBlank()) return null;
        if (value.matches("-?\\d{1,3}([.,]\\d{3})+")) {
            value = value.replace(".", "").replace(",", "");
        } else if (value.contains(",") && value.contains(".")) {
            value = value.lastIndexOf(',') > value.lastIndexOf('.')
                    ? value.replace(".", "").replace(',', '.')
                    : value.replace(",", "");
        } else if (value.contains(",")) {
            value = value.replace(',', '.');
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDate date(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }
        String value = formatter.formatCellValue(cell, evaluator).trim();
        for (DateTimeFormatter dateFormat : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, dateFormat);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted workbook date format.
            }
        }
        return null;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "transactions.xlsx";
        return fileName.replace('\\', '/').substring(fileName.replace('\\', '/').lastIndexOf('/') + 1);
    }

    private record Header(
            int rowIndex,
            int sequenceColumn,
            int nameColumn,
            int amountColumn,
            int dateColumn,
            Integer noteColumn
    ) {
    }

    private record ParsedFile(
            String fileName,
            String fileHash,
            YearMonth period,
            List<String> warnings,
            List<Row> rows
    ) {
    }
}

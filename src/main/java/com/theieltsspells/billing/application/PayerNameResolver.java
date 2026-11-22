package com.theieltsspells.billing.application;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bóc tách tên người nộp từ nội dung giao dịch nhưng không đánh đổi an toàn
 * phát hành hóa đơn. Nội dung gốc vẫn được lưu nguyên vẹn ở PaymentTransaction.
 */
final class PayerNameResolver {

    /**
     * Không phải tên người mua. Chỉ dùng cho hóa đơn cần đối soát khi SePay
     * không gửi đủ dữ liệu để xác định người chuyển.
     */
    private static final String REVIEW_NAME = "Cần đối soát";
    private static final String LEGACY_REVIEW_NAME = "Người nộp học phí";
    private static final Pattern TKP_PATTERN = Pattern.compile("(?iu)^TKP#[^#]+#(.+)$");
    private static final Pattern MOMO_PATTERN = Pattern.compile("(?iu)^\\d{6,}[-_\\s]+(.+)$");
    /** Vietcombank QR payload: ...<sender name>.<six digit bank code> */
    private static final Pattern TRAILING_SENDER_METADATA = Pattern.compile(
            "(?iu)^.*\\.(\\p{L}[\\p{L}\\s]{1,118})\\.\\d{6}$"
    );
    private static final Pattern MBVCB_AMOUNT_SENDER = Pattern.compile(
            "(?iu).*\\bS(?:ố|o)\\s+ti(?:ề|e)n\\s+[\\d.,]+\\s*VND[.\\s:_-]+(\\p{L}[\\p{L}\\s]{1,118})$"
    );
    private static final Pattern PHONE_SUFFIX = Pattern.compile("\\s+\\d{9,11}$");
    private static final Pattern ORDER_CODE = Pattern.compile("(?iu)\\bKH\\d{10,14}\\b");
    private static final Pattern NARRATIVE_SUFFIX = Pattern.compile(
            "(?iu)(?:[.\\s_-]+)(?:CT\\s+từ|CT\\s+tu|chuyển\\s+khoản|chuyen\\s+khoan|chuyển\\s+tiền|chuyen\\s+tien|thanh\\s+toán|thanh\\s+toan|đóng\\s+học\\s+phí|dong\\s+hoc\\s+phi|nộp\\s+tiền\\s+học|nop\\s+tien\\s+hoc)\\b.*$"
    );
    private static final Set<String> SYSTEM_ONLY = Set.of(
            "QRP", "TKP", "MBVCB", "MOMO", "VIETTELMONEY", "VIETCOMBANK", "MBBANK"
    );

    private PayerNameResolver() {
    }

    enum Source {
        ORDER, TKP, MBVCB, MOMO, VIETTEL_MONEY, FAST_TRANSFER, GENERIC, UNKNOWN
    }

    record Resolution(String name, boolean requiresReview, String reason, Source source) {
    }

    static boolean isReviewName(String value) {
        String cleaned = clean(value);
        return cleaned.equalsIgnoreCase(REVIEW_NAME) || cleaned.equalsIgnoreCase(LEGACY_REVIEW_NAME);
    }

    static Resolution resolve(String rawContent, String authoritativeOrderName, String orderCode) {
        String orderName = clean(authoritativeOrderName);
        if (isValid(orderName)) {
            return trusted(orderName, Source.ORDER);
        }

        String raw = clean(rawContent);
        if (raw.isBlank()) {
            return review(REVIEW_NAME, "Không có nội dung chuyển khoản", Source.UNKNOWN);
        }
        String withoutOrder = removeOrderCode(raw, orderCode);

        Resolution resolution = parseTkp(withoutOrder);
        if (resolution != null) return resolution;

        resolution = parseMbvcb(withoutOrder);
        if (resolution != null) return resolution;

        resolution = parseMomo(withoutOrder);
        if (resolution != null) return resolution;

        String candidate = stripNarrative(withoutOrder);
        if (isValid(candidate)) {
            Source source = containsIgnoreCase(withoutOrder, "Zalo")
                    ? Source.FAST_TRANSFER : Source.GENERIC;
            boolean trusted = source == Source.FAST_TRANSFER && !looksTechnical(candidate);
            return trusted
                    ? trusted(candidate, source)
                    : review(candidate, "Không nhận diện chắc chắn định dạng giao dịch", source);
        }
        return review(REVIEW_NAME, "Không bóc tách được tên người mua đáng tin cậy", Source.UNKNOWN);
    }

    private static Resolution parseTkp(String value) {
        Matcher matcher = TKP_PATTERN.matcher(value);
        if (!matcher.matches()) return null;

        String tail = clean(matcher.group(1));
        int qrMarker = indexOfIgnoreCase(tail, "QR -");
        if (qrMarker >= 0) {
            String narrative = clean(tail.substring(qrMarker + 4));
            int secondNameMarker = firstPositiveIndex(
                    indexOfIgnoreCase(narrative, " chuyen em "),
                    indexOfIgnoreCase(narrative, " chuyển em ")
            );
            boolean ambiguous = secondNameMarker >= 0;
            String candidate = ambiguous
                    ? narrative.substring(0, secondNameMarker)
                    : narrative;
            return finish(candidate, ambiguous, ambiguous
                    ? "Nội dung QR chứa nhiều tên, cần đối soát" : null, Source.TKP);
        }

        Matcher momoMetadata = Pattern.compile(
                "(?iu)^.*?\\.Test\\.\\d{8}\\.\\d{6}\\.\\d+\\.(.+?)\\.\\d+$"
        ).matcher(tail);
        if (momoMetadata.matches()) {
            return finish(momoMetadata.group(1), false, null, Source.MOMO);
        }

        int viettelSuffix = indexOfIgnoreCase(tail, " chuyen tien tu ViettelMoney");
        if (viettelSuffix > 0) {
            String beforeSuffix = tail.substring(0, viettelSuffix);
            String candidate = afterLastDot(beforeSuffix);
            return finish(candidate, false, null, Source.VIETTEL_MONEY);
        }

        // Với QR Vietcombank, phần người nhập nội dung có thể đứng trước
        // metadata; tên chủ tài khoản chuyển tiền nằm ngay trước mã ngân hàng
        // 6 chữ số ở cuối chuỗi. Ưu tiên trường này để không lấy nhầm tên trong
        // lời nhắn học phí.
        Matcher trailingSender = TRAILING_SENDER_METADATA.matcher(tail);
        if (trailingSender.matches()) {
            return finish(trailingSender.group(1), false, null, Source.TKP);
        }

        String candidate = tail;
        String[] parts = tail.split("\\.", 4);
        if (parts.length == 4) {
            candidate = parts[3];
        }
        return finish(stripNarrative(candidate), false, null, Source.TKP);
    }

    private static Resolution parseMbvcb(String value) {
        if (!value.regionMatches(true, 0, "MBVCB.", 0, 6)) return null;
        Matcher amountSender = MBVCB_AMOUNT_SENDER.matcher(value);
        if (amountSender.matches()) {
            return finish(amountSender.group(1), false, null, Source.MBVCB);
        }
        String[] parts = value.split("\\.", 3);
        String candidate = parts.length == 3 ? parts[2] : "";
        candidate = candidate.replaceFirst("(?iu)\\.CT\\s+(?:từ|tu)\\b.*$", "");
        return finish(candidate, false, null, Source.MBVCB);
    }

    private static Resolution parseMomo(String value) {
        Matcher matcher = MOMO_PATTERN.matcher(value);
        if (!matcher.matches() || !containsIgnoreCase(value, "MOMO")) return null;
        return finish(matcher.group(1), false, null, Source.MOMO);
    }

    private static Resolution finish(String candidate, boolean forceReview, String reason, Source source) {
        String cleaned = stripNarrative(candidate);
        if (!isValid(cleaned) || looksTechnical(cleaned)) {
            return review(REVIEW_NAME, "Tên bóc tách không đạt điều kiện an toàn", source);
        }
        return forceReview
                ? review(cleaned, reason, source)
                : trusted(cleaned, source);
    }

    private static String stripNarrative(String value) {
        // Một số Vietcombank payload lồng cả chuỗi "Vietcombank:QRP...:" vào
        // trước tên người chuyển. Cắt phần kỹ thuật trước khi kiểm tra tên,
        // nếu không parser sẽ loại nhầm tên hợp lệ vì thấy token QRP.
        String result = stripTechnicalPrefix(clean(value));
        result = ORDER_CODE.matcher(result).replaceAll("");
        result = NARRATIVE_SUFFIX.matcher(result).replaceFirst("");
        result = PHONE_SUFFIX.matcher(result).replaceFirst("");
        return clean(result);
    }

    private static String stripTechnicalPrefix(String value) {
        String result = clean(value);
        int lastColon = result.lastIndexOf(':');
        if (lastColon >= 0) {
            String prefix = result.substring(0, lastColon);
            if (containsIgnoreCase(prefix, "QRP")
                    || containsIgnoreCase(prefix, "TKP#")
                    || containsIgnoreCase(prefix, "Vietcombank")
                    || containsIgnoreCase(prefix, "MBBank")
                    || containsIgnoreCase(prefix, "ViettelMoney")) {
                result = result.substring(lastColon + 1);
            }
        }
        return result.replaceFirst("(?iu)^(?:Vietcombank|MBBank|ViettelMoney)\\s*[:.-]\\s*", "").trim();
    }

    private static String removeOrderCode(String value, String orderCode) {
        String result = value;
        if (orderCode != null && !orderCode.isBlank()) {
            result = result.replaceAll("(?iu)\\b" + Pattern.quote(orderCode.trim()) + "\\b", "");
        }
        return clean(result);
    }

    private static boolean isValid(String value) {
        if (value == null || value.length() < 2 || value.length() > 120) return false;
        if (value.indexOf('#') >= 0 || value.matches("\\d+")) return false;
        if (!value.matches("(?s).*\\p{L}.*")) return false;
        return !SYSTEM_ONLY.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private static boolean looksTechnical(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.contains("QRP") || upper.contains("TKP#") || upper.contains("MBVCB.")) return true;
        long digitCount = value.chars().filter(Character::isDigit).count();
        return digitCount > 12 || value.length() > 80;
    }

    private static String afterLastDot(String value) {
        int index = value.lastIndexOf('.');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private static int indexOfIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static int firstPositiveIndex(int... indexes) {
        int result = -1;
        for (int index : indexes) {
            if (index >= 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return indexOfIgnoreCase(value, needle) >= 0;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static Resolution trusted(String name, Source source) {
        return new Resolution(clean(name), false, null, source);
    }

    private static Resolution review(String name, String reason, Source source) {
        return new Resolution(clean(name), true, reason, source);
    }
}

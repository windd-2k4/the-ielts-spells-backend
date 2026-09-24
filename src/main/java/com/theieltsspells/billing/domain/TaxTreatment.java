package com.theieltsspells.billing.domain;

import lombok.Getter;

@Getter
public enum TaxTreatment {
    NOT_DECLARED(-1, "Không kê khai nộp thuế GTGT"),
    NOT_SUBJECT_TO_VAT(-2, "Không chịu thuế GTGT"),
    VAT_0(0, "Thuế suất 0%"),
    VAT_5(5, "Thuế suất 5%"),
    VAT_8(8, "Thuế suất 8%"),
    VAT_10(10, "Thuế suất 10%"),
    OTHER(null, "Khác (Tùy chỉnh)");

    private final Integer sepayTaxRate;
    private final String description;

    TaxTreatment(Integer sepayTaxRate, String description) {
        this.sepayTaxRate = sepayTaxRate;
        this.description = description;
    }

    public static TaxTreatment fromSepayTaxRate(Integer rate) {
        if (rate == null) return OTHER;
        for (TaxTreatment t : values()) {
            if (t.sepayTaxRate != null && t.sepayTaxRate.equals(rate)) {
                return t;
            }
        }
        return OTHER;
    }

    public static TaxTreatment fromCode(String code) {
        if (code == null || code.isBlank()) return OTHER;
        for (TaxTreatment t : values()) {
            if (t.name().equalsIgnoreCase(code.trim())) {
                return t;
            }
        }
        try {
            int rate = Integer.parseInt(code.trim());
            return fromSepayTaxRate(rate);
        } catch (NumberFormatException ignored) {
            return OTHER;
        }
    }
}

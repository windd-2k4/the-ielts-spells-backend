package com.theieltsspells.dictionary.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public enum DictionaryDirection {
    AUTO,
    EN_VI,
    VI_EN;

    private static final Pattern VIETNAMESE_CHARS = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public static DictionaryDirection resolve(String text, DictionaryDirection requested) {
        if (requested == EN_VI || requested == VI_EN) {
            return requested;
        }
        if (text != null && VIETNAMESE_CHARS.matcher(text).find()) {
            return VI_EN;
        }
        return EN_VI;
    }

    public static String normalizeForSearch(String input) {
        if (input == null) return "";
        String cleaned = input.replace("\uFEFF", "").replaceAll("\\s+", " ").trim();
        String decomposed = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }
}

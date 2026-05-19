package com.copa.ticketing.util;

import java.util.Locale;

public final class DocumentNumbers {

    private DocumentNumbers() {}

    public static String normalize(String documentType, String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if ("CPF".equalsIgnoreCase(documentType)) {
            return digitsOnly(trimmed);
        }
        return trimmed;
    }

    public static String normalizeForLookup(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if (looksLikeCpf(trimmed)) {
            return digitsOnly(trimmed);
        }
        return trimmed;
    }

    public static boolean isCpfLookup(String normalized) {
        return normalized.length() == 11 && normalized.chars().allMatch(Character::isDigit);
    }

    public static boolean isEmailLookup(String raw) {
        if (raw == null) return false;
        String trimmed = raw.trim();
        int at = trimmed.indexOf('@');
        return at > 0 && at < trimmed.length() - 1;
    }

    public static String normalizeEmailForLookup(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeCpf(String value) {
        String digits = digitsOnly(value);
        if (digits.length() != 11) return false;
        return value.equals(digits) || value.contains(".") || value.contains("-");
    }

    private static String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }
}

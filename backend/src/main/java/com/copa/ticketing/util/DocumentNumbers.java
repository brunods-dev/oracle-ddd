package com.copa.ticketing.util;

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

    private static boolean looksLikeCpf(String value) {
        String digits = digitsOnly(value);
        if (digits.length() != 11) return false;
        return value.equals(digits) || value.contains(".") || value.contains("-");
    }

    private static String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }
}

package com.example.estimateserver.utils;

import java.util.regex.Pattern;

public final class PhoneUtils {

    private static final Pattern DIGITS_ONLY = Pattern.compile("[^\\d]");
    private static final Pattern RU_PHONE = Pattern.compile("^7\\d{10}$");

    private PhoneUtils() {}

    public static String normalize(String phone) {
        if (phone == null) return null;

        String digits = DIGITS_ONLY.matcher(phone).replaceAll("");
        if (digits.isEmpty()) return null;

        String result;
        if (digits.startsWith("8") && digits.length() == 11) {
            result = "7" + digits.substring(1);
        } else if (digits.startsWith("7") && digits.length() == 11) {
            result = digits;
        } else if (digits.length() == 10) {
            result = "7" + digits;
        } else if (digits.length() > 11) {
            String last11 = digits.substring(digits.length() - 11);
            if (last11.startsWith("7") || last11.startsWith("8")) {
                result = normalize(last11);
            } else {
                result = "7" + last11;
            }
        } else {
            return null;
        }

        return isValid(result) ? result : null;
    }

    public static boolean isValid(String normalizedPhone) {
        return normalizedPhone != null && RU_PHONE.matcher(normalizedPhone).matches();
    }

    public static String mask(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
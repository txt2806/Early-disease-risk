package com.cardio.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AuthUtil {

    /**
     * Normalizes a phone number to standard Vietnamese format (starts with '0' followed by digits).
     * E.g.: "+84 912-345-678" -> "0912345678"
     *       "0912 345 678"    -> "0912345678"
     *       "84912345678"      -> "0912345678"
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        // Remove all non-digits
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return digits;
        }
        // Normalize Vietnamese international code
        if (digits.startsWith("84") && digits.length() > 9) {
            return "0" + digits.substring(2);
        }
        if (digits.startsWith("0")) {
            return digits;
        }
        // If it starts with 9 and has 9 digits, it's likely a mobile number missing prefix
        if (digits.length() == 9) {
            return "0" + digits;
        }
        return digits;
    }

    /**
     * Generates variations of a phone number format to search robustly.
     * E.g. input "+84912345678" -> ["+84912345678", "0912345678", "84912345678"]
     */
    public static List<String> getPhoneVariations(String rawPhone) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleaned = rawPhone.replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> variations = new LinkedHashSet<>();
        variations.add(rawPhone.trim()); // Always check raw input
        variations.add(cleaned);

        String digits = cleaned.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            variations.add(digits);
            
            String normalized = normalizePhone(cleaned);
            if (normalized != null && !normalized.isEmpty()) {
                variations.add(normalized);
                if (normalized.startsWith("0")) {
                    String base = normalized.substring(1);
                    variations.add("+84" + base);
                    variations.add("84" + base);
                }
            }
        }
        return new ArrayList<>(variations);
    }
}

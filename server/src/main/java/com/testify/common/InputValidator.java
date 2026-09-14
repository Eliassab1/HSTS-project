package com.testify.common;

import java.util.regex.Pattern;

/**
 * Centralized input hardening applied at the server request boundary as
 * defense-in-depth against SQL injection and malformed data — for every user
 * level (student, teacher, principal).
 *
 * <p>The DAOs are the primary defense: they use parameterized
 * {@link java.sql.PreparedStatement}s, so user values are never concatenated
 * into SQL. This class adds a second layer that strips control / NUL bytes,
 * enforces length limits, whitelists enum-like values, and validates any value
 * that must be used as a raw SQL identifier.
 */
public final class InputValidator {

    /** A safe SQL identifier: letters, digits and underscores only. */
    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    /** C0/C1 control characters and NUL, excluding tab, newline and carriage return. */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private InputValidator() {
    }

    /**
     * Trims the value, removes NUL/control characters, and caps its length.
     *
     * @param value     raw input (may be {@code null})
     * @param maxLength  maximum allowed length, or {@code <= 0} for no cap
     * @return cleaned value, or {@code null} if the input was {@code null}
     */
    public static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL_CHARS.matcher(value).replaceAll("").trim();
        if (maxLength > 0 && cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    /**
     * @return {@code true} if the value is a safe SQL identifier (letters,
     *         digits, underscore, up to 64 chars). Used before any value is
     *         embedded in DDL as a raw identifier.
     */
    public static boolean isSafeIdentifier(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches();
    }

    /**
     * Whitelists a value against a fixed set of allowed options.
     *
     * @return the matching allowed option (case-insensitive), or
     *         {@code defaultValue} when there is no match
     */
    public static String oneOf(String value, String defaultValue, String... allowed) {
        if (value != null) {
            for (String option : allowed) {
                if (option.equalsIgnoreCase(value)) {
                    return option;
                }
            }
        }
        return defaultValue;
    }

    /**
     * Normalizes a multiple-choice answer to a single uppercase letter A–D.
     *
     * @return "A", "B", "C" or "D", or {@code null} if the input is not one of them
     */
    public static String answerLetter(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase();
        return switch (v) {
            case "A", "B", "C", "D" -> v;
            default -> null;
        };
    }
}

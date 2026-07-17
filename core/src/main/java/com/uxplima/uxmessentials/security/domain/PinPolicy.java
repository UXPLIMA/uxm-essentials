package com.uxplima.uxmessentials.security.domain;

/**
 * The rules a PIN second factor must satisfy: a numeric-only string within a configured length range. It is a pure
 * value object so the format check is unit-testable without a database or a player, and so the same policy governs
 * both enrolment ({@code /pin set}) and, in the later join-verification phase, entry through the keypad GUI.
 *
 * <p>The policy deliberately checks <b>format only</b> — length and digits — and never touches the stored hash: a
 * PIN is one-way hashed the moment it passes this check, so this class sees the plaintext for exactly as long as it
 * takes to measure it. Numeric-only is intentional; the join-verification factor is a keypad, so a PIN must be
 * enterable there.
 *
 * @param minLength the fewest digits a PIN may have (inclusive)
 * @param maxLength the most digits a PIN may have (inclusive)
 */
public record PinPolicy(int minLength, int maxLength) {

    public PinPolicy {
        if (minLength < 1) {
            throw new IllegalArgumentException("pin min-length must be at least 1: " + minLength);
        }
        if (maxLength < minLength) {
            throw new IllegalArgumentException(
                    "pin max-length (" + maxLength + ") must be at least min-length (" + minLength + ")");
        }
    }

    /** Check {@code candidate} against this policy, returning the typed reason it passed or failed. */
    public PinValidation validate(String candidate) {
        java.util.Objects.requireNonNull(candidate, "candidate");
        if (!isAllDigits(candidate)) {
            return PinValidation.NOT_NUMERIC;
        }
        if (candidate.length() < minLength) {
            return PinValidation.TOO_SHORT;
        }
        if (candidate.length() > maxLength) {
            return PinValidation.TOO_LONG;
        }
        return PinValidation.OK;
    }

    private static boolean isAllDigits(String candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (!Character.isDigit(candidate.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}

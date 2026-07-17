package com.uxplima.uxmessentials.security.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The security context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code SECURITY_2FA_ENABLED} ↔ {@code security.2fa.enabled}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context — every message resolves through one of these.
 *
 * <p>This is the Phase-1 seed: the enrolment surface ({@code /2fa setup|confirm|disable}, {@code /pin set}). Later
 * phases (join verification, op-command protection, IP/alt guard) add their own keys here as their behaviour lands.
 * Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set.
 */
public enum SecurityMessageKey implements MessageKey {

    // /2fa setup — the enrolment challenge: the header, the secret and otpauth URI for the authenticator app, and
    // the hint to confirm with a code before it takes effect.
    SECURITY_2FA_SETUP_HEADER("security.2fa.setup-header"),
    SECURITY_2FA_SETUP_SECRET("security.2fa.setup-secret"),
    SECURITY_2FA_SETUP_URI("security.2fa.setup-uri"),
    SECURITY_2FA_SETUP_HINT("security.2fa.setup-hint"),
    SECURITY_2FA_ALREADY_ENROLLED("security.2fa.already-enrolled"),

    // /2fa confirm — the second step that turns the pending secret into an enabled factor.
    SECURITY_2FA_CONFIRM_USAGE("security.2fa.confirm-usage"),
    SECURITY_2FA_CONFIRM_NO_PENDING("security.2fa.confirm-no-pending"),
    SECURITY_2FA_CONFIRM_INVALID("security.2fa.confirm-invalid"),
    SECURITY_2FA_ENABLED("security.2fa.enabled"),

    // /2fa disable — removing a factor, which requires proving a current one first.
    SECURITY_2FA_DISABLE_USAGE("security.2fa.disable-usage"),
    SECURITY_2FA_DISABLE_NOT_ENROLLED("security.2fa.disable-not-enrolled"),
    SECURITY_2FA_DISABLE_INVALID("security.2fa.disable-invalid"),
    SECURITY_2FA_DISABLED("security.2fa.disabled"),

    // The bare /2fa root: its usage line, a status line, and the refusal when the whole 2FA feature is switched off.
    SECURITY_2FA_USAGE("security.2fa.usage"),
    SECURITY_2FA_STATUS("security.2fa.status"),
    SECURITY_2FA_FEATURE_DISABLED("security.2fa.feature-disabled"),

    // /pin set — the PIN factor: its usage, success, the three PinPolicy refusals, and the feature-off refusal.
    SECURITY_PIN_USAGE("security.pin.usage"),
    SECURITY_PIN_SET("security.pin.set"),
    SECURITY_PIN_TOO_SHORT("security.pin.too-short"),
    SECURITY_PIN_TOO_LONG("security.pin.too-long"),
    SECURITY_PIN_NOT_NUMERIC("security.pin.not-numeric"),
    SECURITY_PIN_FEATURE_DISABLED("security.pin.feature-disabled"),

    // Phase 2 — join verification: the freeze prompt on join, the keypad GUI (title, digit/clear/submit/TOTP buttons
    // and the masked entry display), the TOTP text prompt, the success and failed replies, the must-verify nudge when
    // a frozen player tries to act, and the lockout kick after too many failures.
    SECURITY_VERIFY_PROMPT("security.verify.prompt"),
    SECURITY_VERIFY_KEYPAD_TITLE("security.verify.keypad-title"),
    SECURITY_VERIFY_KEYPAD_DIGIT("security.verify.keypad-digit"),
    SECURITY_VERIFY_KEYPAD_CLEAR("security.verify.keypad-clear"),
    SECURITY_VERIFY_KEYPAD_SUBMIT("security.verify.keypad-submit"),
    SECURITY_VERIFY_KEYPAD_ENTRY("security.verify.keypad-entry"),
    SECURITY_VERIFY_KEYPAD_TOTP("security.verify.keypad-totp"),
    SECURITY_VERIFY_TOTP_PROMPT("security.verify.totp-prompt"),
    SECURITY_VERIFY_SUCCESS("security.verify.success"),
    SECURITY_VERIFY_FAILED("security.verify.failed"),
    SECURITY_VERIFY_MUST_VERIFY("security.verify.must-verify"),
    SECURITY_VERIFY_LOCKED_OUT("security.verify.locked-out");

    private final String key;

    SecurityMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

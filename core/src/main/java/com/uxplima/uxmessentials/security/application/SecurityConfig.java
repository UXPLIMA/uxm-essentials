package com.uxplima.uxmessentials.security.application;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/security/config.conf} for Phase 1: the module enable gate and the
 * two-factor tunables (which factors are offered, the authenticator issuer label, the verification window, and the
 * PIN length policy). Resolved once from the module's scoped {@link ConfigStore} on start and, per the atomic-reload
 * rule, swapped whole on reload — so a command dispatched mid-reload sees one coherent snapshot.
 *
 * <p>The HOCON keys are kebab-case under a {@code two-factor { … }} block; the record components are the camelCase
 * views the application reads. Every knob carries the default the bundled config ships, so an operator who deletes a
 * line falls back to the shipped value. The op-protection and IP/alt-guard blocks land with the later phases.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param twoFactor the two-factor enrolment settings ({@code two-factor.*})
 * @param joinVerification the join-verification (freeze + keypad) settings ({@code join-verification.*})
 */
public record SecurityConfig(boolean enabled, TwoFactorSettings twoFactor, JoinVerification joinVerification) {

    public SecurityConfig {
        Objects.requireNonNull(twoFactor, "twoFactor");
        Objects.requireNonNull(joinVerification, "joinVerification");
    }

    /** Resolve the security config from the module's scoped {@link ConfigStore} ({@code modules.security}). */
    public static SecurityConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new SecurityConfig(
                config.getBoolean("enabled", true), TwoFactorSettings.from(config), JoinVerification.from(config));
    }

    /**
     * The two-factor enrolment tunables ({@code two-factor.*}): the master switch, which of the two factors a player
     * may enrol (TOTP authenticator and/or PIN), the issuer label shown in the authenticator app, the ± time-step
     * tolerance a submitted code is checked within, and the PIN length policy. The numbers are validated here so a
     * nonsensical value never reaches the domain.
     *
     * @param enabled whether two-factor enrolment is offered at all ({@code two-factor.enabled}, default true)
     * @param totp whether the TOTP authenticator factor is offered ({@code two-factor.totp}, default true)
     * @param pin whether the PIN factor is offered ({@code two-factor.pin}, default true)
     * @param issuer the issuer label in the authenticator app ({@code two-factor.issuer}, default "uxmEssentials")
     * @param codeWindow the ± time-steps of tolerance when checking a code ({@code two-factor.code-window}, default 1)
     * @param pinPolicy the PIN length policy built from {@code two-factor.pin-min-length}/{@code pin-max-length}
     */
    public record TwoFactorSettings(
            boolean enabled, boolean totp, boolean pin, String issuer, int codeWindow, PinPolicy pinPolicy) {

        /** The default authenticator issuer label. */
        private static final String DEFAULT_ISSUER = "uxmEssentials";

        /** The default ± time-step tolerance: one step (±30s) absorbs modest clock skew. */
        private static final int DEFAULT_WINDOW = 1;

        private static final int DEFAULT_PIN_MIN = 4;
        private static final int DEFAULT_PIN_MAX = 8;

        /** The hard ceiling on the accepted window, so a misconfigured value cannot widen the factor open. */
        private static final int MAX_WINDOW = 5;

        public TwoFactorSettings {
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(pinPolicy, "pinPolicy");
            if (issuer.isBlank()) {
                throw new IllegalArgumentException("two-factor issuer must not be blank");
            }
            if (codeWindow < 0 || codeWindow > MAX_WINDOW) {
                throw new IllegalArgumentException("two-factor code-window must be between 0 and " + MAX_WINDOW);
            }
        }

        /** Resolve the two-factor settings from the module's scoped {@link ConfigStore} ({@code modules.security}). */
        public static TwoFactorSettings from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            int min = Math.max(1, config.getInt("two-factor.pin-min-length", DEFAULT_PIN_MIN));
            int max = Math.max(min, config.getInt("two-factor.pin-max-length", DEFAULT_PIN_MAX));
            return new TwoFactorSettings(
                    config.getBoolean("two-factor.enabled", true),
                    config.getBoolean("two-factor.totp", true),
                    config.getBoolean("two-factor.pin", true),
                    config.getString("two-factor.issuer", DEFAULT_ISSUER),
                    Math.min(MAX_WINDOW, Math.max(0, config.getInt("two-factor.code-window", DEFAULT_WINDOW))),
                    new PinPolicy(min, max));
        }
    }

    /**
     * The join-verification tunables ({@code join-verification.*}): whether an enrolled player is frozen and made to
     * prove a factor on join at all, whether a successful verification trusts the device so the next join skips the
     * prompt (and for how long), how many failures before the player is kicked, and how long that kick locks them out.
     * The numbers are validated here so a nonsensical value never reaches the keypad.
     *
     * @param enabled whether the join freeze is active ({@code join-verification.enabled}, default true)
     * @param trustDevices whether a verified device is remembered ({@code join-verification.trust-devices}, default true)
     * @param trustDuration how long a trusted device skips the prompt ({@code join-verification.trust-duration-hours})
     * @param maxAttempts failures allowed before a lockout ({@code join-verification.max-attempts}, default 3)
     * @param lockout how long a locked-out player is kicked for ({@code join-verification.lockout-seconds}, default 300)
     */
    public record JoinVerification(
            boolean enabled, boolean trustDevices, Duration trustDuration, int maxAttempts, Duration lockout) {

        private static final int DEFAULT_TRUST_HOURS = 24;
        private static final int DEFAULT_MAX_ATTEMPTS = 3;
        private static final int DEFAULT_LOCKOUT_SECONDS = 300;

        public JoinVerification {
            Objects.requireNonNull(trustDuration, "trustDuration");
            Objects.requireNonNull(lockout, "lockout");
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("join-verification max-attempts must be at least 1: " + maxAttempts);
            }
            if (trustDuration.isNegative()) {
                throw new IllegalArgumentException("join-verification trust-duration must not be negative");
            }
            if (lockout.isNegative()) {
                throw new IllegalArgumentException("join-verification lockout must not be negative");
            }
        }

        /** Resolve the join-verification settings from the module's scoped {@link ConfigStore}. */
        public static JoinVerification from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            int trustHours = Math.max(0, config.getInt("join-verification.trust-duration-hours", DEFAULT_TRUST_HOURS));
            int maxAttempts = Math.max(1, config.getInt("join-verification.max-attempts", DEFAULT_MAX_ATTEMPTS));
            int lockoutSeconds =
                    Math.max(0, config.getInt("join-verification.lockout-seconds", DEFAULT_LOCKOUT_SECONDS));
            return new JoinVerification(
                    config.getBoolean("join-verification.enabled", true),
                    config.getBoolean("join-verification.trust-devices", true),
                    Duration.ofHours(trustHours),
                    maxAttempts,
                    Duration.ofSeconds(lockoutSeconds));
        }

        /** The pure lockout decision this config drives — reused by the keypad to judge a failed attempt. */
        public LockoutPolicy lockoutPolicy() {
            return new LockoutPolicy(maxAttempts);
        }
    }
}

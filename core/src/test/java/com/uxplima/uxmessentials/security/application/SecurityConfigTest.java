package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/** Pins {@link SecurityConfig}: shipped defaults, the two-factor block, and the PIN-length clamp. */
class SecurityConfigTest {

    @Test
    void shipsSensibleDefaultsWhenTheFileIsEmpty() {
        SecurityConfig config = SecurityConfig.from(new MapConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        SecurityConfig.TwoFactorSettings twoFactor = config.twoFactor();
        assertThat(twoFactor.enabled()).isTrue();
        assertThat(twoFactor.totp()).isTrue();
        assertThat(twoFactor.pin()).isTrue();
        assertThat(twoFactor.issuer()).isEqualTo("uxmEssentials");
        assertThat(twoFactor.codeWindow()).isEqualTo(1);
        assertThat(twoFactor.pinPolicy()).isEqualTo(new PinPolicy(4, 8));

        SecurityConfig.JoinVerification join = config.joinVerification();
        assertThat(join.enabled()).isTrue();
        assertThat(join.trustDevices()).isTrue();
        assertThat(join.trustDuration()).isEqualTo(Duration.ofHours(24));
        assertThat(join.maxAttempts()).isEqualTo(3);
        assertThat(join.lockout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void readsTheJoinVerificationBlock() {
        Map<String, Object> values = new HashMap<>();
        values.put("join-verification.trust-devices", false);
        values.put("join-verification.trust-duration-hours", 6);
        values.put("join-verification.max-attempts", 5);
        values.put("join-verification.lockout-seconds", 120);

        SecurityConfig.JoinVerification join =
                SecurityConfig.from(new MapConfig(values)).joinVerification();

        assertThat(join.trustDevices()).isFalse();
        assertThat(join.trustDuration()).isEqualTo(Duration.ofHours(6));
        assertThat(join.maxAttempts()).isEqualTo(5);
        assertThat(join.lockout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(join.lockoutPolicy().maxAttempts()).isEqualTo(5);
    }

    @Test
    void readsTheTwoFactorBlockAndClampsAnInvertedPinRange() {
        Map<String, Object> values = new HashMap<>();
        values.put("two-factor.issuer", "MyServer");
        values.put("two-factor.code-window", 2);
        values.put("two-factor.pin-min-length", 6);
        values.put("two-factor.pin-max-length", 4); // inverted: max is clamped up to min

        SecurityConfig.TwoFactorSettings twoFactor =
                SecurityConfig.from(new MapConfig(values)).twoFactor();

        assertThat(twoFactor.issuer()).isEqualTo("MyServer");
        assertThat(twoFactor.codeWindow()).isEqualTo(2);
        assertThat(twoFactor.pinPolicy()).isEqualTo(new PinPolicy(6, 6));
    }

    @Test
    void clampsAnOutOfRangeCodeWindowIntoBounds() {
        SecurityConfig.TwoFactorSettings twoFactor = SecurityConfig.from(
                        new MapConfig(Map.of("two-factor.code-window", 99)))
                .twoFactor();

        assertThat(twoFactor.codeWindow()).isEqualTo(5);
    }

    /** A map-backed {@link ConfigStore} scoped as the module's subtree already is. */
    private record MapConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}

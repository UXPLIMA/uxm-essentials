package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.security.domain.ClientIdMode;
import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.security.domain.ReauthPolicy;
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

        SecurityConfig.OpProtection op = config.opProtection();
        assertThat(op.enabled()).isTrue();
        assertThat(op.reauthWindow()).isEqualTo(Duration.ofSeconds(60));
        assertThat(op.protectedCommands()).contains("op", "deop", "stop", "gamemode", "ban");

        SecurityConfig.IpGuard ipGuard = config.ipGuard();
        assertThat(ipGuard.enabled()).isTrue();
        assertThat(ipGuard.maxAccountsPerIp()).isZero();
        assertThat(ipGuard.notifyStaff()).isTrue();
        assertThat(ipGuard.limitPolicy().unlimited()).isTrue();

        SecurityConfig.ClientId clientId = config.clientId();
        assertThat(clientId.enabled()).isTrue();
        assertThat(clientId.mode()).isEqualTo(ClientIdMode.FLAG);
        assertThat(clientId.brands()).isEmpty();
    }

    @Test
    void readsTheIpGuardBlock() {
        Map<String, Object> values = new HashMap<>();
        values.put("ip-guard.enabled", false);
        values.put("ip-guard.max-accounts-per-ip", 3);
        values.put("ip-guard.notify-staff", false);

        SecurityConfig.IpGuard ipGuard =
                SecurityConfig.from(new MapConfig(values)).ipGuard();

        assertThat(ipGuard.enabled()).isFalse();
        assertThat(ipGuard.maxAccountsPerIp()).isEqualTo(3);
        assertThat(ipGuard.notifyStaff()).isFalse();
        assertThat(ipGuard.limitPolicy().evaluate(4))
                .isEqualTo(com.uxplima.uxmessentials.security.domain.AltLimitPolicy.Decision.DENY);
    }

    @Test
    void readsTheClientIdBlockAndParsesTheMode() {
        Map<String, Object> values = new HashMap<>();
        values.put("client-id.mode", "block-list");
        values.put("client-id.brands", List.of("wurst", "impact"));

        SecurityConfig.ClientId clientId =
                SecurityConfig.from(new MapConfig(values)).clientId();

        assertThat(clientId.mode()).isEqualTo(ClientIdMode.BLOCK_LIST);
        assertThat(clientId.brands()).containsExactly("wurst", "impact");
        assertThat(clientId.policy().judge("wurst").allowed()).isFalse();
    }

    @Test
    void fallsBackToFlagModeForAnUnknownModeString() {
        SecurityConfig.ClientId clientId = SecurityConfig.from(new MapConfig(Map.of("client-id.mode", "nonsense")))
                .clientId();

        assertThat(clientId.mode()).isEqualTo(ClientIdMode.FLAG);
    }

    @Test
    void readsTheOpProtectionBlock() {
        Map<String, Object> values = new HashMap<>();
        values.put("op-protection.enabled", false);
        values.put("op-protection.reauth-window-seconds", 15);
        values.put("op-protection.protected-commands", List.of("op", "whitelist"));

        SecurityConfig.OpProtection op =
                SecurityConfig.from(new MapConfig(values)).opProtection();

        assertThat(op.enabled()).isFalse();
        assertThat(op.reauthWindow()).isEqualTo(Duration.ofSeconds(15));
        assertThat(op.protectedCommands()).containsExactly("op", "whitelist");

        ReauthPolicy policy = op.policy();
        assertThat(policy.isProtected("/op Steve")).isTrue();
        assertThat(policy.isProtected("gamemode")).isFalse();
    }

    @Test
    void clampsANegativeReauthWindowToZero() {
        SecurityConfig.OpProtection op = SecurityConfig.from(
                        new MapConfig(Map.of("op-protection.reauth-window-seconds", -5)))
                .opProtection();

        assertThat(op.reauthWindow()).isEqualTo(Duration.ZERO);
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

        // The test only ever stores List<String> values under a list path, so the cast is safe here.
        @Override
        @SuppressWarnings("unchecked")
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> list ? List.copyOf((List<String>) list) : fallback;
        }
    }
}

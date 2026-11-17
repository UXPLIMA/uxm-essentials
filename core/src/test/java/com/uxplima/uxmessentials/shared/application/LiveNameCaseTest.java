package com.uxplima.uxmessentials.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import org.junit.jupiter.api.Test;

/**
 * A server that reports authenticated logins but lets offline-mode players in (a proxy in offline mode whose backend
 * keeps Paper's default proxy settings) is found out by its first offline-mode join.
 */
class LiveNameCaseTest {

    private static final UUID OFFLINE_STEVE =
            UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
    private static final UUID ONLINE_ACCOUNT = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID FLOODGATE = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");

    private final Lines log = new Lines();

    @Test
    void onAutoAnOfflineModeJoinSwitchesToCaseSensitiveAndSaysSoOnce() {
        LiveNameCase names = new LiveNameCase(LiveNameCase.Setting.AUTO, NameCase.INSENSITIVE, log);

        names.observe(ONLINE_ACCOUNT, "Notch");
        names.observe(FLOODGATE, ".BedrockGamer");
        assertThat(names.get()).isEqualTo(NameCase.INSENSITIVE);

        names.observe(OFFLINE_STEVE, "Steve");
        names.observe(UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes(StandardCharsets.UTF_8)), "Alex");

        assertThat(names.get()).isEqualTo(NameCase.SENSITIVE);
        assertThat(log.infos).hasSize(1).first().asString().contains("name_case_detected", "Steve");
    }

    @Test
    void aForcedSettingIsKeptWhateverJoins() {
        LiveNameCase insensitive = new LiveNameCase(LiveNameCase.Setting.INSENSITIVE, NameCase.SENSITIVE, log);
        LiveNameCase sensitive = new LiveNameCase(LiveNameCase.Setting.SENSITIVE, NameCase.INSENSITIVE, log);

        insensitive.observe(OFFLINE_STEVE, "Steve");

        assertThat(insensitive.get()).isEqualTo(NameCase.INSENSITIVE);
        assertThat(sensitive.get()).isEqualTo(NameCase.SENSITIVE);
        assertThat(log.infos).isEmpty();
    }

    @Test
    void anUnknownSettingReadsAsAutoWithAWarning() {
        assertThat(LiveNameCase.Setting.parse(" Sensitive ", log)).isEqualTo(LiveNameCase.Setting.SENSITIVE);
        assertThat(log.warnings).isEmpty();

        assertThat(LiveNameCase.Setting.parse("cracked", log)).isEqualTo(LiveNameCase.Setting.AUTO);
        assertThat(log.warnings).hasSize(1);
    }

    /** Keeps the info and warning lines, template followed by arguments. */
    private static final class Lines implements Logger {

        final List<String> infos = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(message + " " + java.util.Arrays.toString(args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message + " " + java.util.Arrays.toString(args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

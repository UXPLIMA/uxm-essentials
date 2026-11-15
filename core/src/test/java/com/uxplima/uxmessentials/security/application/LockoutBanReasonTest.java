package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/** The lockout ban reason reads from the catalogue unless an operator wrote their own. */
class LockoutBanReasonTest {

    private static final String PATH = "join-verification.lockout-ban-reason";
    private static final String SHIPPED = "Too many failed verification attempts";

    private final Notices log = new Notices();

    /** A server start that has said nothing yet. */
    private final AtomicBoolean told = new AtomicBoolean();

    @Test
    void anUnwrittenReasonReadsTheCatalogue() {
        SecurityConfig config = SecurityConfig.from(new Written(), log, told);

        assertThat(config.joinVerification().lockoutBanReason()).isEqualTo("@security.verify.lockout-ban-reason");
        assertThat(log.notices).isEmpty();
    }

    @Test
    void theEnglishSentenceTheFileShippedWithReadsTheCatalogueToo() {
        SecurityConfig config = SecurityConfig.from(new Written().set(PATH, SHIPPED), log, told);

        assertThat(config.joinVerification().lockoutBanReason()).isEqualTo("@security.verify.lockout-ban-reason");
        assertThat(log.notices).hasSize(1);
    }

    @Test
    void aReasonTheOperatorWroteIsKept() {
        SecurityConfig config =
                SecurityConfig.from(new Written().set(PATH, "Locked out, ask staff on Discord"), log, told);

        assertThat(config.joinVerification().lockoutBanReason()).isEqualTo("Locked out, ask staff on Discord");
        assertThat(log.notices).isEmpty();
    }

    @Test
    void theShippedSentenceIsMentionedOncePerServerStart() {
        SecurityConfig.from(new Written().set(PATH, SHIPPED), log, told);
        SecurityConfig.from(new Written().set(PATH, SHIPPED), log, told);

        assertThat(log.notices).hasSize(1);
    }

    @Test
    void theShippedSentenceIsNotMentionedWhileLockoutBansAreOff() {
        SecurityConfig config = SecurityConfig.from(
                new Written().set(PATH, SHIPPED).set("join-verification.lockout-bans", false), log, told);

        assertThat(config.joinVerification().lockoutBanReason()).isEqualTo("@security.verify.lockout-ban-reason");
        assertThat(log.notices).isEmpty();
    }

    /** Keeps the info lines, the ones an operator is told about a value read differently. */
    private static final class Notices implements Logger {

        final List<String> notices = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            notices.add(message);
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** The security config as an operator wrote it: the keys given here, every other key at its default. */
    private static final class Written implements ConfigStore {

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Boolean> switches = new HashMap<>();

        Written set(String path, String value) {
            values.put(path, value);
            return this;
        }

        Written set(String path, boolean value) {
            switches.put(path, value);
            return this;
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return switches.getOrDefault(path, fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            return values.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}

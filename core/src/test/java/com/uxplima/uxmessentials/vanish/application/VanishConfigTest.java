package com.uxplima.uxmessentials.vanish.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/** The fake connection lines read from the catalogue unless an operator wrote their own. */
class VanishConfigTest {

    private final Notices log = new Notices();

    @Test
    void anUnwrittenLineReadsTheCatalogue() {
        VanishConfig config = VanishConfig.from(new Written(), log);

        assertThat(config.fakeQuitMessage()).isEqualTo("@vanish.fake-quit");
        assertThat(config.fakeJoinMessage()).isEqualTo("@vanish.fake-join");
    }

    @Test
    void theEnglishLineEarlierVersionsShippedReadsTheCatalogueToo() {
        VanishConfig config = VanishConfig.from(
                new Written()
                        .set("fake-quit-message", "<yellow>{player} left the game")
                        .set("fake-join-message", "<yellow>{player} joined the game"),
                log);

        assertThat(config.fakeQuitMessage()).isEqualTo("@vanish.fake-quit");
        assertThat(config.fakeJoinMessage()).isEqualTo("@vanish.fake-join");
        assertThat(log.notices).hasSize(1);
    }

    @Test
    void aLineTheOperatorWroteIsKept() {
        VanishConfig config = VanishConfig.from(
                new Written()
                        .set("fake-quit-message", "<gray>{player} went home")
                        .set("fake-join-message", "@custom.welcome"),
                log);

        assertThat(config.fakeQuitMessage()).isEqualTo("<gray>{player} went home");
        assertThat(config.fakeJoinMessage()).isEqualTo("@custom.welcome");
        assertThat(log.notices).isEmpty();
    }

    @Test
    void theStaffLinesStayBlankUnlessWritten() {
        VanishConfig config = VanishConfig.from(new Written(), log);

        assertThat(config.fakeQuitMessageStaff()).isEmpty();
        assertThat(config.fakeJoinMessageStaff()).isEmpty();
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

    /** The vanish config as an operator wrote it: the keys given here, every other key at its default. */
    private static final class Written implements ConfigStore {

        private final Map<String, String> values = new HashMap<>();

        Written set(String path, String value) {
            values.put(path, value);
            return this;
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
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

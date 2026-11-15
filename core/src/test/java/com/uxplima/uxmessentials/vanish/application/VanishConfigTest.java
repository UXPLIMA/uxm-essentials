package com.uxplima.uxmessentials.vanish.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/** The fake connection lines read from the catalogue unless an operator wrote their own. */
class VanishConfigTest {

    @Test
    void anUnwrittenLineReadsTheCatalogue() {
        VanishConfig config = VanishConfig.from(new Written());

        assertThat(config.fakeQuitMessage()).isEqualTo("@vanish.fake-quit");
        assertThat(config.fakeJoinMessage()).isEqualTo("@vanish.fake-join");
    }

    @Test
    void theEnglishLineEarlierVersionsShippedReadsTheCatalogueToo() {
        VanishConfig config = VanishConfig.from(new Written()
                .set("fake-quit-message", "<yellow>{player} left the game")
                .set("fake-join-message", "<yellow>{player} joined the game"));

        assertThat(config.fakeQuitMessage()).isEqualTo("@vanish.fake-quit");
        assertThat(config.fakeJoinMessage()).isEqualTo("@vanish.fake-join");
    }

    @Test
    void aLineTheOperatorWroteIsKept() {
        VanishConfig config = VanishConfig.from(new Written()
                .set("fake-quit-message", "<gray>{player} went home")
                .set("fake-join-message", "@custom.welcome"));

        assertThat(config.fakeQuitMessage()).isEqualTo("<gray>{player} went home");
        assertThat(config.fakeJoinMessage()).isEqualTo("@custom.welcome");
    }

    @Test
    void theStaffLinesStayBlankUnlessWritten() {
        VanishConfig config = VanishConfig.from(new Written());

        assertThat(config.fakeQuitMessageStaff()).isEmpty();
        assertThat(config.fakeJoinMessageStaff()).isEmpty();
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

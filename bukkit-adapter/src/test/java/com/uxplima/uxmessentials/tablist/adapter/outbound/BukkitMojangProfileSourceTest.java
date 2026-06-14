package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the {@link BukkitMojangProfileSource} fetch fail-soft: a blocking Mojang round-trip that fails (here the
 * MockBukkit profile cannot complete, exactly the shape of a misspelled name / rate-limit / outage on real Paper) must
 * be logged at debug (an operator signal that does not spam the default log) and still fall back to no skin so the
 * tablist renders on the native path.
 */
class BukkitMojangProfileSourceTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFailedFetchLogsAtDebugWithTheNameAndFallsBackToEmpty() {
        RecordingLogger log = new RecordingLogger();
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(log);

        Optional<TabSkin> result = source.fetchTexture("BadName");

        assertThat(result).isEmpty();
        // The failure is logged at debug — not warn — naming the offending name so an operator can grep for it.
        assertThat(log.debugLines)
                .anyMatch(line -> line.contains("tablist skin fetch failed") && line.contains("BadName"));
        assertThat(log.warnLines).isEmpty();
    }

    @Test
    void anOnlineNameWithNoLiveProfileResolvesEmptyWithoutLogging() {
        // The online read is inline and never fetches, so a name with no live player simply yields empty and logs
        // nothing — only the blocking fetch path is the one that can fail and must report.
        RecordingLogger log = new RecordingLogger();
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(log);

        Optional<TabSkin> result = source.onlineTexture("Nobody");

        assertThat(result).isEmpty();
        assertThat(log.debugLines).isEmpty();
    }

    @Test
    void aFailedFetchNeverThrows() {
        BukkitMojangProfileSource source = new BukkitMojangProfileSource(new RecordingLogger());

        assertThatCode(() -> source.fetchTexture("AnotherBadName")).doesNotThrowAnyException();
    }

    /** A logger that captures formatted {@code debug} / {@code warn} lines so a test can assert what was reported where. */
    private static final class RecordingLogger implements Logger {
        private final List<String> debugLines = new ArrayList<>();
        private final List<String> warnLines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnLines.add(format(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {
            debugLines.add(format(message, args));
        }

        private static String format(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                int at = out.indexOf("{}");
                if (at < 0) {
                    break;
                }
                out = out.substring(0, at) + arg + out.substring(at + 2);
            }
            return out;
        }
    }
}

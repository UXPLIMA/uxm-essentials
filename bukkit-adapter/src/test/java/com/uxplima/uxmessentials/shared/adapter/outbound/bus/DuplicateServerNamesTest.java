package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.junit.jupiter.api.Test;

/** A second backend under this backend's name is reported, even though the bus drops its frames. */
class DuplicateServerNamesTest {

    private static final String NAME = "survival-1";
    private static final long OWN_NONCE = 7L;
    private static final Duration WINDOW = Duration.ofSeconds(90);
    private static final long START = 1_700_000_000_000L;

    private long now = START;
    private final RecordingLogger log = new RecordingLogger();
    private final DuplicateServerNames duplicates = new DuplicateServerNames(NAME, OWN_NONCE, WINDOW, () -> now, log);
    private final List<NetworkMessage> dispatched = new ArrayList<>();
    private final BusCore core = core();

    private BusCore core() {
        RemoteSyncRegistry registry = new RemoteSyncRegistry();
        registry.register(dispatched::add);
        return new BusCore(new IdleTransport(), NAME, registry, duplicates, log);
    }

    private void arrive(NetworkMessage message) {
        core.onFrame(NetworkMessageCodec.encode(message));
    }

    @Test
    void aHeartbeatWithOurNameAndAnotherBootIsReportedAndStillDropped() {
        arrive(new ServerPing(NAME, START, 99L));

        assertThat(log.warnings).singleElement().asString().contains("'survival-1'", "network.server-name");
        assertThat(duplicates.seen()).isTrue();
        assertThat(dispatched).isEmpty();
    }

    @Test
    void theSameClashingBootIsReportedOnceAndANewBootAgain() {
        arrive(new ServerPing(NAME, START, 99L));
        now += 30_000;
        arrive(new ServerPing(NAME, now, 99L));
        now += 30_000;
        arrive(new ServerPing(NAME, now, 99L));
        arrive(new ServerPing(NAME, now, 100L));

        assertThat(log.warnings).hasSize(2);
    }

    @Test
    void ourOwnEchoAndAPeerWithoutANonceAreNotAClash() {
        arrive(new ServerPing(NAME, START, OWN_NONCE));
        arrive(new ServerPing(NAME, START, ServerPing.NO_NONCE));
        arrive(new HomeChanged(NAME, new UUID(1L, 2L)));

        assertThat(log.warnings).isEmpty();
        assertThat(duplicates.seen()).isFalse();
    }

    @Test
    void aClashClearsOnceTheOtherBackendFallsSilentForTheWindow() {
        arrive(new ServerPing(NAME, START, 99L));

        now = START + WINDOW.toMillis();
        assertThat(duplicates.seen()).isTrue();
        now = START + WINDOW.toMillis() + 1;
        assertThat(duplicates.seen()).isFalse();
    }

    @Test
    void thePeersOwnClockNeitherHidesNorProlongsTheClash() {
        arrive(new ServerPing(NAME, START - Duration.ofHours(3).toMillis(), 99L));
        assertThat(duplicates.seen()).isTrue();

        arrive(new ServerPing(NAME, START + Duration.ofHours(3).toMillis(), 100L));
        now = START + WINDOW.toMillis() + 1;
        assertThat(duplicates.seen()).isFalse();
    }

    @Test
    void aPeerWithAnotherNameIsDispatchedAndNotAClash() {
        ServerPing peer = new ServerPing("lobby-1", START, 99L);

        arrive(peer);

        assertThat(dispatched).containsExactly(peer);
        assertThat(log.warnings).isEmpty();
    }

    private static final class IdleTransport implements BusTransport {
        @Override
        public void start(Consumer<byte[]> onFrame) {}

        @Override
        public void stop() {}

        @Override
        public void send(byte[] frame) {}

        @Override
        public boolean healthy() {
            return true;
        }
    }

    private static final class RecordingLogger implements Logger {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            warnings.add(line);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

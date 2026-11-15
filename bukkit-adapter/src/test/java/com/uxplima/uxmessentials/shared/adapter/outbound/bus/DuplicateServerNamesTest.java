package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    private static final long NOW = 1_700_000_000_000L;

    private final RecordingLogger log = new RecordingLogger();
    private final DuplicateServerNames duplicates = new DuplicateServerNames(NAME, OWN_NONCE, WINDOW, log);
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
    void aHeartbeatWithOurNameAndAnotherBootIsAnErrorAndStillDropped() {
        arrive(new ServerPing(NAME, NOW, 99L));

        assertThat(log.errors).singleElement().asString().contains("'survival-1'", "network.server-name");
        assertThat(duplicates.seen(NOW)).isTrue();
        assertThat(dispatched).isEmpty();
    }

    @Test
    void theSameClashingBootIsReportedOnceAndANewBootAgain() {
        arrive(new ServerPing(NAME, NOW, 99L));
        arrive(new ServerPing(NAME, NOW + 30_000, 99L));
        arrive(new ServerPing(NAME, NOW + 60_000, 99L));
        arrive(new ServerPing(NAME, NOW + 90_000, 100L));

        assertThat(log.errors).hasSize(2);
    }

    @Test
    void ourOwnEchoAndAPeerWithoutANonceAreNotAClash() {
        arrive(new ServerPing(NAME, NOW, OWN_NONCE));
        arrive(new ServerPing(NAME, NOW, ServerPing.NO_NONCE));
        arrive(new HomeChanged(NAME, new java.util.UUID(1L, 2L)));

        assertThat(log.errors).isEmpty();
        assertThat(duplicates.seen(NOW)).isFalse();
    }

    @Test
    void aClashClearsOnceTheOtherBackendFallsSilent() {
        arrive(new ServerPing(NAME, NOW, 99L));

        assertThat(duplicates.seen(NOW + WINDOW.toMillis())).isTrue();
        assertThat(duplicates.seen(NOW + WINDOW.toMillis() + 1)).isFalse();
    }

    @Test
    void aLateOlderHeartbeatDoesNotShortenTheClash() {
        arrive(new ServerPing(NAME, NOW + 60_000, 99L));
        arrive(new ServerPing(NAME, NOW, 99L));

        assertThat(duplicates.seen(NOW + 60_000 + WINDOW.toMillis())).isTrue();
    }

    @Test
    void aPeerWithAnotherNameIsDispatchedAndNotAClash() {
        ServerPing peer = new ServerPing("lobby-1", NOW, 99L);

        arrive(peer);

        assertThat(dispatched).containsExactly(peer);
        assertThat(log.errors).isEmpty();
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
        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void error(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            errors.add(line);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}

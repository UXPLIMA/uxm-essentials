package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;

import org.bukkit.plugin.messaging.PluginMessageListener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The transport counts the proxy as answering only for a frame on its own channel while it runs. */
class PluginMessagingTransportTest {

    private static final String CHANNEL = "uxmessentials:bus_v1";
    private static final Duration WINDOW = Duration.ofSeconds(90);
    private static final long START = 1_700_000_000_000L;
    private static final byte[] FRAME = {1, 5, 0};

    private long now = START;
    private final FakeChannel pluginChannel = new FakeChannel();
    private final List<byte[]> delivered = new ArrayList<>();
    private final PluginMessagingTransport transport = new PluginMessagingTransport(
            pluginChannel, new ImmediateScheduler(), CHANNEL, 16, WINDOW, new QuietLogger(), () -> now);

    @Test
    void aFrameBeforeStartOrOnAnotherChannelIsNotAnAnswer() {
        transport.received(CHANNEL, FRAME);
        transport.start(delivered::add);
        transport.received("other:channel", FRAME);

        assertThat(transport.link()).isEqualTo(new BusHealth.ProxyLink(true, OptionalLong.empty(), false));
        assertThat(transport.healthy()).isFalse();
        assertThat(delivered).isEmpty();
    }

    @Test
    void aFrameOnTheChannelIsAnAnswerUntilTheWindowPasses() {
        transport.start(delivered::add);
        transport.received(CHANNEL, FRAME);

        assertThat(transport.link().lastAnswerMillis()).hasValue(START);
        assertThat(delivered).containsExactly(FRAME);
        now = START + WINDOW.toMillis();
        assertThat(transport.healthy()).isTrue();
        now = START + WINDOW.toMillis() + 1;
        assertThat(transport.healthy()).isFalse();
    }

    @Test
    void stoppingForgetsTheLastAnswerAndUnregistersTheChannel() {
        transport.start(delivered::add);
        transport.received(CHANNEL, FRAME);

        transport.stop();
        transport.received(CHANNEL, FRAME);

        assertThat(transport.link()).isEqualTo(new BusHealth.ProxyLink(false, OptionalLong.empty(), false));
        assertThat(pluginChannel.open).isFalse();
        assertThat(delivered).containsExactly(FRAME);
    }

    @Test
    void aSendWithNobodyOnlineIsReportedAndTheNextPlayerCarriesTheQueue() {
        transport.start(delivered::add);

        transport.send(FRAME);
        assertThat(transport.link().waitingForPlayer()).isTrue();

        pluginChannel.online = true;
        transport.send(new byte[] {1, 6});
        assertThat(transport.link().waitingForPlayer()).isFalse();
        assertThat(pluginChannel.sent).containsExactly(FRAME, new byte[] {1, 6});
    }

    /** The server side of the channel: whether it is registered, and an online player when one is set. */
    private static final class FakeChannel implements PluginMessagingTransport.PluginChannel {

        boolean open;
        boolean online;
        final List<byte[]> sent = new ArrayList<>();

        @Override
        public void open(PluginMessageListener listener) {
            open = true;
        }

        @Override
        public void close(PluginMessageListener listener) {
            open = false;
        }

        @Override
        public @Nullable Consumer<byte[]> carrier() {
            return online ? sent::add : null;
        }
    }

    private static final class ImmediateScheduler implements Scheduler {

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }
    }

    private static final class QuietLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

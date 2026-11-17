package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.bukkit.plugin.messaging.PluginMessageListener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import com.uxplima.uxmessentials.shared.network.BanChanged;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.BusSecret;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The transport hands the core only frames signed with the network secret, counts the proxy as answering only for
 * those, and without a usable secret never opens the channel.
 */
class PluginMessagingTransportTest {

    private static final String CHANNEL = "uxmessentials:bus_v1";
    private static final Duration WINDOW = Duration.ofSeconds(90);
    private static final long START = 1_700_000_000_000L;
    private static final String SECRET = "0123456789abcdef0123456789abcdef-network";
    private static final byte[] FRAME = NetworkMessageCodec.encode(new ServerPing("survival-1", START, 7L));
    private static final int QUEUE = 16;

    private long now = START;
    private final RecordingLogger log = new RecordingLogger();
    private final FakeChannel pluginChannel = new FakeChannel();
    private final List<byte[]> delivered = new ArrayList<>();
    private final PluginMessagingTransport transport = transport(pluginChannel, SECRET);

    private PluginMessagingTransport transport(FakeChannel channel, String secret) {
        return transport(channel, secret, new ImmediateScheduler());
    }

    private PluginMessagingTransport transport(FakeChannel channel, String secret, Scheduler scheduler) {
        return new PluginMessagingTransport(
                channel, scheduler, CHANNEL, QUEUE, WINDOW, BusSecret.of(secret), log, () -> now);
    }

    /** What the frames the carrier sent say, as a peer decodes them. */
    private List<NetworkMessage> sentMessages() {
        FakeChannel peerChannel = new FakeChannel();
        PluginMessagingTransport peer = transport(peerChannel, SECRET);
        List<byte[]> peerDelivered = new ArrayList<>();
        peer.start(peerDelivered::add);
        pluginChannel.sent.forEach(bytes -> peer.received(CHANNEL, bytes));
        return peerDelivered.stream().map(NetworkMessageCodec::decode).toList();
    }

    private static byte[] home(int index) {
        return NetworkMessageCodec.encode(new HomeChanged("survival-1", new UUID(0L, index)));
    }

    private static byte[] ping(long sentAt) {
        return NetworkMessageCodec.encode(new ServerPing("survival-1", sentAt, 7L));
    }

    /** What another backend with {@code secret} puts on the wire for {@code frame}. */
    private byte[] sentByPeer(String secret, byte[] frame) {
        FakeChannel peerChannel = new FakeChannel();
        peerChannel.online = true;
        PluginMessagingTransport peer = transport(peerChannel, secret);
        peer.start(bytes -> {});
        peer.send(frame);
        return peerChannel.sent.getFirst();
    }

    @Test
    void aFrameBeforeStartOrOnAnotherChannelIsNotAnAnswer() {
        byte[] signed = sentByPeer(SECRET, FRAME);
        transport.received(CHANNEL, signed);
        transport.start(delivered::add);
        transport.received("other:channel", signed);

        assertThat(transport.link().lastAnswerMillis()).isEmpty();
        assertThat(transport.healthy()).isFalse();
        assertThat(delivered).isEmpty();
    }

    @Test
    void aFrameSignedByAPeerReachesTheCoreAsSentAndIsAnAnswerUntilTheWindowPasses() {
        transport.start(delivered::add);
        transport.received(CHANNEL, sentByPeer(SECRET, FRAME));

        assertThat(transport.link().lastAnswerMillis()).hasValue(START);
        assertThat(delivered).containsExactly(FRAME);
        now = START + WINDOW.toMillis();
        assertThat(transport.healthy()).isTrue();
        now = START + WINDOW.toMillis() + 1;
        assertThat(transport.healthy()).isFalse();
    }

    @Test
    void aFrameNotSignedWithThisSecretNeverReachesTheCoreAndIsNotAnAnswer() {
        transport.start(delivered::add);

        transport.received(CHANNEL, FRAME);
        transport.received(CHANNEL, sentByPeer("another-secret-of-at-least-32-characters", FRAME));
        byte[] tampered = sentByPeer(SECRET, FRAME);
        tampered[3] ^= 1;
        transport.received(CHANNEL, tampered);

        assertThat(delivered).isEmpty();
        assertThat(transport.link().lastAnswerMillis()).isEmpty();
        assertThat(transport.link().refusedFrames()).isEqualTo(3);
        assertThat(transport.link().lastRefusedMillis()).hasValue(START);
        assertThat(log.lines).singleElement().asString().contains("network.bus.secret");
    }

    @Test
    void aSignedFrameReplayedOutsideTheClockToleranceIsRefused() {
        transport.start(delivered::add);
        byte[] captured = sentByPeer(SECRET, FRAME);

        now = START + FrameSeal.CLOCK_TOLERANCE.toMillis();
        transport.received(CHANNEL, captured);
        now = START + FrameSeal.CLOCK_TOLERANCE.toMillis() + 1;
        transport.received(CHANNEL, captured);

        assertThat(delivered).containsExactly(FRAME);
        assertThat(transport.link().refusedFrames()).isEqualTo(1);
        assertThat(log.lines).singleElement().asString().contains("clock");
    }

    @Test
    void theProxyRelaysASignedFrameByItsHeaderAndAnOlderDecoderReadsIt() {
        byte[] signed = sentByPeer(SECRET, FRAME);

        assertThat(NetworkMessageCodec.readOrigin(signed)).isEqualTo("survival-1");
        assertThat(NetworkMessageCodec.isServerPing(signed)).isTrue();
        assertThat(NetworkMessageCodec.decode(signed)).isEqualTo(NetworkMessageCodec.decode(FRAME));
    }

    @Test
    void withoutAUsableSecretTheChannelStaysClosedAndNothingIsSent() {
        for (String secret : List.of("", "   ", "short-secret")) {
            FakeChannel channel = new FakeChannel();
            channel.online = true;
            PluginMessagingTransport unsigned = transport(channel, secret);

            unsigned.start(delivered::add);
            unsigned.send(FRAME);
            unsigned.received(CHANNEL, sentByPeer(SECRET, FRAME));

            assertThat(channel.open).isFalse();
            assertThat(channel.sent).isEmpty();
            assertThat(delivered).isEmpty();
            assertThat(unsigned.link().signing()).isFalse();
            assertThat(unsigned.link().registered()).isFalse();
        }
    }

    @Test
    void stoppingForgetsTheLastAnswerAndRefusalsAndUnregistersTheChannel() {
        transport.start(delivered::add);
        transport.received(CHANNEL, sentByPeer(SECRET, FRAME));
        transport.received(CHANNEL, FRAME);

        transport.stop();
        transport.received(CHANNEL, sentByPeer(SECRET, FRAME));

        assertThat(transport.link())
                .isEqualTo(new BusHealth.ProxyLink(true, false, OptionalLong.empty(), false, 0, OptionalLong.empty()));
        assertThat(pluginChannel.open).isFalse();
        assertThat(delivered).containsExactly(FRAME);
    }

    @Test
    void aSendWithNobodyOnlineIsReportedAndTheNextPlayerCarriesTheQueue() {
        transport.start(delivered::add);

        byte[] first = home(0);
        transport.send(first);
        assertThat(transport.link().waitingForPlayer()).isTrue();

        pluginChannel.online = true;
        byte[] second = NetworkMessageCodec.encode(new ServerPing("survival-1", START + 1, 7L));
        transport.send(second);
        assertThat(transport.link().waitingForPlayer()).isFalse();
        assertThat(pluginChannel.sent).hasSize(2);

        FakeChannel peerChannel = new FakeChannel();
        PluginMessagingTransport peer = transport(peerChannel, SECRET);
        List<byte[]> peerDelivered = new ArrayList<>();
        peer.start(peerDelivered::add);
        pluginChannel.sent.forEach(bytes -> peer.received(CHANNEL, bytes));
        assertThat(peerDelivered).containsExactly(first, second);
    }

    @Test
    void aFrameOverTheLimitOnceSignedIsDroppedAndOneAtTheLimitLeaves() {
        transport.start(delivered::add);
        pluginChannel.online = true;
        byte[] fits = Arrays.copyOf(FRAME, BusChannel.MAX_FRAME_BYTES - FrameSeal.TRAILER_BYTES);
        byte[] tooLarge = Arrays.copyOf(FRAME, fits.length + 1);

        transport.send(tooLarge);
        transport.send(fits);

        assertThat(pluginChannel.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent).hasSize(BusChannel.MAX_FRAME_BYTES));
        assertThat(log.lines).singleElement().asString().contains(String.valueOf(BusChannel.MAX_FRAME_BYTES + 1));
    }

    @Test
    void aBurstOverTheQueueDropsOrdinaryFramesKeepsBansAndBalancesAndIsReportedOncePerMinute() {
        transport.start(delivered::add);
        BanChanged ban = new BanChanged("survival-1", new UUID(1L, 1L));
        BalanceChanged balance = new BalanceChanged("survival-1", new UUID(1L, 2L), "coins");
        transport.send(NetworkMessageCodec.encode(ban));
        transport.send(NetworkMessageCodec.encode(balance));
        for (int index = 0; index < 20; index++) {
            transport.send(home(index));
        }

        assertThat(log.lines)
                .singleElement()
                .asString()
                .contains("HOME_CHANGED", "of " + QUEUE + " frames", "1 dropped so far");

        now = START + DroppedFrameReport.INTERVAL_MILLIS;
        pluginChannel.online = true;
        transport.send(home(20));

        assertThat(log.lines).hasSize(2).last().asString().contains("7 dropped so far");
        List<NetworkMessage> sent = sentMessages();
        assertThat(sent).hasSize(QUEUE).startsWith(ban, balance);
        assertThat(sent.subList(2, QUEUE))
                .extracting(message -> ((HomeChanged) message).owner())
                .containsExactly(IntStream.rangeClosed(7, 20)
                        .mapToObj(index -> new UUID(0L, index))
                        .toArray(UUID[]::new));
    }

    @Test
    void aChangeArrivingAtAQueueFullOfProtectedFramesIsTheOneDropped() {
        transport.start(delivered::add);
        for (int index = 0; index < QUEUE; index++) {
            transport.send(NetworkMessageCodec.encode(new BanChanged("survival-1", new UUID(1L, index))));
        }

        transport.send(home(0));
        pluginChannel.online = true;
        transport.send(NetworkMessageCodec.encode(new BanChanged("survival-1", new UUID(1L, QUEUE))));

        List<NetworkMessage> sent = sentMessages();
        assertThat(sent).hasSize(QUEUE).allMatch(BanChanged.class::isInstance);
        assertThat(((BanChanged) sent.getLast()).target()).isEqualTo(new UUID(1L, QUEUE));
        assertThat(log.lines).hasSize(1).first().asString().contains("HOME_CHANGED");
    }

    @Test
    void onlyTheNewestHeartbeatWaitsAndAHeartbeatIsNeverReportedAsDropped() {
        transport.start(delivered::add);
        transport.send(ping(START));
        transport.send(home(0));
        for (int index = 1; index < QUEUE + 4; index++) {
            transport.send(ping(START + index));
        }

        pluginChannel.online = true;
        transport.send(ping(START + 100));

        assertThat(sentMessages())
                .containsExactly(
                        new HomeChanged("survival-1", new UUID(0L, 0)), new ServerPing("survival-1", START + 100, 7L));
        assertThat(log.lines).isEmpty();
    }

    @Test
    void framesSentBeforeTheFlushRunsShareOneFlush() {
        ManualScheduler scheduler = new ManualScheduler();
        PluginMessagingTransport batched = transport(pluginChannel, SECRET, scheduler);
        batched.start(delivered::add);
        pluginChannel.online = true;

        for (int index = 0; index < 5; index++) {
            batched.send(home(index));
        }
        assertThat(scheduler.global).hasSize(1);
        scheduler.runGlobal();
        assertThat(pluginChannel.sent).hasSize(5);

        batched.send(home(5));
        assertThat(scheduler.global).hasSize(1);
        scheduler.runGlobal();
        assertThat(pluginChannel.sent).hasSize(6);
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

    /** Keeps global tasks until the test runs them, as a busy global thread would. */
    private static final class ManualScheduler implements Scheduler {

        final List<Runnable> global = new ArrayList<>();

        void runGlobal() {
            List<Runnable> due = List.copyOf(global);
            global.clear();
            due.forEach(Runnable::run);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public void onGlobal(Runnable task) {
            global.add(task);
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

    /** Keeps each warning with its arguments filled in. */
    private static final class RecordingLogger implements Logger {

        final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            record(message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            record(message, args);
        }

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message);
        }

        @Override
        public void debug(String message, Object... args) {
            record(message, args);
        }

        private void record(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            lines.add(line);
        }
    }
}

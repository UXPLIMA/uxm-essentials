package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link VanishConnectionMessenger}: a vanishing player fakes a quit line to viewers who
 * cannot see them, never to themselves, sends the staff variant to a see-permitted viewer, and stays silent when the
 * {@code fake-join-quit} gate is off. A synchronous inline scheduler runs the global fan-out deterministically.
 */
class VanishConnectionMessengerTest {

    private static VanishConfig config(boolean fakeJoinQuit, String publicLine, String staffLine) {
        return new VanishConfig(
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                fakeJoinQuit,
                true,
                false,
                publicLine,
                publicLine,
                staffLine,
                staffLine);
    }

    private ServerMock server;
    private RecordingSink sink;
    private BukkitVanishLevelResolver levels;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        sink = new RecordingSink();
        levels = new BukkitVanishLevelResolver();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aVanishFakesAQuitToOthersButNotToTheVanishingPlayer() {
        PlayerMock vanisher = server.addPlayer("Vanisher");
        PlayerMock observer = server.addPlayer("Observer");
        VanishConnectionMessenger messenger = messenger(config(true, "{player} left the game", ""));

        messenger.announceVanish(BukkitRefs.toRef(vanisher));

        assertThat(sink.received(observer.getUniqueId())).containsExactly("Vanisher left the game");
        assertThat(sink.received(vanisher.getUniqueId())).isEmpty();
    }

    @Test
    void aReappearFakesAJoinToOthers() {
        PlayerMock vanisher = server.addPlayer("Vanisher");
        PlayerMock observer = server.addPlayer("Observer");
        VanishConnectionMessenger messenger = messenger(config(true, "{player} joined the game", ""));

        messenger.announceReappear(BukkitRefs.toRef(vanisher));

        assertThat(sink.received(observer.getUniqueId())).containsExactly("Vanisher joined the game");
    }

    @Test
    void aSeePermittedViewerGetsTheStaffVariant() {
        PlayerMock vanisher = server.addPlayer("Vanisher");
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see", true);
        VanishConnectionMessenger messenger = messenger(config(true, "{player} left the game", "{player} vanished"));

        messenger.announceVanish(BukkitRefs.toRef(vanisher));

        assertThat(sink.received(staff.getUniqueId())).containsExactly("Vanisher vanished");
    }

    @Test
    void aBlankStaffVariantSendsSeeViewersNothing() {
        PlayerMock vanisher = server.addPlayer("Vanisher");
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see", true);
        VanishConnectionMessenger messenger = messenger(config(true, "{player} left the game", ""));

        messenger.announceVanish(BukkitRefs.toRef(vanisher));

        assertThat(sink.received(staff.getUniqueId())).isEmpty();
    }

    @Test
    void nothingIsBroadcastWhenFakeJoinQuitIsOff() {
        PlayerMock vanisher = server.addPlayer("Vanisher");
        PlayerMock observer = server.addPlayer("Observer");
        VanishConnectionMessenger messenger = messenger(config(false, "{player} left the game", ""));

        messenger.announceVanish(BukkitRefs.toRef(vanisher));

        assertThat(sink.received(observer.getUniqueId())).isEmpty();
    }

    private VanishConnectionMessenger messenger(VanishConfig config) {
        return new VanishConnectionMessenger(new InlineScheduler(), sink, levels, config);
    }

    /** Records every delivery per viewer so the fan-out is assertable. */
    private static final class RecordingSink implements MessageSink {
        private final ConcurrentHashMap<UUID, List<String>> delivered = new ConcurrentHashMap<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.computeIfAbsent(viewer.uuid(), id -> new ArrayList<>()).add(renderedText);
        }

        List<String> received(UUID viewer) {
            return delivered.getOrDefault(viewer, List.of());
        }
    }

    /** A scheduler that runs every task inline so the global fan-out fires at once. */
    private static final class InlineScheduler implements Scheduler {
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

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}

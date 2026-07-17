package com.uxplima.uxmessentials.vanish.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishLifecycleListener;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the vanish adapters against a real (mock) Bukkit server: the {@link ToggleVanish} use case
 * over the in-memory {@link InMemoryVanishStore} authority and the {@link BukkitVanishView}, driving Bukkit's
 * {@code hidePlayer}/{@code showPlayer} graph so a vanished caller disappears from a viewer who cannot see them and
 * reappears on unvanish; a see-node holder is exempt. The {@link VanishLifecycleListener} drops a quitting player's
 * vanish state. The {@link Scheduler} is a synchronous inline fake so the entity-thread hop runs deterministically.
 */
class VanishAdapterTest {

    private ServerMock server;
    private InMemoryVanishStore store;
    private ToggleVanish toggleVanish;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        InlineScheduler scheduler = new InlineScheduler();
        store = new InMemoryVanishStore();
        BukkitVanishView view = new BukkitVanishView(MockBukkit.createMockPlugin(), scheduler);
        VanishNotifier notifier = new VanishNotifier(new KeyMessages(), new DiscardingSink());
        toggleVanish = new ToggleVanish(store, view, notifier);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void vanishHidesTheCallerFromANonSeeViewerThenShowsAgain() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        toggleVanish.toggle(BukkitRefs.toRef(alice));
        assertThat(store.isVanished(alice.getUniqueId())).isTrue();
        assertThat(bob.canSee(alice)).isFalse(); // bob no longer sees the vanished alice

        toggleVanish.toggle(BukkitRefs.toRef(alice));
        assertThat(store.isVanished(alice.getUniqueId())).isFalse();
        assertThat(bob.canSee(alice)).isTrue(); // revealed again
    }

    @Test
    void aSeePermittedViewerStillSeesAVanishedPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see", true);

        toggleVanish.toggle(BukkitRefs.toRef(alice));

        assertThat(staff.canSee(alice)).isTrue(); // a see-node holder is exempt from the hide
    }

    @Test
    void theLifecycleListenerForgetsVanishOnQuit() {
        PlayerMock alice = server.addPlayer("Alice");
        toggleVanish.toggle(BukkitRefs.toRef(alice));
        assertThat(store.isVanished(alice.getUniqueId())).isTrue();
        VanishLifecycleListener listener = new VanishLifecycleListener(store, new NoopView(), server);
        PlayerQuitEvent quit = new PlayerQuitEvent(
                alice, net.kyori.adventure.text.Component.text("Alice left"), PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(quit);

        assertThat(quit.quitMessage()).isNull(); // a vanished player's quit line is suppressed
        assertThat(store.isVanished(alice.getUniqueId())).isFalse(); // vanish state dropped on quit
    }

    /** A view that records nothing — the lifecycle test only asserts store state and the quit message. */
    private static final class NoopView implements com.uxplima.uxmessentials.vanish.application.port.VanishView {
        @Override
        public void hide(PlayerRef who) {}

        @Override
        public void reveal(PlayerRef who) {}
    }

    /** A scheduler that runs every task inline so the entity-thread hop fires at once. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class DiscardingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: delivery is not under test here
        }
    }
}

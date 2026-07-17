package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link VanishActionBar}: {@link VanishActionBar#refresh()} sends the indicator to every
 * currently-vanished online player, {@link VanishActionBar#clear(PlayerRef)} wipes it on reappear, and both are a no-op
 * when the {@code action-bar} gate is off. A synchronous inline scheduler runs the global-then-entity hops at once.
 */
class VanishActionBarTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static VanishConfig config(boolean actionBar) {
        return new VanishConfig(
                true, true, false, true, true, true, true, true, true, actionBar, false, "", "", "", "", false);
    }

    private ServerMock server;
    private InMemoryVanishStore store;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        store = new InMemoryVanishStore();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void refreshShowsTheIndicatorToAVanishedPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);

        actionBar(config(true)).refresh();

        assertThat(PLAIN.serialize(alice.nextActionBar())).contains("vanish.actionbar");
    }

    @Test
    void refreshSkipsAVisiblePlayer() {
        PlayerMock bob = server.addPlayer("Bob"); // not vanished

        actionBar(config(true)).refresh();

        assertThat(bob.nextActionBar()).isNull();
    }

    @Test
    void clearWipesTheIndicatorOnReappear() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);
        VanishActionBar actionBar = actionBar(config(true));
        actionBar.refresh();
        assertThat(PLAIN.serialize(alice.nextActionBar())).contains("vanish.actionbar"); // shown first

        store.reveal(alice.getUniqueId());
        actionBar.clear(BukkitRefs.toRef(alice));

        assertThat(PLAIN.serialize(alice.nextActionBar())).isEmpty(); // wiped
    }

    @Test
    void refreshIsANoOpWhenTheIndicatorIsDisabled() {
        PlayerMock alice = server.addPlayer("Alice");
        store.vanish(alice.getUniqueId(), VanishLevel.DEFAULT);

        actionBar(config(false)).refresh();

        assertThat(alice.nextActionBar()).isNull();
    }

    private VanishActionBar actionBar(VanishConfig config) {
        return new VanishActionBar(server, new InlineScheduler(), new KeyMessages(), store, config);
    }

    /** Echoes the resolved key so the rendered action bar carries an assertable token. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** A scheduler that runs every task inline so the global-then-entity hops fire at once. */
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

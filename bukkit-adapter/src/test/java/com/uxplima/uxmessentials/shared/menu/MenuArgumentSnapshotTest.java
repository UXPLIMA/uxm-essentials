package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * An open crosses two hops: the caller's thread hands the arguments in, a list resolution runs off-thread, and the
 * window is built back on the viewer's entity thread. The arguments are read again at that second point, so the
 * copy taken in {@code Menus.openInternal} is what a caller reusing its own map is held off by.
 *
 * <p>{@code MenuContext}, {@code LastOpen} and {@code OpenMenuInfo} each copy at their own door, which makes that
 * one look like a fourth redundant copy. It is not: those three run after the hop and would copy the mutation
 * rather than prevent it. A scheduler that runs entity work inline never gives the caller a turn in the gap, so
 * the redundancy is what every other test in this tree would report. This one queues the entity hop and writes to
 * the caller's map inside it.
 */
class MenuArgumentSnapshotTest {

    private static final String HOCON = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "%argument_who%" } }
            """;

    private ServerMock server;
    private PlayerMock player;
    private Menus menus;
    private QueueingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");
        scheduler = new QueueingScheduler();
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        menus = engine.menus();
        menus.registerSpec("greet", new MenuSpecLoader().parse(HOCON));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theWindowIsBuiltFromTheArgumentsAsTheyWereWhenOpenWasCalled() {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("who", "Notch");
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());

        menus.open(viewer, "greet", null, 0, arguments);
        arguments.put("who", "Herobrine");
        scheduler.drain();

        assertThat(name(player.getOpenInventory().getTopInventory().getItem(0))).isEqualTo("Notch");
    }

    @Test
    void theRecordedOpenCarriesTheSameSnapshot() {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("who", "Notch");
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());

        menus.open(viewer, "greet", null, 0, arguments);
        arguments.put("who", "Herobrine");
        scheduler.drain();

        assertThat(menus.currentMenu(viewer.uuid()))
                .hasValueSatisfying(info -> assertThat(info.arguments()).containsEntry("who", "Notch"));
    }

    private static String name(ItemStack item) {
        assertThat(item).isNotNull();
        return PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    /** The catalog stand-in: a key resolves to itself, which is enough for a spec whose text is a placeholder. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs async work inline and queues every entity hop, so a test can act in the gap the open leaves open. */
    private static final class QueueingScheduler implements Scheduler {
        private final Deque<Runnable> queued = new ArrayDeque<>();

        void drain() {
            while (!queued.isEmpty()) {
                queued.poll().run();
            }
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
            queued.add(task);
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}

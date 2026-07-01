package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.NumericSpatialConditions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * The proof that a valued spatial condition written as an {@code id:value} token in a config gates an item, end to end
 * through the real {@link MenuSpecLoader}, {@link MenuBindings#validate} and {@link MenuRenderer}. A spec whose item is
 * gated on {@code world:world} renders while the viewer stands in the world named {@code world} and is hidden once the
 * viewer moves to another world. The parser leaves the whole token intact (it is registry-blind); the runtime's
 * registry-aware split re-keys it to the head {@code world}, which is exactly what lets it both pass validation and
 * evaluate against the viewer's live world at render time.
 */
class SpatialConditionReachabilityGoldenTest {

    private static final String HOCON = """
            rows = 1
            items {
              gated { slot = 0, material = DIAMOND, name = "x", view = ["world:world"] }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private World world;
    private World other;
    private PlayerMock player;
    private MenuBindings bindings;
    private Menus menus;
    private MenuSpec spec;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        other = server.addSimpleWorld("other");
        player = server.addPlayer("Viewer");

        Logger log = new NoopLogger();
        bindings = new MenuBindings();
        NumericSpatialConditions.register(bindings, log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        spec = loader.parse(HOCON);
        menus.registerSpec("gated", spec);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theValuedViewConditionPassesStartupValidation() {
        // The registry-aware split makes world:world count as known because its head world is registered.
        assertThat(bindings.validate(List.of(spec))).isEmpty();
    }

    @Test
    void theItemRendersWhenTheViewerIsInTheNamedWorld() {
        player.teleport(new Location(world, 0, 64, 0));

        open();

        assertThat(topInventory().getItem(0))
                .as("the gated item is visible while the viewer stands in the named world")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void theItemIsHiddenWhenTheViewerIsInAnotherWorld() {
        player.teleport(new Location(other, 0, 64, 0));

        open();

        assertThat(topInventory().getItem(0))
                .as("moving to another world fails the view condition, so the item is hidden")
                .isNull();
    }

    private void open() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "gated", null);
    }

    private Inventory topInventory() {
        return player.getOpenInventory().getTopInventory();
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class SyncScheduler implements Scheduler {
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}

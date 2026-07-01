package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuSwapListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
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
 * MockBukkit coverage of {@link MenuSwapListener} through the real event bus. A configured swap menu cancels the F-key
 * swap and opens that menu; no configured menu (or one naming an unregistered id) leaves the swap alone and opens
 * nothing.
 */
class MenuSwapListenerTest {

    private static final String HUB_HOCON = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "hub" } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private final AtomicReference<Optional<String>> swapMenu = new AtomicReference<>(Optional.empty());

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, new SyncScheduler(), new ListSourceRegistry());
        menus.registerSpec("hub", new MenuSpecLoader().parse(HUB_HOCON));
        Supplier<List<String>> names = () -> List.of("hub");
        MenuSwapListener listener = new MenuSwapListener(menus, swapMenu::get, names);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aConfiguredSwapMenuOpensAndCancelsTheSwap() {
        swapMenu.set(Optional.of("hub"));

        PlayerSwapHandItemsEvent event = fireSwap();

        assertThat(event.isCancelled())
                .as("the F swap is cancelled so the menu opens instead")
                .isTrue();
        assertThat(menuIsOpen()).isTrue();
    }

    @Test
    void noConfiguredSwapMenuLeavesTheSwapAloneAndOpensNothing() {
        swapMenu.set(Optional.empty());

        PlayerSwapHandItemsEvent event = fireSwap();

        assertThat(event.isCancelled())
                .as("with no swap menu the vanilla swap happens")
                .isFalse();
        assertThat(menuIsOpen()).isFalse();
    }

    @Test
    void aSwapMenuNamingAnUnregisteredIdLeavesTheSwapAlone() {
        swapMenu.set(Optional.of("ghost"));

        PlayerSwapHandItemsEvent event = fireSwap();

        assertThat(event.isCancelled()).isFalse();
        assertThat(menuIsOpen()).isFalse();
    }

    private boolean menuIsOpen() {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    private PlayerSwapHandItemsEvent fireSwap() {
        PlayerSwapHandItemsEvent event =
                new PlayerSwapHandItemsEvent(player, new ItemStack(Material.AIR), new ItemStack(Material.AIR));
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
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
    }
}

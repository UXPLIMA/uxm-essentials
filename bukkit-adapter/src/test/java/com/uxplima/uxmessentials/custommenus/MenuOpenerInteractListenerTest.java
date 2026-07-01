package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuOpenerInteractListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerItems;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec.GiveOnJoin;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
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
 * MockBukkit coverage of open-with-item through the real {@link MenuOpenerInteractListener}. A right-click with the
 * tagged opener opens the target menu and cancels the interaction; a plain item, an opener whose menu is no longer
 * registered, and a left-click all do nothing.
 */
class MenuOpenerInteractListenerTest {

    private static final String HUB_HOCON = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private ServerMock server;
    private Menus menus;
    private OpenerItems openerItems;
    private MenuOpenerInteractListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, new SyncScheduler(), new ListSourceRegistry());
        MenuSpec spec = new MenuSpecLoader().parse(HUB_HOCON);
        menus.registerSpec("hub", spec);
        openerItems = new OpenerItems(MockBukkit.createMockPlugin());
        listener = new MenuOpenerInteractListener(menus, () -> List.of("hub"), openerItems);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rightClickingTheOpenerOpensTheMenuAndCancelsTheInteraction() {
        PlayerMock player = server.addPlayer("Steve");
        ItemStack opener = openerItems.build(opener("hub"));
        PlayerInteractEvent event = rightClick(player, opener);

        listener.onInteract(event);

        assertThat(openMenuIdFor(player)).contains("hub");
        // setCancelled(true) denies the item interaction; asserted via the non-deprecated result accessor.
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void aPlainUntaggedItemDoesNothing() {
        PlayerMock player = server.addPlayer("Steve");
        PlayerInteractEvent event = rightClick(player, new ItemStack(Material.COMPASS));

        listener.onInteract(event);

        assertThat(openMenuIdFor(player)).isEmpty();
        assertThat(event.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void anOpenerForANoLongerRegisteredMenuDoesNothingWithoutThrowing() {
        PlayerMock player = server.addPlayer("Steve");
        ItemStack opener = openerItems.build(opener("ghost"));
        PlayerInteractEvent event = rightClick(player, opener);

        assertThatCode(() -> listener.onInteract(event)).doesNotThrowAnyException();
        assertThat(openMenuIdFor(player)).isEmpty();
        assertThat(event.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void aLeftClickWithTheOpenerDoesNothing() {
        PlayerMock player = server.addPlayer("Steve");
        ItemStack opener = openerItems.build(opener("hub"));
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_AIR, opener, null, BlockFace.SELF, EquipmentSlot.HAND);

        listener.onInteract(event);

        assertThat(openMenuIdFor(player)).isEmpty();
    }

    private static PlayerInteractEvent rightClick(PlayerMock player, ItemStack item) {
        player.getInventory().setItemInMainHand(item);
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, item, null, BlockFace.SELF, EquipmentSlot.HAND);
    }

    private static OpenerSpec opener(String menu) {
        return new OpenerSpec(
                menu, new OpenerSpec.Item(Material.COMPASS, "<gold>Menu", List.of()), 4, GiveOnJoin.NEVER);
    }

    private static Optional<String> openMenuIdFor(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder holder
                ? Optional.of(holder.specId())
                : Optional.empty();
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

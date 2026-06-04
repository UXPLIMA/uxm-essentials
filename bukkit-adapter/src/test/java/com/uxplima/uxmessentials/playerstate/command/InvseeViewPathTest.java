package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeListener;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView;
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
 * MockBukkit coverage of the managed {@code /invsee} view: the menu mirrors the target's full inventory — main
 * slots, armour, and offhand — into a private 54-slot copy; the viewer's edit to that copy is reconciled back
 * onto the target on close, and nothing is duplicated because the viewer never touches the target's live
 * {@code PlayerInventory} while the menu is open.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open and the close write-back run inline, and the
 * close is dispatched as a real {@link InventoryCloseEvent} through the same {@link InvseeListener} a live close
 * routes through. The conservation assertion is the dupe guard: the total item count across the target plus the
 * menu copy is the same before and after the edit, so an item moved inside the menu is moved, not cloned.
 */
class InvseeViewPathTest {

    private ServerMock server;
    private Plugin plugin;
    private InvseeView view;
    private InvseeListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        view = new InvseeView(new KeyMessages(), new SyncScheduler());
        listener = new InvseeListener(view);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mirrorsMainArmourAndOffhandIntoTheManagedMenu() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        target.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        target.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));

        view.open(ref(viewer), ref(target));

        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getSize()).isEqualTo(54);
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(menu.getItem(39).getType()).isEqualTo(Material.DIAMOND_HELMET); // helmet slot
        assertThat(menu.getItem(40).getType()).isEqualTo(Material.SHIELD); // offhand slot
        assertThat(menu.getItem(53).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE); // filler
    }

    @Test
    void editInTheMenuIsWrittenBackOnCloseWithoutDuplicating() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));
        int before = totalDiamonds(target);
        view.open(ref(viewer), ref(target));
        Inventory menu = viewer.getOpenInventory().getTopInventory();

        // The viewer relocates the stack inside the private menu copy from slot 0 to slot 7, then closes.
        ItemStack moved = menu.getItem(0);
        menu.setItem(0, null);
        menu.setItem(7, moved);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        // The move landed on the target (slot 0 emptied, slot 7 holds the stack) and nothing was duplicated:
        // the close write-back overwrites the target's slots from the menu copy, it never adds to them.
        assertThat(target.getInventory().getItem(0)).isNull();
        assertThat(target.getInventory().getItem(7)).isNotNull();
        assertThat(target.getInventory().getItem(7).getType()).isEqualTo(Material.DIAMOND);
        assertThat(before).isEqualTo(5);
        assertThat(totalDiamonds(target)).isEqualTo(before); // conserved: 5 in, 5 out — no 2x
    }

    @Test
    void selfInvseeOpensAndWritesBackToYourself() {
        PlayerMock self = grantModify(server.addPlayer("Solo"));
        self.getInventory().setItem(2, new ItemStack(Material.EMERALD, 3));

        view.open(ref(self), ref(self));
        Inventory menu = self.getOpenInventory().getTopInventory();
        menu.setItem(2, new ItemStack(Material.EMERALD, 9)); // grow the stack inside the menu
        server.getPluginManager().callEvent(new InventoryCloseEvent(self.getOpenInventory()));

        assertThat(self.getInventory().getItem(2)).isNotNull();
        assertThat(self.getInventory().getItem(2).getAmount()).isEqualTo(9);
    }

    @Test
    void withoutModifyTheMenuStillOpensAndNeverDuplicatesOnClose() {
        PlayerMock target = server.addPlayer("Target");
        target.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
        PlayerMock viewer = server.addPlayer("Watcher"); // no modify node

        view.open(ref(viewer), ref(target));
        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getSize()).isEqualTo(54);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        assertThat(target.getInventory().getItem(0).getAmount()).isEqualTo(4);
    }

    private PlayerMock grantModify(PlayerMock player) {
        player.addAttachment(plugin, "uxmessentials.invsee.modify", true);
        return player;
    }

    private static int totalDiamonds(PlayerMock player) {
        return countDiamonds(player.getInventory().getContents());
    }

    private static int countDiamonds(ItemStack[] contents) {
        return Arrays.stream(contents)
                .filter(stack -> stack != null && stack.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** Resolves a title key to its plain key string; MiniMessage parses it as literal text in the view. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled task inline so the entity-bound open and the close write-back complete in-test. */
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

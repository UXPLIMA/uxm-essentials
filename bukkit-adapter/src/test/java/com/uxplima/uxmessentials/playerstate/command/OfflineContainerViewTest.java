package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.MirrorWindow;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.OfflineContainerView;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflineInventory;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the offline {@code /invsee} and {@code /endersee} view. The disk layer is a fake
 * {@link OfflinePlayerStorage} (the real one is Mojang-mapped NMS, exercised on a dev server), so the test pins
 * exactly the adapter logic on top of it: a snapshot is seeded into the right managed-menu slots, an edit is
 * reconciled back to disk on close, a view-only viewer writes nothing, and an edit closes onto the live player
 * instead of disk when the subject has logged in while the menu was open.
 */
class OfflineContainerViewTest {

    private ServerMock server;
    private Plugin plugin;
    private FakeOfflineStorage storage;
    private OfflineContainerView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        storage = new FakeOfflineStorage();
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        MirrorWindow window = new MirrorWindow(
                new KeyMessages(),
                engine.menus(),
                new SyncScheduler(),
                java.nio.file.Path.of("no-such-data-folder"),
                TestMenuEngine.SILENT_LOG);
        window.register(engine.bindings());
        engine.installListener(plugin);
        view = new OfflineContainerView(new SyncScheduler(), storage, window);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void offlineInvseeSeedsTheSnapshotIntoTheManagedMenu() {
        storage.snapshot = inventorySnapshot();
        PlayerRef subject = offlineRef();
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));

        view.openInventory(ref(viewer), subject);

        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getSize()).isEqualTo(54);
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(menu.getItem(39).getType()).isEqualTo(Material.DIAMOND_HELMET); // armour: head
        assertThat(menu.getItem(40).getType()).isEqualTo(Material.SHIELD); // offhand
        assertThat(menu.getItem(53).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE); // filler
    }

    @Test
    void offlineInvseeEditIsWrittenBackToDiskOnClose() {
        storage.snapshot = inventorySnapshot();
        PlayerRef subject = offlineRef();
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));
        view.openInventory(ref(viewer), subject);
        Inventory menu = viewer.getOpenInventory().getTopInventory();

        ItemStack moved = menu.getItem(0);
        menu.setItem(0, null);
        menu.setItem(7, moved);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        assertThat(storage.savedInventory).isNotNull();
        assertThat(storage.savedInventory[0]).isNull();
        assertThat(storage.savedInventory[7]).isNotNull();
        assertThat(storage.savedInventory[7].getType()).isEqualTo(Material.DIAMOND);
        assertThat(storage.savedEnder).isNull();
    }

    @Test
    void offlineInvseeWithoutModifyWritesNothingOnClose() {
        storage.snapshot = inventorySnapshot();
        PlayerRef subject = offlineRef();
        PlayerMock viewer = server.addPlayer("Watcher"); // no modify node

        view.openInventory(ref(viewer), subject);
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        assertThat(storage.savedInventory).isNull();
    }

    @Test
    void editClosesOntoTheLivePlayerWhenTheySignBackInMidEdit() {
        storage.snapshot = inventorySnapshot();
        PlayerMock subject = server.addPlayer("Bob"); // online at close → live apply, not disk
        PlayerMock viewer = grantModify(server.addPlayer("Staff"));
        view.openInventory(ref(viewer), ref(subject));
        Inventory menu = viewer.getOpenInventory().getTopInventory();

        menu.setItem(0, new ItemStack(Material.EMERALD, 2));
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        assertThat(storage.savedInventory).isNull(); // never touched disk for an online subject
        assertThat(subject.getInventory().getItem(0)).isNotNull();
        assertThat(subject.getInventory().getItem(0).getType()).isEqualTo(Material.EMERALD);
        assertThat(subject.getInventory().getItem(0).getAmount()).isEqualTo(2);
    }

    @Test
    void offlineEnderseeSeedsAndWritesBackTheEnderChest() {
        storage.snapshot = enderSnapshot();
        PlayerRef subject = offlineRef();
        PlayerMock viewer = server.addPlayer("Staff");

        view.openEnderChest(ref(viewer), subject);
        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getSize()).isEqualTo(27);
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.ENDER_PEARL);

        menu.setItem(5, new ItemStack(Material.OBSIDIAN, 3));
        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        assertThat(storage.savedEnder).isNotNull();
        assertThat(storage.savedEnder[5]).isNotNull();
        assertThat(storage.savedEnder[5].getType()).isEqualTo(Material.OBSIDIAN);
        assertThat(storage.savedInventory).isNull();
    }

    private static OfflineInventory inventorySnapshot() {
        ItemStack[] slots = new ItemStack[OfflineInventory.SLOT_COUNT];
        slots[0] = new ItemStack(Material.DIAMOND, 5);
        slots[39] = new ItemStack(Material.DIAMOND_HELMET);
        slots[40] = new ItemStack(Material.SHIELD);
        return new OfflineInventory(slots, new ItemStack[OfflineInventory.ENDER_SIZE]);
    }

    private static OfflineInventory enderSnapshot() {
        ItemStack[] ender = new ItemStack[OfflineInventory.ENDER_SIZE];
        ender[0] = new ItemStack(Material.ENDER_PEARL, 16);
        return new OfflineInventory(new ItemStack[OfflineInventory.SLOT_COUNT], ender);
    }

    private PlayerMock grantModify(PlayerMock player) {
        player.addAttachment(plugin, "uxmessentials.invsee.modify", true);
        return player;
    }

    private static PlayerRef offlineRef() {
        return new PlayerRef(UUID.randomUUID(), "Ghost");
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** In-memory stand-in for the NMS disk layer: serves one snapshot and captures the write-back. */
    private static final class FakeOfflineStorage implements OfflinePlayerStorage {
        private OfflineInventory snapshot;
        private ItemStack[] savedInventory;
        private ItemStack[] savedEnder;

        @Override
        public boolean hasData(UUID uuid) {
            return snapshot != null;
        }

        @Override
        public Optional<OfflineInventory> load(UUID uuid) {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public void saveInventory(UUID uuid, ItemStack[] slots) {
            this.savedInventory = slots;
        }

        @Override
        public void saveEnderChest(UUID uuid, ItemStack[] ender) {
            this.savedEnder = ender;
        }
    }

    /** Resolves a title key to its plain key string; MiniMessage parses it as literal text in the view. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled task inline so the async load, entity-bound open, and close write-back complete. */
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

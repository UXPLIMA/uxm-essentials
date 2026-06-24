package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.EchoMessages;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingFreeze;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingTeleport;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingVanish;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.SilentSink;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.StaffKeySink;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineView;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffGadgetActions;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffModeListener;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffFollowService;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The gadget interaction listener: an air interact fires the air action (VANISH toggles, the target-needing
 * gadgets report no-target), a right-click on a player fires the player action (FREEZE/FOLLOW/COMPASS), a
 * non-gadget interaction is left alone, and a gadget item can never be dropped or swapped out.
 */
class StaffModeListenerTest {

    private ServerMock server;
    private Player player;
    private Player target;
    private Plugin plugin;
    private StaffGadgetItems gadgetItems;
    private StaffSettings settings;
    private RecordingVanish vanish;
    private RecordingFreeze freeze;
    private RecordingTeleport teleport;
    private RecordingInspector inspector;
    private StaffFollowService follow;
    private StaffKeySink keySink;
    private StaffModeListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        target = server.addPlayer("Bob");
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        gadgetItems = new StaffGadgetItems(plugin);
        settings = StaffAdapterFakes.defaultSettings();
        vanish = new RecordingVanish();
        freeze = new RecordingFreeze();
        teleport = new RecordingTeleport();
        inspector = new RecordingInspector();
        keySink = new StaffKeySink();
        follow = new StaffFollowService(
                server,
                new SyncScheduler(),
                StaffAdapterFakes.notifier(),
                new StaffAdapterFakes.NoopLogger(),
                id -> true,
                10);
        listener = new StaffModeListener(services(), gadgetItems, follow, actions());
    }

    @AfterEach
    void tearDown() {
        follow.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void rightClickingTheVanishGadgetTogglesVanishAndCancelsTheInteraction() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.VANISH));
        PlayerInteractEvent event = interact(gadget);

        listener.onInteract(event);

        assertThat(vanish.states).hasSize(1);
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void usingTheFreezeGadgetOnAirReportsNoTarget() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.FREEZE));

        listener.onInteract(interact(gadget));

        assertThat(freeze.toggled).isEmpty();
        assertThat(keySink.delivered).anyMatch(line -> line.startsWith(StaffMessageKey.STAFF_GADGET_NO_TARGET.key()));
    }

    @Test
    void usingTheCompassGadgetOnAirOpensTheNavigatorWithoutTeleporting() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.COMPASS));

        listener.onInteract(interact(gadget));

        // Air use opens the picker; no teleport happens until a head is clicked.
        assertThat(teleport.targets).isEmpty();
    }

    @Test
    void rightClickingAPlayerWithTheFreezeGadgetTogglesFreeze() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.FREEZE));
        player.getInventory().setItemInMainHand(gadget);
        PlayerInteractEntityEvent event = interactEntity();

        listener.onInteractEntity(event);

        assertThat(freeze.toggled).extracting(PlayerRef::uuid).containsExactly(target.getUniqueId());
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void rightClickingAPlayerWithTheCompassGadgetTeleportsToThem() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.COMPASS));
        player.getInventory().setItemInMainHand(gadget);

        listener.onInteractEntity(interactEntity());

        assertThat(teleport.targets).extracting(PlayerRef::uuid).containsExactly(target.getUniqueId());
    }

    @Test
    void rightClickingAPlayerWithTheFollowGadgetStartsFollowing() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.FOLLOW));
        player.getInventory().setItemInMainHand(gadget);

        listener.onInteractEntity(interactEntity());

        assertThat(follow.isFollowing(player.getUniqueId())).isTrue();
    }

    @Test
    void rightClickingAnEntityWithAnOrdinaryItemIsLeftAlone() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        PlayerInteractEntityEvent event = interactEntity();

        listener.onInteractEntity(event);

        assertThat(freeze.toggled).isEmpty();
        assertThat(teleport.targets).isEmpty();
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void rightClickingAnOrdinaryItemIsLeftAlone() {
        PlayerInteractEvent event = interact(new ItemStack(Material.DIAMOND_SWORD));

        listener.onInteract(event);

        assertThat(vanish.states).isEmpty();
        assertThat(event.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void aGadgetItemCannotBeDropped() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.VANISH));
        Item dropped = player.getWorld().dropItem(player.getLocation(), gadget);
        PlayerDropItemEvent event = new PlayerDropItemEvent(player, dropped);

        listener.onDrop(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void swappingAGadgetIntoTheOffHandIsCancelled() {
        ItemStack gadget = gadgetItems.build(spec(StaffGadget.VANISH));
        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(player, new ItemStack(Material.AIR), gadget);

        listener.onSwapHand(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void swappingOrdinaryItemsIsLeftAlone() {
        PlayerSwapHandItemsEvent event =
                new PlayerSwapHandItemsEvent(player, new ItemStack(Material.DIRT), new ItemStack(Material.STONE));

        listener.onSwapHand(event);

        assertThat(event.isCancelled()).isFalse();
    }

    private PlayerInteractEvent interact(ItemStack hand) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, hand, null, null, EquipmentSlot.HAND);
    }

    private PlayerInteractEntityEvent interactEntity() {
        return new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND);
    }

    private StaffSettings.GadgetSpec spec(StaffGadget gadget) {
        return settings.gadgets().stream()
                .filter(s -> s.gadget() == gadget)
                .findFirst()
                .orElseThrow();
    }

    private StaffGadgetActions actions() {
        return new StaffGadgetActions(
                vanish,
                freeze,
                teleport,
                follow,
                examineView(),
                playerMenu(),
                new SyncScheduler(),
                server,
                StaffAdapterFakes.notifier(keySink));
    }

    private StaffServices services() {
        StaffModeStoreImpl store = new StaffModeStoreImpl();
        StaffAdapterFakes.RecordingRepository repository = new StaffAdapterFakes.RecordingRepository();
        var capture = new com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture(
                settings, gadgetItems, vanish);
        var recover = new com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout(
                store, repository, capture, vanish, StaffAdapterFakes.notifier());
        EnterStaffMode enter = new EnterStaffMode(
                store,
                repository,
                capture,
                vanish,
                StaffAdapterFakes.notifier(),
                new StaffAdapterFakes.RecordingEvents(),
                recover,
                "default",
                true);
        ExitStaffMode exit = new ExitStaffMode(
                store,
                repository,
                capture,
                vanish,
                StaffAdapterFakes.notifier(),
                new StaffAdapterFakes.RecordingEvents());
        SendStaffChat chat = new SendStaffChat(StaffChannel.NONE, new StaffAdapterFakes.RecordingEvents());
        return new StaffServices(enter, exit, recover, chat, inspector, store);
    }

    private StaffExamineView examineView() {
        Messages messages = new EchoMessages();
        return new StaffExamineView(messages, new SilentSink(), new SyncScheduler(), services());
    }

    /** A live menu engine with the navigator/list specs registered, so an air-COMPASS open does not throw. */
    private StaffPlayerMenu playerMenu() {
        GuiText guiText = new GuiText(new EchoMessages());
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, guiText, new SyncScheduler(), bindings.lists());
        StaffPlayerMenu menu = new StaffPlayerMenu(menus, server, new EchoMessages(), new SilentSink(), teleport);
        menu.register(bindings, specResources(), new StaffAdapterFakes.NoopLogger());
        return menu;
    }

    /** The bundled spec directory under the source tree, so the menu loads the shipped navigator/list specs. */
    private static java.nio.file.Path specResources() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        while (dir != null && !java.nio.file.Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        return java.util.Objects.requireNonNull(dir, "repo root").resolve("bukkit-adapter/src/main/resources");
    }

    /** A {@link StaffInspector} that records who inspected whom. */
    private static final class RecordingInspector implements StaffInspector {
        final List<PlayerRef> targets = new ArrayList<>();

        @Override
        public void inspect(PlayerRef looker, PlayerRef target) {
            targets.add(target);
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            // The follow service holds the handle and closes it on shutdown; the test drives ticks directly.
            return () -> {};
        }
    }
}

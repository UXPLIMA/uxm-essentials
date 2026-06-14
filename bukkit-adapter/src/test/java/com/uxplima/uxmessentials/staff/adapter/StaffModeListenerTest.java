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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.EchoMessages;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingVanish;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.SilentSink;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineView;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffModeListener;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The gadget interaction listener: a right-click on the VANISH gadget toggles vanish and cancels the
 * interaction, a non-gadget interaction is left alone, and a gadget item can never be dropped.
 */
class StaffModeListenerTest {

    private ServerMock server;
    private Player player;
    private Plugin plugin;
    private StaffGadgetItems gadgetItems;
    private StaffSettings settings;
    private RecordingVanish vanish;
    private RecordingInspector inspector;
    private StaffModeListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        gadgetItems = new StaffGadgetItems(plugin);
        settings = StaffAdapterFakes.defaultSettings();
        vanish = new RecordingVanish();
        inspector = new RecordingInspector();
        listener = new StaffModeListener(services(), gadgetItems, vanish, examineView());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rightClickingTheVanishGadgetTogglesVanishAndCancelsTheInteraction() {
        ItemStack gadget = gadgetItems.build(vanishSpec());
        PlayerInteractEvent event = interact(gadget);

        listener.onInteract(event);

        assertThat(vanish.states).hasSize(1);
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
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
        ItemStack gadget = gadgetItems.build(vanishSpec());
        Item dropped = player.getWorld().dropItem(player.getLocation(), gadget);
        PlayerDropItemEvent event = new PlayerDropItemEvent(player, dropped);

        listener.onDrop(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void anOrdinaryItemCanStillBeDropped() {
        Item dropped = player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.DIRT));
        PlayerDropItemEvent event = new PlayerDropItemEvent(player, dropped);

        listener.onDrop(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void swappingAGadgetIntoTheOffHandIsCancelled() {
        ItemStack gadget = gadgetItems.build(vanishSpec());
        // F-key swap with a gadget in hand: the swap must be cancelled so a gadget never strands in the off-hand.
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

    private StaffSettings.GadgetSpec vanishSpec() {
        return settings.gadgets().stream()
                .filter(spec -> spec.gadget() == StaffGadget.VANISH)
                .findFirst()
                .orElseThrow();
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
    }
}

package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.Jail;
import com.uxplima.uxmessentials.moderation.application.ListJails;
import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end MockBukkit coverage of the bare-{@code /jail} GUI flow (capability A), driving the real picker →
 * jail-chooser → duration chain through inventory clicks and asserting the {@code Jail} use case is reached with
 * the chosen jail and duration token. The permanent path passes the permanent token; a timed path passes the
 * chosen preset span. The use cases are Mockito mocks so the wiring is asserted without a live persistence stack;
 * the scheduler runs each hop inline.
 */
class JailGuiFlowTest {

    // The picker grids the online roster in insertion order: the actor is added first (slot 0), so the target
    // added second sits at slot 1. Clicking it picks the jail subject.
    private static final int TARGET_HEAD_SLOT = 1;

    // The jail chooser is a paginated list whose content starts at slot 0; the single jail name sits there.
    private static final int FIRST_JAIL_SLOT = 0;

    // The duration grid puts its first preset (the permanent token) one slot in; the second preset follows.
    private static final int PERMANENT_PRESET_SLOT = 1;
    private static final int FIRST_TIMED_PRESET_SLOT = 2;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock actor;
    private PlayerRef actorRef;
    private PlayerMock targetPlayer;
    private PlayerRef target;

    private ModerationServices services;
    private Jail jail;
    private ListJails listJails;
    private JailGuiFlow flow;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        actor = server.addPlayer("Staff");
        actorRef = new PlayerRef(actor.getUniqueId(), actor.getName());
        targetPlayer = server.addPlayer("Target");
        target = new PlayerRef(targetPlayer.getUniqueId(), targetPlayer.getName());

        services = mock(ModerationServices.class);
        jail = mock(Jail.class);
        listJails = mock(ListJails.class);
        TargetResolver targets = name -> Optional.of(target);
        when(services.jail()).thenReturn(jail);
        when(services.listJails()).thenReturn(listJails);
        when(services.targets()).thenReturn(targets);
        when(listJails.names()).thenReturn(List.of("alcatraz"));

        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();
        AnvilInput anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        PlayerPickerView picker =
                new PlayerPickerView(guiText, scheduler, anvil, server, new KeyMessages(), new NoopSink());
        DurationPickerView durations =
                new DurationPickerView(guiText, scheduler, anvil, new KeyMessages(), new NoopSink());
        flow = new JailGuiFlow(guiText, scheduler, services, picker, durations, new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void permanentPathJailsWithThePermanentToken() {
        flow.open(actor, actorRef, footers());

        fireClick(TARGET_HEAD_SLOT); // pick the target head -> jail chooser
        fireClick(FIRST_JAIL_SLOT); // pick the jail -> duration picker
        fireClick(PERMANENT_PRESET_SLOT); // pick permanent -> jail

        verify(jail)
                .jail(eq(actorRef), eq(target), eq("alcatraz"), eq(JailGuiFlow.PERMANENT_TOKEN), eq(Optional.empty()));
    }

    @Test
    void timedPathJailsWithTheChosenDurationToken() {
        flow.open(actor, actorRef, footers());

        fireClick(TARGET_HEAD_SLOT);
        fireClick(FIRST_JAIL_SLOT);
        fireClick(FIRST_TIMED_PRESET_SLOT); // the first timed preset (permanent is preset 0)

        String chosen = DurationPickerView.defaultPresets().get(0);
        verify(jail).jail(eq(actorRef), eq(target), eq("alcatraz"), eq(chosen), eq(Optional.empty()));
    }

    private static JailGuiFlow.Footers footers() {
        return new JailGuiFlow.Footers(List.of());
    }

    private void fireClick(int slot) {
        InventoryView view = actor.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, java.util.Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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

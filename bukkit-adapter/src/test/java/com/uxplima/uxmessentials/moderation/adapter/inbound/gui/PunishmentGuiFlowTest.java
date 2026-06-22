package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.application.IssueWarn;
import com.uxplima.uxmessentials.moderation.application.Mute;
import com.uxplima.uxmessentials.moderation.application.TempBan;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
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
 * End-to-end MockBukkit coverage of the bare-command GUI flow for the timed and non-silent verbs, driving the
 * real picker → (duration) → confirm chain through inventory clicks and asserting the verb-specific use case is
 * reached with the right arguments: tempban with the chosen duration and the silent flag of the clicked button,
 * tempmute likewise routing to {@code mute(...)} with the duration, warn routing to {@code warn(...)}, and banip
 * resolving the picked player's last-known IP into {@code banIp(...)}. The use cases are Mockito mocks so the
 * flow's wiring is asserted without a live persistence stack; the scheduler runs each hop inline.
 */
class PunishmentGuiFlowTest {

    private static final int FIRST_PRESET_SLOT = 1;
    private static final int APPLY_SLOT = 10;
    private static final int SILENT_SLOT = 12;

    // The picker grids the online roster in insertion order: the actor is added first (slot 0), so the target
    // added second sits at slot 1. Clicking it picks the target as the sanction subject.
    private static final int TARGET_HEAD_SLOT = 1;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock actor;
    private PlayerRef actorRef;
    private PlayerMock targetPlayer;
    private PlayerRef target;

    private ModerationServices services;
    private TempBan tempBan;
    private Mute mute;
    private IssueWarn warn;
    private BanIp banIp;
    private ModerationRepository repository;
    private PunishmentGuiFlow flow;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        actor = server.addPlayer("Staff");
        actorRef = new PlayerRef(actor.getUniqueId(), actor.getName());
        targetPlayer = server.addPlayer("Target");
        target = new PlayerRef(targetPlayer.getUniqueId(), targetPlayer.getName());

        services = mock(ModerationServices.class);
        tempBan = mock(TempBan.class);
        mute = mock(Mute.class);
        warn = mock(IssueWarn.class);
        banIp = mock(BanIp.class);
        repository = mock(ModerationRepository.class);
        TargetResolver targets = name -> Optional.of(target);
        when(services.tempBan()).thenReturn(tempBan);
        when(services.mute()).thenReturn(mute);
        when(services.warn()).thenReturn(warn);
        when(services.banIp()).thenReturn(banIp);
        when(services.repository()).thenReturn(repository);
        when(services.targets()).thenReturn(targets);

        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();
        AnvilInput anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        PlayerPickerView picker =
                new PlayerPickerView(guiText, scheduler, anvil, server, new KeyMessages(), new NoopSink());
        DurationPickerView durations =
                new DurationPickerView(guiText, scheduler, anvil, new KeyMessages(), new NoopSink());
        PunishmentConfirmView confirm = new PunishmentConfirmView(guiText, scheduler, anvil);
        flow = new PunishmentGuiFlow(services, picker, durations, confirm, new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void tempbanRoutesPickerThenDurationThenConfirmWithTheChosenDuration() {
        flow.open(actor, actorRef, PunishmentAction.TEMPBAN);

        fireClick(TARGET_HEAD_SLOT); // pick the target head -> duration picker
        fireClick(FIRST_PRESET_SLOT); // pick the first preset -> confirm screen
        fireClick(APPLY_SLOT); // apply (non-silent)

        String chosen = DurationPickerView.defaultPresets().get(0);
        verify(tempBan).tempban(eq(actorRef), eq(target), eq(chosen), eq(Optional.empty()), eq(false));
    }

    @Test
    void tempbanSilentButtonPassesTheSilentFlag() {
        flow.open(actor, actorRef, PunishmentAction.TEMPBAN);

        fireClick(TARGET_HEAD_SLOT);
        fireClick(FIRST_PRESET_SLOT);
        fireClick(SILENT_SLOT);

        String chosen = DurationPickerView.defaultPresets().get(0);
        verify(tempBan).tempban(eq(actorRef), eq(target), eq(chosen), eq(Optional.empty()), eq(true));
    }

    @Test
    void tempmuteRoutesThroughMuteWithTheChosenDuration() {
        flow.open(actor, actorRef, PunishmentAction.TEMPMUTE);

        fireClick(TARGET_HEAD_SLOT);
        fireClick(FIRST_PRESET_SLOT);
        fireClick(APPLY_SLOT);

        String chosen = DurationPickerView.defaultPresets().get(0);
        verify(mute).mute(eq(actorRef), eq(target), eq(chosen), eq(Optional.empty()), eq(false));
    }

    @Test
    void warnRoutesPickerStraightToConfirm() {
        flow.open(actor, actorRef, PunishmentAction.WARN);

        fireClick(TARGET_HEAD_SLOT); // no duration step for warn -> confirm screen
        fireClick(APPLY_SLOT);

        verify(warn).warn(eq(actorRef), eq(target), eq(Optional.empty()), eq(false));
    }

    @Test
    void banipResolvesThePickedPlayersIpIntoTheUseCase() {
        when(repository.seen(target))
                .thenReturn(
                        Optional.of(new SeenRecord(target, Optional.of("203.0.113.7"), Instant.EPOCH, Instant.EPOCH)));

        flow.open(actor, actorRef, PunishmentAction.BANIP);

        fireClick(TARGET_HEAD_SLOT); // no duration step, no silent button for banip -> confirm
        fireClick(APPLY_SLOT);

        verify(banIp)
                .banIp(
                        eq(actorRef),
                        eq(new BanIp.Target("203.0.113.7", Optional.of(target.uuid()))),
                        eq(""),
                        eq(Optional.empty()));
    }

    @Test
    void banipWithNoRecordedIpDoesNotBan() {
        when(repository.seen(target))
                .thenReturn(Optional.of(new SeenRecord(target, Optional.empty(), Instant.EPOCH, Instant.EPOCH)));

        flow.open(actor, actorRef, PunishmentAction.BANIP);

        fireClick(TARGET_HEAD_SLOT);
        fireClick(APPLY_SLOT);

        verifyNoInteractions(banIp);
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

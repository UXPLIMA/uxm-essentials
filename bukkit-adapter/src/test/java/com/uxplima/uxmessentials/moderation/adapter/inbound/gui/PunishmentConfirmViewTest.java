package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link PunishmentConfirmView}: opening it builds a 3-row screen with the target head and
 * the apply/silent confirm buttons, the normal apply button fires the executor with {@code silent=false}, and the
 * silent button fires it with {@code silent=true} — both carrying the chosen target. The scheduler double runs
 * each hop inline so a click resolves on the test thread.
 */
class PunishmentConfirmViewTest {

    private static final int APPLY_SLOT = 10;
    private static final int SILENT_SLOT = 12;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock actor;
    private PlayerRef actorRef;
    private PlayerRef target;
    private TextInput textInput;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        actor = server.addPlayer("Staff");
        actorRef = new PlayerRef(actor.getUniqueId(), actor.getName());
        target = new PlayerRef(java.util.UUID.randomUUID(), "Target");
        textInput = TextInputTestKit.create(
                plugin,
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                java.nio.file.Path.of("nonexistent"),
                new NoopLogger());
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void opensAConfirmScreenWithTheTargetHeadAndButtons() {
        view().open(actor, actorRef, target, PunishmentAction.BAN, recording(), () -> {});

        Inventory menu = actor.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(SimpleGui.class);
        assertThat(menu.getItem(4).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(menu.getItem(APPLY_SLOT).getType()).isEqualTo(Material.REDSTONE_BLOCK);
        assertThat(menu.getItem(SILENT_SLOT).getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    void theApplyButtonCallsTheExecutorNonSilent() {
        RecordingExecutor executor = recording();
        view().open(actor, actorRef, target, PunishmentAction.BAN, executor, () -> {});

        fireClick(APPLY_SLOT);

        assertThat(executor.calls).hasSize(1);
        assertThat(executor.calls.get(0).target()).isEqualTo(target);
        assertThat(executor.calls.get(0).silent()).isFalse();
    }

    @Test
    void theSilentButtonCallsTheExecutorSilent() {
        RecordingExecutor executor = recording();
        view().open(actor, actorRef, target, PunishmentAction.MUTE, executor, () -> {});

        fireClick(SILENT_SLOT);

        assertThat(executor.calls).hasSize(1);
        assertThat(executor.calls.get(0).target()).isEqualTo(target);
        assertThat(executor.calls.get(0).silent()).isTrue();
    }

    private PunishmentConfirmView view() {
        return new PunishmentConfirmView(new GuiText(new KeyMessages()), new SyncScheduler(), textInput);
    }

    private RecordingExecutor recording() {
        return new RecordingExecutor();
    }

    private void fireClick(int slot) {
        InventoryView view = actor.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private record Call(PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {}

    private static final class RecordingExecutor implements PunishmentAction.Executor {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public void execute(PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
            calls.add(new Call(actor, target, reason, silent));
        }
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
    }
}

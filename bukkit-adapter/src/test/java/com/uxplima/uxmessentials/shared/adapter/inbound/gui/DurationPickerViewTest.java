package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link DurationPickerView}: opening it builds the preset grid with clock buttons, a
 * preset click fires the callback with that exact duration string, a custom anvil submission of a valid timed
 * span fires it with the trimmed string, a malformed span replies with the reject key without firing, and a
 * permanent parse is rejected for a timed verb (the validator the flow supplies refuses it). The scheduler
 * double runs each hop inline so a click resolves on the test thread.
 */
class DurationPickerViewTest {

    // The first preset sits one slot in from the top-left corner (see DurationPickerView#presetSlot).
    private static final int FIRST_PRESET_SLOT = 1;

    @TempDir
    private Path inputDir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock viewer;
    private PlayerRef viewerRef;
    private TextInput textInput;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = server.addPlayer("Staff");
        viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        // A rejected span reopens the prompt; chat mode keeps that reopen off the anvil (MockBukkit cannot render
        // a live anvil), so the reject branch is observable while the reopen is a harmless chat send.
        Files.writeString(inputDir.resolve("config.conf"), "default-mode = chat\n");
        textInput = TextInputTestKit.create(
                plugin, new GuiText(new KeyMessages()), new SyncScheduler(), inputDir, new NoopLogger());
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void opensThePresetGridWithClockButtons() {
        view().open(viewer, viewerRef, request(new RecordingPick()));

        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(SimpleGui.class);
        assertThat(menu.getItem(FIRST_PRESET_SLOT).getType()).isEqualTo(Material.CLOCK);
    }

    @Test
    void aPresetClickFiresTheCallbackWithThatString() {
        RecordingPick pick = new RecordingPick();
        view().open(viewer, viewerRef, request(pick));

        fireClick(FIRST_PRESET_SLOT);

        assertThat(pick.picks)
                .containsExactly(DurationPickerView.defaultPresets().get(0));
    }

    @Test
    void aValidCustomSpanFiresTheCallbackWithTheTrimmedString() {
        RecordingPick pick = new RecordingPick();

        view().resolveTyped(viewer, viewerRef, request(pick), "  2h30m  ");

        assertThat(pick.picks).containsExactly("2h30m");
    }

    @Test
    void aMalformedCustomSpanRejectsWithoutFiring() {
        RecordingPick pick = new RecordingPick();
        RecordingSink sink = new RecordingSink();

        view(sink).resolveTyped(viewer, viewerRef, request(pick), "notaduration");

        assertThat(pick.picks).isEmpty();
        assertThat(sink.delivered).containsExactly(ModerationMessageKey.MOD_GUI_DURATION_REJECT.key());
    }

    @Test
    void aPermanentSpanIsRejectedForATimedVerb() {
        RecordingPick pick = new RecordingPick();
        RecordingSink sink = new RecordingSink();

        view(sink).resolveTyped(viewer, viewerRef, request(pick), "permanent");

        assertThat(pick.picks).isEmpty();
        assertThat(sink.delivered).containsExactly(ModerationMessageKey.MOD_GUI_DURATION_REJECT.key());
    }

    private DurationPickerView view() {
        return view(new RecordingSink());
    }

    private DurationPickerView view(MessageSink sink) {
        return new DurationPickerView(
                new GuiText(new KeyMessages()), new SyncScheduler(), textInput, new KeyMessages(), sink);
    }

    // The validator mirrors the timed-verb rule the moderation flow supplies: only a positive, well-formed span.
    private DurationPickerView.Request request(RecordingPick pick) {
        return new DurationPickerView.Request(
                ModerationMessageKey.MOD_GUI_DURATION_TEMPBAN_TITLE,
                DurationPickerView.defaultPresets(),
                pick,
                raw -> SanctionDuration.parse(raw).duration().isPresent(),
                ModerationMessageKey.MOD_GUI_DURATION_REJECT,
                Optional.empty());
    }

    private void fireClick(int slot) {
        InventoryView view = viewer.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class RecordingPick implements java.util.function.Consumer<String> {
        private final List<String> picks = new ArrayList<>();

        @Override
        public void accept(String duration) {
            picks.add(duration);
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
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

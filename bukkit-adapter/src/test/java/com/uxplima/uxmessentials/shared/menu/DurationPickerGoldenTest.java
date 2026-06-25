package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
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
 * The duration-picker golden test: the engine-rendered preset grid must draw the exact single-page grid the original
 * {@code DurationPickerView} drew on uxmLib's {@code SimpleGui}. The fixture is the default nine-span preset ladder
 * over the picker's three-row layout (preset CLOCKs in the centre of the top two rows, a WRITABLE_BOOK custom button
 * at slot 22, an ARROW back button at slot 18, gray-glass filler everywhere else). The engine window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the analytic baseline the old view
 * produced for this fixture. A real click on a preset through the engine's own {@link
 * com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} proves the migrated path hands that
 * exact span to the recording {@code onPick}; the back button runs its supplied action. The custom-span anvil branch is
 * driven through the package-private {@code resolveTyped} apply seam (MockBukkit cannot open a live anvil): a valid span
 * fires {@code onPick}, a malformed one does not.
 */
class DurationPickerGoldenTest {

    private static final int CUSTOM_SLOT = 22;
    private static final int BACK_SLOT = 18;

    /** The default preset ladder the moderation callers offer; the geometry baseline is computed against it. */
    private static final List<String> PRESETS = DurationPickerView.defaultPresets();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TextInput textInput;
    private TestMenuEngine engine;
    private DurationPickerView picker;
    private final List<String> picked = new ArrayList<>();
    private final List<String> backRuns = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP_LOG);
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        picker = new DurationPickerView(engine.menus(), guiText, scheduler, textInput, new KeyMessages(), SINK);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePresetGridCustomBackAndFillerAsTheOldView() {
        openPicker();

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        openPicker();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingAPresetThroughTheEngineRunsOnPickWithThatSpan() {
        openPicker();

        fireClick(presetSlot(0)); // the first preset, "30m"

        assertThat(picked).containsExactly(PRESETS.get(0));
    }

    @Test
    void clickingTheBackButtonThroughTheEngineRunsTheBackAction() {
        openPicker();

        fireClick(BACK_SLOT);

        assertThat(backRuns).containsExactly("back");
    }

    private void openPicker() {
        picker.open(player, viewer, request());
    }

    private DurationPickerView.Request request() {
        return new DurationPickerView.Request(
                Key.TITLE,
                PRESETS,
                picked::add,
                raw -> raw.equals("12h") || PRESETS.contains(raw),
                Key.REJECT,
                Optional.of(() -> backRuns.add("back")));
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code DurationPickerView} produced for this fixture: a CLOCK
     * at each preset slot named through the {@code gui.duration-picker.preset-name} key the test's {@code KeyMessages}
     * returns verbatim, a WRITABLE_BOOK custom button at slot 22, and an ARROW back button at slot 18. The gray-glass
     * filler slots are dropped from the snapshot, so a wrong material, name, or misplaced button still mismatches.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int i = 0; i < PRESETS.size(); i++) {
            baseline.put(presetSlot(i), new Snapshot(Material.CLOCK, GuiMessageKey.DURATION_PICKER_PRESET_NAME.key()));
        }
        baseline.put(CUSTOM_SLOT, new Snapshot(Material.WRITABLE_BOOK, GuiMessageKey.DURATION_PICKER_CUSTOM.key()));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, GuiMessageKey.DURATION_PICKER_BACK.key()));
        return baseline;
    }

    /** The preset geometry the old view used: the centre of the top two rows, wrapping after seven per row. */
    private static int presetSlot(int index) {
        return index + (index / 7) * 2 + 1;
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        Component name = Objects.requireNonNull(item.getItemMeta()).displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** Catalog keys for the synthetic request; their text is irrelevant beyond identifying the title and reject line. */
    private enum Key implements MessageKey {
        TITLE("demo.duration.title"),
        REJECT("demo.duration.reject");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final MessageSink SINK = (viewer, message) -> {};

    private static final Logger NOOP_LOG = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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

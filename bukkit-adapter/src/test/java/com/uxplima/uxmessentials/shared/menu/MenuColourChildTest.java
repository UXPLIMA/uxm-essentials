package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.EngineScheduler;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.gui.input.TextInputTestKit;
import com.uxplima.uxmlib.menu.EditorSpec;
import com.uxplima.uxmlib.menu.EntityEditorLayout;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.property.colour.ColourPickerLayout;
import com.uxplima.uxmlib.menu.property.colour.ColourPickerText;
import com.uxplima.uxmlib.menu.property.colour.ColourProperty;
import com.uxplima.uxmlib.menu.property.colour.ColourSwatch;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.runtime.MenuListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of increment 8: a {@link ColourProperty} clicked inside an editor running on the engine
 * editor runtime opens its colour picker as an engine child window. A {@link MenuHolder} routed by the one
 * {@link MenuListener}, rather than a uxmLib {@code SimpleGui}. The picker renders the 16-colour palette plus the
 * custom-hex, clear, and back buttons at the layout slots; clicking a swatch hands its packed ARGB int to the
 * setter and reopens the parent editor in place, the clear button fires the clear runnable and reopens the parent,
 * and back reopens the parent. The custom-hex anvil submit branch rides the runtime-neutral apply seam
 * ({@link ColourProperty#applyCustom}): a valid hex writes through the setter, an invalid one writes nothing and
 * reopens the picker child. The whole open → swatch → back → close flow leaves no live refresh task.
 */
class MenuColourChildTest {

    /** A mutable fake subject whose single packed-ARGB field the colour property rewrites through a recording setter. */
    private static final class Widget {
        private int colour = SENTINEL;
    }

    private static final int SENTINEL = Integer.MIN_VALUE;
    private static final int PROP_SLOT = 10;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private GuiText guiText;
    private Scheduler scheduler;
    private TextInput textInput;
    private Menus menus;
    private Widget widget;
    private boolean cleared;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        widget = new Widget();
        cleared = false;
        // The custom-hex anvil seam and the legacy uxmLib fallback both ride uxmLib's installed listener.
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP_LOG);
        installEngine(scheduler);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void clickingTheColourPropertyOpensAnEngineChildWithThePaletteCustomClearAndBack() {
        openEditor();

        fireClick(PROP_SLOT, ClickType.LEFT);

        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        // Every palette slot carries its swatch's configured stained-glass pane material.
        for (int i = 0; i < layout.paletteSlots().size(); i++) {
            assertThat(child.getItem(layout.paletteSlots().get(i)).getType())
                    .isEqualTo(layout.paletteIcons().get(i));
        }
        assertThat(child.getItem(layout.customSlot()).getType()).isEqualTo(Material.ANVIL);
        assertThat(child.getItem(layout.clearSlot()).getType()).isEqualTo(Material.BARRIER);
        assertThat(child.getItem(layout.backSlot()).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void clickingASwatchWritesThePackedArgbAndReopensTheParentEditor() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        // Slot 11 is the second palette swatch (orange) in the code-default layout.
        fireClick(11, ClickType.LEFT);

        assertThat(widget.colour).isEqualTo(ColourSwatch.ORANGE.argb());
        // A swatch reopens the parent editor (matching the old picker, whose pick runs context.reopen()).
        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
        assertThat(parent.getItem(PROP_SLOT).getType()).isEqualTo(Material.PAINTING);
    }

    @Test
    void clickingClearFiresTheClearRunnableAndReopensTheParentEditor() {
        widget.colour = 0xFF112233;
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(ColourPickerLayout.codeDefault().clearSlot(), ClickType.LEFT);

        assertThat(cleared).isTrue();
        assertThat(widget.colour).isEqualTo(SENTINEL);
        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
    }

    @Test
    void clickingBackReopensTheParentEditor() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(ColourPickerLayout.codeDefault().backSlot(), ClickType.LEFT);

        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
        assertThat(parent.getItem(PROP_SLOT).getType()).isEqualTo(Material.PAINTING);
    }

    @Test
    void theCustomHexApplySeamSavesAValidColourAndReopensTheParent() {
        // The custom-hex anvil submit branch reaches the runtime-neutral applyCustom seam (player.openAnvil is
        // unimplemented in MockBukkit, exactly as ListPropertyApplyTest drives applyAdd/applyEdit). A valid hex
        // writes the parsed ARGB through the setter and reopens the parent editor.
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);
        PropertyClick picker = engineContext();

        property().applyCustom(picker, "#FF5733");

        assertThat(widget.colour).isEqualTo(0xFFFF5733);
        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
    }

    @Test
    void theCustomHexApplySeamRejectsAnInvalidColourWithoutWritingAndReopensThePicker() {
        widget.colour = 0xFF445566;
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);
        PropertyClick picker = engineContext();

        property().applyCustom(picker, "not-a-colour");

        // The invalid line wrote nothing and reopened the picker child, not the parent editor.
        assertThat(widget.colour).isEqualTo(0xFF445566);
        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) child.getHolder()).editor()).isEmpty();
        assertThat(child.getItem(ColourPickerLayout.codeDefault().customSlot()).getType())
                .isEqualTo(Material.ANVIL);
    }

    @Test
    void openSwatchBackCloseLeavesNoLiveRefreshTask() {
        var recording = new RecordingScheduler();
        installEngine(recording);

        menus.openEditor(player, editorSpec(), widget);
        fireClick(PROP_SLOT, ClickType.LEFT); // open picker child
        fireClick(11, ClickType.LEFT); // swatch → setter + reopen parent
        fireClick(PROP_SLOT, ClickType.LEFT); // reopen the picker child
        fireClick(ColourPickerLayout.codeDefault().backSlot(), ClickType.LEFT); // back → parent editor
        player.closeInventory();
        player.closeInventory(); // a double-close is a harmless no-op

        // Neither the editor nor the picker arms a refresh timer, so start and cancel both stay balanced at zero.
        assertThat(recording.scheduled).isZero();
        assertThat(recording.cancelled).isZero();
    }

    private void installEngine(Scheduler sched) {
        EditorRenderer editorRenderer = new EditorRenderer(guiText, ThemeFile::shippedTheme);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, ThemeFile::shippedTheme, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, EngineScheduler.of(sched), new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                EngineScheduler.of(sched),
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private void openEditor() {
        menus.openEditor(player, editorSpec(), widget);
    }

    private EditorSpec editorSpec() {
        return EditorSpec.builder()
                .layout(layout())
                .title((v, subject) -> Component.text("edit"))
                .valueLore(Key.VALUE_LORE.key())
                .backName(Key.BACK.key())
                .properties(w -> List.<EditableProperty>of(property()))
                .onBack(p -> {})
                .build();
    }

    /** A context on the engine path: it carries the engine openers so a custom-hex reopen takes the engine branch. */
    private PropertyClick engineContext() {
        return new PropertyClick(
                player,
                false,
                false,
                () -> menus.openEditor(player, editorSpec(), widget),
                menus.selectorOpener(),
                menus.confirmOpener());
    }

    private ColourProperty property() {
        return new ColourProperty(
                Key.LABEL.key(),
                Material.PAINTING,
                () -> widget.colour,
                value -> widget.colour = value,
                () -> {
                    cleared = true;
                    widget.colour = SENTINEL;
                },
                SENTINEL,
                v -> "default",
                guiText,
                ColourPickerText.shared(),
                ColourPickerLayout.codeDefault(),
                textInput::prompt,
                EngineScheduler.of(scheduler));
    }

    private static EntityEditorLayout layout() {
        return new EntityEditorLayout(
                3,
                List.of(PROP_SLOT),
                22,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Catalog keys for the test editor and picker; their text is irrelevant beyond the value-lore placeholder. */
    private enum Key implements MessageKey {
        LABEL("demo.prop.label"),
        VALUE_LORE("demo.editor.value-lore"),
        BACK("demo.editor.back");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    /** A value-lore key renders as {@code value=<value>} so a value can be read back; everything else echoes the key. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("demo.editor.value-lore")) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

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

    /** A scheduler that records every repeating-task start and cancel, for the leak-balance assertion. */
    private static final class RecordingScheduler implements Scheduler {
        int scheduled = 0;
        int cancelled = 0;

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            scheduled++;
            return () -> cancelled++;
        }

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

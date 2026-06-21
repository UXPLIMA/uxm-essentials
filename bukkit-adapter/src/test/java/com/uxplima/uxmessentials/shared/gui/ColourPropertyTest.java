package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourSwatch;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
 * MockBukkit coverage of the shared colour-picker widget on its own, independent of any module: the picker
 * renders the 16-colour palette at the configured slots, clicking a palette swatch hands its packed ARGB int to
 * the setter, the custom-hex seam parses a valid hex into the right ARGB and rejects an invalid one without
 * writing, and the clear button fires the clear callback. The scheduler runs every hop inline so the off-thread
 * writes land synchronously.
 */
class ColourPropertyTest {

    private static final int SENTINEL = Integer.MIN_VALUE;
    private static final MessageKey LABEL = GuiMessageKey.COLOUR_PICKER_TITLE;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private AnvilInput anvil;
    private AtomicInteger stored;
    private AtomicBoolean cleared;
    private ColourProperty property;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        anvil = new AnvilInput(plugin);
        Guis.install(plugin);
        stored = new AtomicInteger(SENTINEL);
        cleared = new AtomicBoolean(false);
        property = new ColourProperty(
                LABEL,
                Material.PAINTING,
                stored::get,
                stored::set,
                () -> {
                    cleared.set(true);
                    stored.set(SENTINEL);
                },
                SENTINEL,
                v -> "default",
                guiText,
                ColourPickerText.shared(),
                ColourPickerLayout.codeDefault(),
                anvil,
                scheduler);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void pickerRendersThePaletteAtTheConfSlots() {
        open();

        Inventory inv = player.getOpenInventory().getTopInventory();
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        // Every palette slot carries its swatch's configured stained-glass pane material.
        for (int i = 0; i < layout.paletteSlots().size(); i++) {
            assertThat(inv.getItem(layout.paletteSlots().get(i)).getType())
                    .isEqualTo(layout.paletteIcons().get(i));
        }
        assertThat(inv.getItem(layout.customSlot()).getType()).isEqualTo(Material.ANVIL);
        assertThat(inv.getItem(layout.clearSlot()).getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    void clickingAPaletteSwatchCallsTheSetterWithThatArgb() {
        open();

        // Slot 11 is the second palette swatch (orange) in the code-default layout.
        clickPicker(11);

        assertThat(stored.get()).isEqualTo(ColourSwatch.ORANGE.argb());
    }

    @Test
    void customHexSeamParsesAValidColourAndRejectsAnInvalidOne() {
        ClickContext ctx = context();

        property.applyCustom(ctx, "#FF5733");
        assertThat(stored.get()).isEqualTo(0xFFFF5733);

        property.applyCustom(ctx, "zzzzzz");
        // Still the previous valid value; the invalid line wrote nothing.
        assertThat(stored.get()).isEqualTo(0xFFFF5733);

        // An 8-digit value carries its own alpha.
        property.applyCustom(ctx, "8011AAFF");
        assertThat(stored.get()).isEqualTo(0x8011AAFF);
    }

    @Test
    void clearButtonFiresTheClearCallback() {
        stored.set(0xFF112233);
        open();

        clickPicker(ColourPickerLayout.codeDefault().clearSlot());

        assertThat(cleared.get()).isTrue();
        assertThat(stored.get()).isEqualTo(SENTINEL);
    }

    private void open() {
        property.onClick(context());
    }

    private ClickContext context() {
        return new ClickContext(player, viewer, false, false, () -> {});
    }

    private void clickPicker(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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

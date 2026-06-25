package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.EnumMap;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.npc.adapter.outbound.EquipmentPayloads;
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
 * MockBukkit coverage of {@link NpcEquipmentProperty} on the engine editor runtime: clicking the equipment property
 * opens the per-slot grid as an engine child window (a {@link MenuHolder} routed by the one {@link MenuListener}),
 * never a uxmLib {@code SimpleGui}. The grid carries one button per {@link EquipmentSlot} plus a back button;
 * left-clicking a slot sets it from the operator's main hand (the held item serialized through
 * {@link EquipmentPayloads}), shift-left clears it, and back reopens the parent editor. A recording set/clear pair
 * proves the gestures reach the use-case seam, and the open → click → child → back flow leaves no live refresh task.
 */
class NpcEquipmentPropertyTest {

    private static final int PROP_SLOT = 10;
    // codeDefault equipment layout: HEAD..MAINHAND at 11..15, OFFHAND at 16, back at 22.
    private static final int HEAD_SLOT = 11;
    private static final int OFFHAND_SLOT = 16;
    private static final int BACK_SLOT = 22;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private Menus menus;
    private EnumMap<EquipmentSlot, String> worn;
    private List<EquipmentSlot> cleared;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        worn = new EnumMap<>(EquipmentSlot.class);
        cleared = new java.util.ArrayList<>();
        installEngine(scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void clickingTheEquipmentPropertyOpensAnEngineChildGridWithOneButtonPerSlotPlusBack() {
        openEditor();

        fireClick(PROP_SLOT, ClickType.LEFT);

        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        // Every equipment slot draws a button (empty slots render with the layout's empty icon), and so does back.
        assertThat(child.getItem(HEAD_SLOT)).isNotNull();
        assertThat(child.getItem(OFFHAND_SLOT)).isNotNull();
        assertThat(child.getItem(BACK_SLOT).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void leftClickingASlotWithAHeldItemSetsItFromTheSerializedToken() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_HELMET));
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(HEAD_SLOT, ClickType.LEFT);

        // The HEAD slot was set from the held diamond helmet, serialized through EquipmentPayloads.
        String token = worn.get(EquipmentSlot.HEAD);
        assertThat(token).isNotNull();
        assertThat(EquipmentPayloads.isSerialized(token)).isTrue();
        assertThat(EquipmentPayloads.resolve(token))
                .hasValueSatisfying(item -> assertThat(item.getType()).isEqualTo(Material.DIAMOND_HELMET));
        // The grid re-rendered in place: still an engine child, not the parent editor.
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) player.getOpenInventory().getTopInventory().getHolder()).editor())
                .isEmpty();
    }

    @Test
    void shiftLeftClickingASlotClearsIt() {
        worn.put(EquipmentSlot.HEAD, EquipmentPayloads.serialize(new ItemStack(Material.DIAMOND_HELMET)));
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(HEAD_SLOT, ClickType.SHIFT_LEFT);

        // The clear use-case ran for the HEAD slot.
        assertThat(cleared).containsExactly(EquipmentSlot.HEAD);
    }

    @Test
    void clickingBackReopensTheParentEditor() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(BACK_SLOT, ClickType.LEFT);

        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
        assertThat(parent.getItem(PROP_SLOT).getType()).isEqualTo(Material.ARMOR_STAND);
    }

    @Test
    void openClickSlotBackCloseLeavesNoLiveRefreshTask() {
        RecordingScheduler recording = new RecordingScheduler();
        installEngine(recording);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_HELMET));

        menus.openEditor(viewer, editorSpec(), null);
        fireClick(PROP_SLOT, ClickType.LEFT); // open the equipment grid child
        fireClick(HEAD_SLOT, ClickType.LEFT); // set from hand + reopen grid
        fireClick(BACK_SLOT, ClickType.LEFT); // back → parent editor
        player.closeInventory();
        player.closeInventory(); // a double-close is a harmless no-op

        // Neither the editor nor the grid arms a refresh timer, so start and cancel both stay balanced at zero.
        assertThat(recording.scheduled).isZero();
        assertThat(recording.cancelled).isZero();
    }

    private void installEngine(Scheduler sched) {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, sched, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                sched,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private void openEditor() {
        menus.openEditor(viewer, editorSpec(), null);
    }

    private EditorSpec editorSpec() {
        return EditorSpec.builder()
                .layout(layout())
                .title((v, subject) -> Component.text("edit"))
                .valueLore(Key.VALUE_LORE)
                .backName(Key.BACK)
                .properties(subject -> List.<EditableProperty>of(equipmentProperty()))
                .onBack((p, v) -> {})
                .build();
    }

    private NpcEquipmentProperty equipmentProperty() {
        return new NpcEquipmentProperty(
                guiText,
                new KeyMessages(),
                NpcEditorSubLayouts.codeDefault().equipment(),
                () -> Map.copyOf(worn),
                (slot, token) -> worn.put(slot, token),
                slot -> {
                    cleared.add(slot);
                    worn.remove(slot);
                    return null;
                },
                scheduler);
    }

    private static EntityEditorLayout layout() {
        return new EntityEditorLayout(
                3,
                List.of(PROP_SLOT),
                26,
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

    /** Catalog keys for the test editor; every key resolves to its own string. */
    private enum Key implements MessageKey {
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

    /** A value-lore key renders as {@code value=<value>}; everything else echoes the key. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("demo.editor.value-lore")) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
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

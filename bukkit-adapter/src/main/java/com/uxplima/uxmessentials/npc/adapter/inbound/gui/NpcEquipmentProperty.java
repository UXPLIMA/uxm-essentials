package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.npc.adapter.outbound.EquipmentPayloads;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;

/**
 * The "equipment" property: its click opens a six-button grid — head, chest, legs, feet, main-hand, off-hand —
 * each button showing what the NPC currently wears in that slot. Left-clicking a slot sets it from the operator's
 * held item (serialized verbatim with its NBT, exactly as {@code /npc equip <slot> hand} does), and a shift-left
 * clears it. The reads come fresh from the supplier on each open (the list-click snapshot would go stale after an
 * edit), and writes go through the existing {@link com.uxplima.uxmessentials.npc.application.SetNpcEquipment} use
 * case wrapped as the {@code setSlot}/{@code clearSlot} consumers, so this property adds no domain logic.
 */
final class NpcEquipmentProperty implements EditableProperty {

    private final GuiText guiText;
    private final Messages messages;
    private final NpcEditorSubLayouts.EquipmentLayout layout;
    private final Supplier<Map<EquipmentSlot, String>> current;
    private final BiConsumer<EquipmentSlot, String> setSlot;
    private final Function<EquipmentSlot, Void> clearSlot;
    private final Scheduler scheduler;

    /** The six slots, in the order their buttons are drawn (matches {@code layout.allSlots()}). */
    private static final List<EquipmentSlot> SLOT_ORDER = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND);

    NpcEquipmentProperty(
            GuiText guiText,
            Messages messages,
            NpcEditorSubLayouts.EquipmentLayout layout,
            Supplier<Map<EquipmentSlot, String>> current,
            BiConsumer<EquipmentSlot, String> setSlot,
            Function<EquipmentSlot, Void> clearSlot,
            Scheduler scheduler) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.current = Objects.requireNonNull(current, "current");
        this.setSlot = Objects.requireNonNull(setSlot, "setSlot");
        this.clearSlot = Objects.requireNonNull(clearSlot, "clearSlot");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public NpcMessageKey label() {
        return NpcMessageKey.NPC_GUI_PROP_EQUIPMENT;
    }

    @Override
    public Material icon() {
        return Material.ARMOR_STAND;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return Integer.toString(current.get().size());
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        scheduler.onEntity(context.viewer(), () -> openGrid(context));
    }

    private void openGrid(ClickContext context) {
        SimpleGui grid = Guis.gui()
                .title(guiText.text(context.viewer(), NpcMessageKey.NPC_GUI_EQUIP_TITLE))
                .rows(layout.rows())
                .build();
        fill(grid);
        Map<EquipmentSlot, String> worn = current.get();
        List<Integer> slots = layout.allSlots();
        for (int i = 0; i < SLOT_ORDER.size() && i < slots.size(); i++) {
            EquipmentSlot slot = SLOT_ORDER.get(i);
            grid.set(slots.get(i), slotButton(context, slot, worn.get(slot)));
        }
        grid.set(layout.backSlot(), backButton(context));
        grid.open(context.player());
    }

    private GuiItem slotButton(
            ClickContext context, EquipmentSlot slot, @org.jspecify.annotations.Nullable String token) {
        Material material = iconFor(token);
        ItemStack icon = ItemBuilder.of(material)
                .name(guiText.text(
                        context.viewer(), NpcMessageKey.NPC_GUI_EQUIP_SLOT_NAME, Map.of("slot", label(slot))))
                .lore(guiText.text(
                        context.viewer(),
                        NpcMessageKey.NPC_GUI_EQUIP_SLOT_HINTS,
                        Map.of("item", describe(context.viewer(), token))))
                .build();
        return GuiItem.button(icon, event -> {
            if (event.isShiftClick() && event.isLeftClick()) {
                clear(context, slot);
            } else if (event.isLeftClick()) {
                setFromHand(context, slot);
            }
        });
    }

    private GuiItem backButton(ClickContext context) {
        ItemStack back = ItemBuilder.of(layout.backIcon())
                .name(guiText.text(context.viewer(), NpcMessageKey.NPC_GUI_EQUIP_BACK))
                .build();
        return GuiItem.button(back, event -> context.reopen().run());
    }

    private void setFromHand(ClickContext context, EquipmentSlot slot) {
        ItemStack hand = context.player().getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            scheduler.onEntity(context.viewer(), () -> openGrid(context));
            return;
        }
        String token = EquipmentPayloads.serialize(hand);
        scheduler.async(() -> {
            setSlot.accept(slot, token);
            scheduler.onEntity(context.viewer(), () -> openGrid(context));
        });
    }

    private void clear(ClickContext context, EquipmentSlot slot) {
        scheduler.async(() -> {
            clearSlot.apply(slot);
            scheduler.onEntity(context.viewer(), () -> openGrid(context));
        });
    }

    private Material iconFor(@org.jspecify.annotations.Nullable String token) {
        return EquipmentPayloads.resolve(token).map(ItemStack::getType).orElse(layout.emptyIcon());
    }

    private String describe(PlayerRef viewer, @org.jspecify.annotations.Nullable String token) {
        return EquipmentPayloads.resolve(token)
                .map(item -> item.getType().name())
                .orElseGet(() -> messages.resolve(viewer, NpcMessageKey.NPC_GUI_EQUIP_EMPTY, Map.of()));
    }

    /** The slot's display word — its enum name, the same word {@code /npc equip} uses. */
    private static String label(EquipmentSlot slot) {
        return slot.name();
    }

    private void fill(SimpleGui grid) {
        ItemStack filler =
                ItemBuilder.of(layout.fillerIcon()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            grid.set(slot, GuiItem.display(filler));
        }
    }
}

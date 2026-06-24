package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * GUI for administrators to customize specific kit settings like cooldown,
 * cost, permission, display options, and commands.
 */
@NullMarked
public final class KitSettingsView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public KitSettingsView(Messages messages, Scheduler scheduler, GuiLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    public GuiLayout layout() {
        return layout;
    }

    /**
     * Open the settings GUI for a specific kit.
     */
    public void open(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kit, "kit");
        scheduler.onEntity(viewer, () -> {
            KitSettingsHolder holder = new KitSettingsHolder(viewer, kit);
            Inventory inventory = Bukkit.createInventory(holder, layout.rows() * 9, title(kit, viewer));
            holder.attach(inventory);
            populate(inventory, kit, viewer);
            player.openInventory(inventory);
        });
    }

    private int slot(int index, int fallback) {
        List<Integer> slots = layout.contentSlots();
        if (index >= 0 && index < slots.size()) {
            return slots.get(index);
        }
        return fallback;
    }

    private void setItemIfValid(Inventory inventory, int slot, int size, ItemStack item) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
        }
    }

    private void populate(Inventory inventory, KitDefinition kit, PlayerRef viewer) {
        // A neutral background pane, decoupled from the kit fallback icon so a non-pane fallback never tiles the
        // whole settings screen with itself.
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        int size = layout.rows() * 9;
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        editButton(inventory, viewer, size, 0, 0, Material.CHEST, KitsMessageKey.KIT_EDITOR_SETTINGS_EDIT_ITEMS_NAME);
        button(
                inventory,
                viewer,
                size,
                1,
                2,
                kit.permission() ? Material.PAPER : Material.BARRIER,
                KitsMessageKey.KIT_EDITOR_SETTINGS_PERMISSION_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_PERMISSION_LORE,
                value(
                        viewer,
                        kit.permission(),
                        KitsMessageKey.KIT_EDITOR_VALUE_REQUIRED,
                        KitsMessageKey.KIT_EDITOR_VALUE_NONE),
                KitsMessageKey.KIT_EDITOR_SETTINGS_PERMISSION_TOGGLE);
        button(
                inventory,
                viewer,
                size,
                2,
                4,
                Material.CLOCK,
                KitsMessageKey.KIT_EDITOR_SETTINGS_ONETIME_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_ONETIME_LORE,
                yesNo(viewer, kit.oneTime()),
                KitsMessageKey.KIT_EDITOR_SETTINGS_ONETIME_TOGGLE);
        button(
                inventory,
                viewer,
                size,
                3,
                6,
                Material.COMPARATOR,
                KitsMessageKey.KIT_EDITOR_SETTINGS_COOLDOWN_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_COOLDOWN_LORE,
                Long.toString(kit.cooldownSeconds()),
                KitsMessageKey.KIT_EDITOR_SETTINGS_COOLDOWN_PROMPT);
        button(
                inventory,
                viewer,
                size,
                4,
                8,
                Material.GOLD_INGOT,
                KitsMessageKey.KIT_EDITOR_SETTINGS_COST_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_COST_LORE,
                kit.hasCost()
                        ? kit.cost().amount().toPlainString()
                        : resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_FREE),
                KitsMessageKey.KIT_EDITOR_SETTINGS_COST_PROMPT);
        button(
                inventory,
                viewer,
                size,
                5,
                10,
                Material.NAME_TAG,
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_NAME_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_NAME_LORE,
                kit.displayName().orElseGet(() -> resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NONE)),
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_NAME_PROMPT);
        Material displayMaterial = resolveDisplayMaterial(kit);
        button(
                inventory,
                viewer,
                size,
                6,
                12,
                displayMaterial,
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_MATERIAL_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_MATERIAL_LORE,
                displayMaterial.name().toLowerCase(java.util.Locale.ROOT),
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_MATERIAL_PROMPT);
        button(
                inventory,
                viewer,
                size,
                7,
                14,
                Material.BOOK,
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_LORE_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_LORE_LORE,
                Integer.toString(kit.displayLore().size()),
                "count",
                KitsMessageKey.KIT_EDITOR_SETTINGS_DISPLAY_LORE_PROMPT);
        button(
                inventory,
                viewer,
                size,
                8,
                16,
                Material.COMMAND_BLOCK,
                KitsMessageKey.KIT_EDITOR_SETTINGS_COMMANDS_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_COMMANDS_LORE,
                Integer.toString(kit.commands().size()),
                "count",
                KitsMessageKey.KIT_EDITOR_SETTINGS_COMMANDS_PROMPT);
        deleteButton(inventory, viewer, size, 9, 22);
        button(
                inventory,
                viewer,
                size,
                10,
                18,
                Material.FEATHER,
                KitsMessageKey.KIT_EDITOR_SETTINGS_FIRSTJOIN_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_FIRSTJOIN_LORE,
                yesNo(viewer, kit.firstJoin()),
                KitsMessageKey.KIT_EDITOR_SETTINGS_FIRSTJOIN_TOGGLE);
        button(
                inventory,
                viewer,
                size,
                11,
                20,
                Material.ARMOR_STAND,
                KitsMessageKey.KIT_EDITOR_SETTINGS_AUTOEQUIP_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_AUTOEQUIP_LORE,
                yesNo(viewer, kit.autoEquip()),
                KitsMessageKey.KIT_EDITOR_SETTINGS_AUTOEQUIP_TOGGLE);
        button(
                inventory,
                viewer,
                size,
                12,
                24,
                Material.BOOKSHELF,
                KitsMessageKey.KIT_EDITOR_SETTINGS_CATEGORY_NAME,
                KitsMessageKey.KIT_EDITOR_SETTINGS_CATEGORY_LORE,
                kit.categoryId().orElseGet(() -> resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NONE)),
                KitsMessageKey.KIT_EDITOR_SETTINGS_CATEGORY_PROMPT);
        backButton(inventory, viewer, size);
    }

    /** A settings button whose current-value placeholder is named {@code current}. */
    private void button(
            Inventory inventory,
            PlayerRef viewer,
            int size,
            int slotIndex,
            int fallback,
            Material material,
            KitsMessageKey nameKey,
            KitsMessageKey loreKey,
            String current,
            KitsMessageKey secondaryKey) {
        button(
                inventory,
                viewer,
                size,
                slotIndex,
                fallback,
                material,
                nameKey,
                loreKey,
                current,
                "current",
                secondaryKey);
    }

    private void button(
            Inventory inventory,
            PlayerRef viewer,
            int size,
            int slotIndex,
            int fallback,
            Material material,
            KitsMessageKey nameKey,
            KitsMessageKey loreKey,
            String value,
            String placeholder,
            KitsMessageKey secondaryKey) {
        setItemIfValid(
                inventory,
                slot(slotIndex, fallback),
                size,
                ItemBuilder.of(material)
                        .name(text(viewer, nameKey))
                        .lore(List.of(
                                text(viewer, loreKey, Map.of(placeholder, value)),
                                Component.empty(),
                                text(viewer, secondaryKey)))
                        .build());
    }

    private void editButton(
            Inventory inventory,
            PlayerRef viewer,
            int size,
            int slotIndex,
            int fallback,
            Material material,
            KitsMessageKey nameKey) {
        setItemIfValid(
                inventory,
                slot(slotIndex, fallback),
                size,
                ItemBuilder.of(material)
                        .name(text(viewer, nameKey))
                        .lore(List.of(text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_EDIT_ITEMS_LORE)))
                        .build());
    }

    private void deleteButton(Inventory inventory, PlayerRef viewer, int size, int slotIndex, int fallback) {
        setItemIfValid(
                inventory,
                slot(slotIndex, fallback),
                size,
                ItemBuilder.of(Material.REDSTONE_BLOCK)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_DELETE_NAME))
                        .lore(List.of(
                                text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_DELETE_LORE),
                                Component.empty(),
                                text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_DELETE_WARNING)))
                        .build());
    }

    private void backButton(Inventory inventory, PlayerRef viewer, int size) {
        if (layout.prevSlot() >= 0 && layout.prevSlot() < size) {
            inventory.setItem(
                    layout.prevSlot(),
                    ItemBuilder.of(Material.ARROW)
                            .name(text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_BACK_BUTTON))
                            .build());
        }
    }

    /**
     * The display item the kit actually shows in the browse menu — the configured display material if it parses,
     * otherwise the first kit item's type, otherwise {@link Material#CHEST}. The display-material button renders
     * with this so the editor always reflects the live icon rather than a fixed placeholder.
     */
    private Material resolveDisplayMaterial(KitDefinition kit) {
        if (kit.displayMaterial().isPresent()) {
            Material parsed = Material.matchMaterial(kit.displayMaterial().get().toUpperCase(java.util.Locale.ROOT));
            if (parsed != null && !parsed.isAir()) {
                return parsed;
            }
        }
        if (kit.items().isEmpty()) {
            return Material.CHEST;
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? Material.CHEST : type;
    }

    private String resolve(PlayerRef viewer, KitsMessageKey key) {
        return messages.resolve(viewer, key, Map.of());
    }

    private String yesNo(PlayerRef viewer, boolean value) {
        return value(viewer, value, KitsMessageKey.KIT_EDITOR_VALUE_YES, KitsMessageKey.KIT_EDITOR_VALUE_NO);
    }

    private String value(PlayerRef viewer, boolean condition, KitsMessageKey trueKey, KitsMessageKey falseKey) {
        return resolve(viewer, condition ? trueKey : falseKey);
    }

    private Component title(KitDefinition kit, PlayerRef viewer) {
        return text(
                viewer,
                KitsMessageKey.KIT_EDITOR_SETTINGS_TITLE,
                Map.of("kit", kit.id().value()));
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }
}

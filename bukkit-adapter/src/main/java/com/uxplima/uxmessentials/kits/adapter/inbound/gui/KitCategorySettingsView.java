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
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KitCategorySettingsView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage;

    public KitCategorySettingsView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(Player player, PlayerRef viewer, KitCategory category) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(category, "category");
        scheduler.onEntity(viewer, () -> {
            KitCategorySettingsHolder holder = new KitCategorySettingsHolder(viewer, category);
            Inventory inventory = Bukkit.createInventory(holder, 27, title(category, viewer));
            holder.attach(inventory);
            populate(inventory, category, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, KitCategory category, PlayerRef viewer) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        // Slot 10: Set Display Name
        inventory.setItem(
                10,
                ItemBuilder.of(Material.NAME_TAG)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_CURRENT,
                                        Map.of("current", category.displayName())),
                                Component.empty(),
                                text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_PROMPT)))
                        .build());

        // Slot 12: Set Display Material
        String matName = category.displayMaterial().orElse("BOOK");
        inventory.setItem(
                12,
                ItemBuilder.of(Material.GOLDEN_CHESTPLATE)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_CURRENT,
                                        Map.of("current", matName)),
                                Component.empty(),
                                text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_PROMPT)))
                        .build());

        // Slot 14: Edit Display Lore
        inventory.setItem(
                14,
                ItemBuilder.of(Material.BOOK)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_CURRENT,
                                        Map.of(
                                                "count",
                                                Integer.toString(
                                                        category.displayLore().size()))),
                                Component.empty(),
                                text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_PROMPT)))
                        .build());

        // Slot 16: Set Slot Index (Sorting Slot)
        inventory.setItem(
                16,
                ItemBuilder.of(Material.COMPARATOR)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_SLOT_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_SLOT_CURRENT,
                                        Map.of("slot", Integer.toString(category.slot()))),
                                Component.empty(),
                                text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_SLOT_PROMPT)))
                        .build());

        // Slot 18: Set Parent Category
        String parentName = category.parentCategoryId()
                .orElseGet(() -> messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of()));
        inventory.setItem(
                18,
                ItemBuilder.of(Material.BOOKSHELF)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_PARENT_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_PARENT_CURRENT,
                                        Map.of("parent", parentName)),
                                Component.empty(),
                                text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_PARENT_PROMPT)))
                        .build());

        // Slot 22: Delete Category
        inventory.setItem(
                22,
                ItemBuilder.of(Material.REDSTONE_BLOCK)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DELETE_NAME))
                        .lore(List.of(
                                text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DELETE_LORE),
                                Component.empty(),
                                text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DELETE_WARNING)))
                        .build());

        // Slot 26: Back Arrow
        inventory.setItem(
                26,
                ItemBuilder.of(Material.ARROW)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_BACK_BUTTON))
                        .build());
    }

    private Component title(KitCategory category, PlayerRef viewer) {
        return miniMessage.deserialize(messages.resolve(
                viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_TITLE, Map.of("kit", category.id())));
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

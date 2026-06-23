package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A single warp category's settings: a 27-slot chest with one button per editable field (display name,
 * material, lore, sorting slot, parent category) plus delete and a back arrow. Mirrors
 * {@code KitCategorySettingsView}; the click routing lives in {@link WarpCategoryEditing}.
 */
@NullMarked
public final class WarpCategorySettingsView {

    private final Messages messages;
    private final Scheduler scheduler;

    public WarpCategorySettingsView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void open(Player player, PlayerRef viewer, WarpCategory category) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(category, "category");
        scheduler.onEntity(viewer, () -> {
            WarpCategorySettingsHolder holder = new WarpCategorySettingsHolder(viewer, category);
            Inventory inventory = Bukkit.createInventory(holder, 27, title(category, viewer));
            holder.attach(inventory);
            populate(inventory, category, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, WarpCategory category, PlayerRef viewer) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(
                10,
                ItemBuilder.of(Material.NAME_TAG)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_CURRENT,
                                        Map.of("current", category.displayName())),
                                Component.empty(),
                                text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_PROMPT)))
                        .build());

        String matName = category.displayMaterial().orElse("BOOK");
        inventory.setItem(
                12,
                ItemBuilder.of(buttonMaterial(matName))
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_CURRENT,
                                        Map.of("current", matName)),
                                Component.empty(),
                                text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_PROMPT)))
                        .build());

        inventory.setItem(
                14,
                ItemBuilder.of(Material.BOOK)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_CURRENT,
                                        Map.of(
                                                "count",
                                                Integer.toString(
                                                        category.displayLore().size()))),
                                Component.empty(),
                                text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_PROMPT)))
                        .build());

        inventory.setItem(
                16,
                ItemBuilder.of(Material.COMPARATOR)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_SLOT_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_SLOT_CURRENT,
                                        Map.of("slot", Integer.toString(category.slot()))),
                                Component.empty(),
                                text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_SLOT_PROMPT)))
                        .build());

        String parentName = category.parentCategoryId().orElseGet(() -> none(viewer));
        inventory.setItem(
                18,
                ItemBuilder.of(Material.BOOKSHELF)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_PARENT_NAME))
                        .lore(List.of(
                                text(
                                        viewer,
                                        WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_PARENT_CURRENT,
                                        Map.of("parent", parentName)),
                                Component.empty(),
                                text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_PARENT_PROMPT)))
                        .build());

        inventory.setItem(
                22,
                ItemBuilder.of(Material.REDSTONE_BLOCK)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DELETE_NAME))
                        .lore(List.of(
                                text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DELETE_LORE),
                                Component.empty(),
                                text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DELETE_WARNING)))
                        .build());

        inventory.setItem(
                26,
                ItemBuilder.of(Material.ARROW)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                        .build());
    }

    /**
     * The button material for the display-material option: the category's configured material itself, so an
     * operator sees the icon they set rather than a fixed stand-in. An unset or unparseable name falls back to
     * the same {@code BOOK} the lore line names.
     */
    private static Material buttonMaterial(String name) {
        Material parsed = Material.matchMaterial(name);
        return parsed != null && parsed != Material.AIR ? parsed : Material.BOOK;
    }

    private Component title(WarpCategory category, PlayerRef viewer) {
        return StyledText.render(messages.resolve(
                viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_TITLE, Map.of("category", category.id())));
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, WarpsMessageKey.WARP_EDITOR_VALUE_NONE, Map.of());
    }
}

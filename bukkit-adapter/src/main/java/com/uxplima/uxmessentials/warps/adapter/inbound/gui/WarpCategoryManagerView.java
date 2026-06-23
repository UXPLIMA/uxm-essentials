package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
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

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The warp category manager: a 6-row chest listing every category as a clickable icon, with a "create
 * category" button and a back arrow that returns to the warp manager. Mirrors {@code KitCategoryManagerView}.
 * Clicking a category opens its settings (handled by {@link WarpCategoryEditing} via the listener); every line
 * resolves from a {@link MessageKey} in the viewer's locale.
 */
@NullMarked
public final class WarpCategoryManagerView {

    private final Messages messages;
    private final WarpCategoryRepository categoryRepository;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage;

    public WarpCategoryManagerView(Messages messages, WarpCategoryRepository categoryRepository, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> {
            List<WarpCategory> categories = categoryRepository.all();
            WarpCategoryManagerHolder holder = new WarpCategoryManagerHolder(viewer, categories);
            Inventory inventory = Bukkit.createInventory(holder, 54, title(viewer));
            holder.attach(inventory);
            populate(inventory, categories, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, List<WarpCategory> categories, PlayerRef viewer) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        int index = 0;
        for (WarpCategory cat : categories) {
            if (index >= 45) {
                break;
            }
            inventory.setItem(index, icon(cat, viewer));
            index++;
        }

        inventory.setItem(
                49,
                ItemBuilder.of(Material.EMERALD_BLOCK)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_CREATE_BUTTON_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_CREATE_BUTTON_LORE)))
                        .build());

        inventory.setItem(
                53,
                ItemBuilder.of(Material.ARROW)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                        .build());
    }

    private ItemStack icon(WarpCategory category, PlayerRef viewer) {
        Component name = miniMessage.deserialize(category.displayName());
        List<Component> lore = new ArrayList<>();
        if (!category.displayLore().isEmpty()) {
            for (String line : category.displayLore()) {
                lore.add(miniMessage.deserialize(line));
            }
        }
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lore.add(text(
                viewer,
                WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_SLOT,
                Map.of("slot", Integer.toString(category.slot()))));
        lore.add(text(
                viewer,
                WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_PARENT,
                Map.of("parent", category.parentCategoryId().orElse(none(viewer)))));
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_LORE_EDIT_HINT));
        return ItemBuilder.of(WarpCategoryIcons.material(category))
                .name(name)
                .lore(lore)
                .build();
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_MANAGER_TITLE);
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

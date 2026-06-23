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
 * The warp → category selector: opened from a warp's editor, it lists every category plus a "no category"
 * button, and clicking one assigns the warp being edited to that category (or clears it). Mirrors
 * {@code KitCategorySelectorView}; click routing lives in {@link WarpCategoryEditing}.
 */
@NullMarked
public final class WarpCategorySelectorView {

    private final Messages messages;
    private final WarpCategoryRepository categoryRepository;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage;

    public WarpCategorySelectorView(Messages messages, WarpCategoryRepository categoryRepository, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(Player player, PlayerRef viewer, String warpName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        scheduler.onEntity(viewer, () -> {
            List<WarpCategory> categories = categoryRepository.all();
            WarpCategorySelectorHolder holder = new WarpCategorySelectorHolder(viewer, warpName);
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
                ItemBuilder.of(Material.BARRIER)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_NONE_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_NONE_LORE)))
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
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_SELECT_HINT));
        return ItemBuilder.of(WarpCategoryIcons.material(category))
                .name(name)
                .lore(lore)
                .build();
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_TITLE);
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }
}

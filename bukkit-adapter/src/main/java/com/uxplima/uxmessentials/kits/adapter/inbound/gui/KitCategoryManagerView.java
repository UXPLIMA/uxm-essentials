package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

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

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KitCategoryManagerView {

    private final Messages messages;
    private final KitCategoryRepository categoryRepository;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage;

    public KitCategoryManagerView(Messages messages, KitCategoryRepository categoryRepository, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> {
            List<KitCategory> categories = categoryRepository.all();
            KitCategoryManagerHolder holder = new KitCategoryManagerHolder(viewer, categories);
            Inventory inventory = Bukkit.createInventory(holder, 54, title(viewer));
            holder.attach(inventory);
            populate(inventory, categories, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, List<KitCategory> categories, PlayerRef viewer) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        int index = 0;
        for (KitCategory cat : categories) {
            if (index >= 45) {
                break;
            }
            inventory.setItem(index, icon(cat, viewer));
            index++;
        }

        // Create new category button at slot 49
        inventory.setItem(
                49,
                ItemBuilder.of(Material.EMERALD_BLOCK)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_CREATE_BUTTON_NAME))
                        .lore(List.of(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_CREATE_BUTTON_LORE)))
                        .build());

        // Close button at slot 53 (goes back to the engine-rendered kit manager)
        inventory.setItem(
                53,
                ItemBuilder.of(Material.ARROW)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_BACK_BUTTON))
                        .build());
    }

    private ItemStack icon(KitCategory category, PlayerRef viewer) {
        Component name = miniMessage.deserialize(category.displayName());
        List<Component> lore = new ArrayList<>();

        if (!category.displayLore().isEmpty()) {
            for (String line : category.displayLore()) {
                lore.add(miniMessage.deserialize(line));
            }
        }
        lore.add(Component.empty());
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lore.add(text(
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_SLOT,
                Map.of("slot", Integer.toString(category.slot()))));
        lore.add(text(
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_PARENT,
                Map.of("parent", category.parentCategoryId().orElse(none(viewer)))));
        lore.add(Component.empty());
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_LORE_EDIT_HINT));

        Material mat = Material.BOOK;
        if (category.displayMaterial().isPresent()) {
            try {
                Material parsed =
                        Material.valueOf(category.displayMaterial().get().toUpperCase(java.util.Locale.ROOT));
                if (!parsed.isAir()) {
                    mat = parsed;
                }
            } catch (IllegalArgumentException absent) {
                // Keep default
            }
        }

        return ItemBuilder.of(mat).name(name).lore(lore).build();
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_MANAGER_TITLE);
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of());
    }
}

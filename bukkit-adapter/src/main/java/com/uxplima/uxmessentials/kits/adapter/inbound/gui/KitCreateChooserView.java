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
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KitCreateChooserView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage;

    public KitCreateChooserView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> {
            KitCreateChooserHolder holder = new KitCreateChooserHolder(viewer);
            Inventory inventory = Bukkit.createInventory(holder, 27, title(viewer));
            holder.attach(inventory);
            populate(inventory, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, PlayerRef viewer) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        // Slot 11: Create Kit (Chest)
        inventory.setItem(
                11,
                ItemBuilder.of(Material.CHEST)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CREATE_KIT_OPTION_NAME))
                        .lore(List.of(text(viewer, KitsMessageKey.KIT_EDITOR_CREATE_KIT_OPTION_LORE)))
                        .build());

        // Slot 15: Create Category (Book)
        inventory.setItem(
                15,
                ItemBuilder.of(Material.BOOK)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CREATE_CATEGORY_OPTION_NAME))
                        .lore(List.of(text(viewer, KitsMessageKey.KIT_EDITOR_CREATE_CATEGORY_OPTION_LORE)))
                        .build());

        // Slot 22: Back (Arrow)
        inventory.setItem(
                22,
                ItemBuilder.of(Material.ARROW)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_BACK_BUTTON))
                        .build());
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, KitsMessageKey.KIT_EDITOR_CREATE_CHOOSER_TITLE);
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }
}

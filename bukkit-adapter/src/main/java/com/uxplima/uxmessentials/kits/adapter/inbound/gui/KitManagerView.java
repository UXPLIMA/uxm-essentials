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

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The main GUI that displays all available kits to administrators,
 * with a button to create new kits and options to edit existing ones.
 */
@NullMarked
public final class KitManagerView {

    private final Messages messages;
    private final KitRepository repository;
    private final Scheduler scheduler;
    private final GuiLayout layout;
    private final MiniMessage miniMessage;

    public KitManagerView(Messages messages, KitRepository repository, Scheduler scheduler, GuiLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public GuiLayout layout() {
        return layout;
    }

    /**
     * Open the manager GUI for an administrator.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> {
            List<KitDefinition> kits = repository.all();
            KitManagerHolder holder = new KitManagerHolder(viewer, kits);
            Inventory inventory = Bukkit.createInventory(holder, layout.rows() * 9, title(viewer));
            holder.attach(inventory);
            populate(inventory, kits, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, List<KitDefinition> kits, PlayerRef viewer) {
        ItemStack filler =
                ItemBuilder.of(layout.fallbackIcon()).name(Component.empty()).build();
        int size = layout.rows() * 9;
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        List<Integer> slots = layout.explicitContentSlots().orElseGet(() -> {
            List<Integer> defaultSlots = new ArrayList<>();
            int contentLimit = (layout.rows() - 1) * 9;
            for (int i = 0; i < contentLimit; i++) {
                defaultSlots.add(i);
            }
            return defaultSlots;
        });

        int index = 0;
        for (KitDefinition kit : kits) {
            if (index >= slots.size()) {
                break;
            }
            inventory.setItem(slots.get(index), icon(kit, viewer));
            index++;
        }

        // Create new kit button
        if (layout.prevSlot() >= 0 && layout.prevSlot() < size) {
            inventory.setItem(
                    layout.prevSlot(),
                    ItemBuilder.of(Material.EMERALD_BLOCK)
                            .name(text(viewer, KitsMessageKey.KIT_EDITOR_CREATE_BUTTON_NAME))
                            .lore(List.of(text(viewer, KitsMessageKey.KIT_EDITOR_CREATE_BUTTON_LORE)))
                            .build());
        }

        // Close button
        if (layout.nextSlot() >= 0 && layout.nextSlot() < size) {
            inventory.setItem(
                    layout.nextSlot(),
                    ItemBuilder.of(Material.BARRIER)
                            .name(text(viewer, KitsMessageKey.KIT_EDITOR_CLOSE_BUTTON_NAME))
                            .build());
        }

        // Manage categories button at slot 51
        inventory.setItem(
                51,
                ItemBuilder.of(Material.BOOK)
                        .name(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_MANAGER_TITLE))
                        .lore(List.of(text(viewer, KitsMessageKey.KIT_EDITOR_MANAGE_CATEGORIES_LORE)))
                        .build());
    }

    private ItemStack icon(KitDefinition kit, PlayerRef viewer) {
        Component name = kit.displayName()
                .map(miniMessage::deserialize)
                .orElseGet(() -> text(
                        viewer,
                        KitsMessageKey.KIT_MENU_ENTRY_NAME,
                        Map.of("kit", kit.id().value())));

        List<Component> lore = new ArrayList<>();
        if (!kit.displayLore().isEmpty()) {
            for (String line : kit.displayLore()) {
                lore.add(miniMessage.deserialize(line));
            }
        }

        String costStr = kit.hasCost()
                ? kit.cost().amount().toPlainString()
                : messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_FREE, Map.of());

        String permStr = kit.requiresPermission()
                ? messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_REQUIRED, Map.of())
                : messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of());

        String onetimeStr = kit.isOneTime()
                ? messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_YES, Map.of())
                : messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NO, Map.of());

        String firstJoinStr = kit.firstJoin()
                ? messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_YES, Map.of())
                : messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NO, Map.of());

        String autoEquipStr = kit.autoEquip()
                ? messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_YES, Map.of())
                : messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NO, Map.of());

        lore.add(text(
                viewer,
                KitsMessageKey.KIT_EDITOR_KIT_LORE_COOLDOWN,
                Map.of("seconds", Long.toString(kit.cooldownSeconds()))));
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_KIT_LORE_COST, Map.of("cost", costStr)));
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_KIT_LORE_PERMISSION, Map.of("permission", permStr)));
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_KIT_LORE_ONETIME, Map.of("onetime", onetimeStr)));
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_KIT_LORE_FIRSTJOIN, Map.of("firstjoin", firstJoinStr)));
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_KIT_LORE_AUTOEQUIP, Map.of("autoequip", autoEquipStr)));
        lore.add(Component.empty());
        lore.add(text(viewer, KitsMessageKey.KIT_EDITOR_KIT_LORE_EDIT_HINT));

        return ItemBuilder.of(material(kit)).name(name).lore(lore).build();
    }

    private Material material(KitDefinition kit) {
        if (kit.displayMaterial().isPresent()) {
            try {
                Material mat = Material.valueOf(kit.displayMaterial().get().toUpperCase(java.util.Locale.ROOT));
                if (!mat.isAir()) {
                    return mat;
                }
            } catch (IllegalArgumentException absent) {
                // Use fallback icon if display material is invalid
            }
        }
        if (kit.items().isEmpty()) {
            return Material.CHEST;
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? Material.CHEST : type;
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, KitsMessageKey.KIT_EDITOR_MANAGER_TITLE);
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

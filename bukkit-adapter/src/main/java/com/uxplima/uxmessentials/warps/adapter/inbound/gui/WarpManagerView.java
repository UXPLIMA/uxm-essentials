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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The admin warp manager: a paginated chest listing every server warp as a clickable icon, with a button to
 * create a new warp at the operator's current position, a close button, and an entry into the category
 * manager. Mirrors {@code KitManagerView}: clicking a warp opens its {@link WarpEditorView}, the create button
 * runs through the same {@code /warp create} path the command uses, and the categories button opens
 * {@link WarpCategoryManagerView}. Click routing lives in {@link WarpEditorListener}; every line resolves from
 * a {@link MessageKey} in the viewer's locale.
 */
@NullMarked
public final class WarpManagerView {

    /** The reserved slot for the category-manager button, matching the kit manager's slot 51. */
    static final int CATEGORIES_SLOT = 51;

    private final Messages messages;
    private final WarpRepository repository;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public WarpManagerView(Messages messages, WarpRepository repository, Scheduler scheduler, GuiLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    public GuiLayout layout() {
        return layout;
    }

    /** Open the manager GUI for an administrator, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> {
            List<Warp> warps = repository.all();
            WarpManagerHolder holder = new WarpManagerHolder(viewer, warps);
            Inventory inventory = Bukkit.createInventory(holder, layout.rows() * 9, title(viewer));
            holder.attach(inventory);
            populate(inventory, warps, viewer);
            player.openInventory(inventory);
        });
    }

    /** The content slots warps fill, so the listener can map a clicked slot back to a warp. */
    List<Integer> contentSlots() {
        return layout.explicitContentSlots().orElseGet(() -> {
            List<Integer> defaults = new ArrayList<>();
            int contentLimit = (layout.rows() - 1) * 9;
            for (int i = 0; i < contentLimit; i++) {
                defaults.add(i);
            }
            return defaults;
        });
    }

    private void populate(Inventory inventory, List<Warp> warps, PlayerRef viewer) {
        ItemStack filler =
                ItemBuilder.of(layout.fallbackIcon()).name(Component.empty()).build();
        int size = layout.rows() * 9;
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        List<Integer> slots = contentSlots();
        int index = 0;
        for (Warp warp : warps) {
            if (index >= slots.size()) {
                break;
            }
            inventory.setItem(slots.get(index), icon(warp, viewer));
            index++;
        }

        if (layout.prevSlot() >= 0 && layout.prevSlot() < size) {
            inventory.setItem(
                    layout.prevSlot(),
                    ItemBuilder.of(Material.EMERALD_BLOCK)
                            .name(text(viewer, WarpsMessageKey.WARP_MANAGER_CREATE_BUTTON_NAME))
                            .lore(List.of(text(viewer, WarpsMessageKey.WARP_MANAGER_CREATE_BUTTON_LORE)))
                            .build());
        }

        if (layout.nextSlot() >= 0 && layout.nextSlot() < size) {
            inventory.setItem(
                    layout.nextSlot(),
                    ItemBuilder.of(Material.BARRIER)
                            .name(text(viewer, WarpsMessageKey.WARP_MANAGER_CLOSE_BUTTON_NAME))
                            .build());
        }

        if (CATEGORIES_SLOT < size) {
            inventory.setItem(
                    CATEGORIES_SLOT,
                    ItemBuilder.of(Material.BOOK)
                            .name(text(viewer, WarpsMessageKey.WARP_MANAGER_MANAGE_CATEGORIES_NAME))
                            .lore(List.of(text(viewer, WarpsMessageKey.WARP_MANAGER_MANAGE_CATEGORIES_LORE)))
                            .build());
        }
    }

    private ItemStack icon(Warp warp, PlayerRef viewer) {
        Component name = text(
                viewer,
                WarpsMessageKey.WARP_MENU_ENTRY_NAME,
                Map.of("warp", warp.name().value()));
        List<Component> lore = new ArrayList<>();
        lore.add(text(
                viewer,
                WarpsMessageKey.WARP_MANAGER_ENTRY_LORE_CATEGORY,
                Map.of("category", warp.categoryId().orElse(none(viewer)))));
        var at = warp.location();
        lore.add(text(
                viewer,
                WarpsMessageKey.WARP_MANAGER_ENTRY_LORE_LOCATION,
                Map.of(
                        "world", at.world().name(),
                        "x", Integer.toString(at.blockX()),
                        "y", Integer.toString(at.blockY()),
                        "z", Integer.toString(at.blockZ()))));
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_MANAGER_ENTRY_LORE_EDIT_HINT));
        return ItemBuilder.of(material(warp)).name(name).lore(lore).build();
    }

    private Material material(Warp warp) {
        if (warp.iconMaterial().isPresent()) {
            Material parsed = Material.matchMaterial(warp.iconMaterial().get());
            if (parsed != null && parsed != Material.AIR) {
                return parsed;
            }
        }
        return layout.fallbackIcon();
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, WarpsMessageKey.WARP_MANAGER_TITLE);
    }

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, WarpsMessageKey.WARP_EDITOR_VALUE_NONE, Map.of());
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }
}

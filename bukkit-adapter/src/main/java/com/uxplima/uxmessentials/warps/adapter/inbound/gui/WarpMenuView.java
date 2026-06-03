package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the read-only {@code /warps} browse menu as a uxmLib {@link PaginatedGui}: one display icon per warp the
 * player may use, paged through the menu's content slots with previous/next buttons pinned in the reserved
 * bottom row. The warp list is the {@code ListWarps.available} filter the chat list also uses, so the menu never
 * advertises a warp the player can no longer reach. Each icon shows the warp's name and its optional cost and
 * required-permission detail as lore — every line resolved from a {@link MessageKey} in the viewer's locale,
 * never an inline literal. Clicking an icon warps the player through the same {@link UseWarp} use case the
 * {@code /warp} command drives — the view adds no warp, gate, cost, or cooldown logic of its own; UseWarp gates
 * access, charges any cost, and delegates the hop to the teleport context — and then closes the menu.
 *
 * <p>Warps carry no item, so every icon uses the single fixed teleport-flavoured fallback material the
 * layout supplies. The clicked warp's identity comes from the bound element ({@code warp.name()}), never from re-reading the icon, so
 * case normalisation is irrelevant. {@link #open} touches the live player, so the caller schedules it on the
 * viewer's entity thread through the kernel {@link Scheduler}; a click hops the live player, so it too runs on
 * the viewer's entity thread through that same scheduler.
 */
@NullMarked
public final class WarpMenuView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final UseWarp useWarp;
    private final GuiLayout layout;
    private final MiniMessage miniMessage;

    public WarpMenuView(Messages messages, Scheduler scheduler, UseWarp useWarp, GuiLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.useWarp = Objects.requireNonNull(useWarp, "useWarp");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the browse menu listing {@code warps} for {@code player}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, List<Warp> warps) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warps, "warps");
        List<Warp> snapshot = List.copyOf(warps);
        scheduler.onEntity(viewer, () -> {
            PaginatedGui gui = build(viewer);
            gui.populate(
                    snapshot,
                    ItemPopulator.of(
                            warp -> icon(viewer, warp), (warp, event) -> onIconClick(player, viewer, gui, warp)));
            gui.set(layout.prevSlot(), GuiItem.previousPage(gui, navIcon(viewer, WarpsMessageKey.WARP_MENU_PREV)));
            gui.set(layout.nextSlot(), GuiItem.nextPage(gui, navIcon(viewer, WarpsMessageKey.WARP_MENU_NEXT)));
            gui.open(player);
        });
    }

    private PaginatedGui build(PlayerRef viewer) {
        Guis.PaginatedBuilder builder = Guis.paginated().title(title(viewer)).rows(layout.rows());
        layout.explicitContentSlots().ifPresent(builder::contentSlots);
        return builder.build();
    }

    /**
     * Warp to the clicked warp and close the menu. The warp identity comes from the bound element, never from
     * re-reading the clicked icon. The hop moves the live player, so it runs on the viewer's entity thread;
     * {@link UseWarp} gates access, charges any cost, and delegates the hop to the teleport context itself.
     */
    private void onIconClick(Player player, PlayerRef viewer, PaginatedGui gui, Warp warp) {
        scheduler.onEntity(viewer, () -> {
            useWarp.use(viewer, warp.name());
            gui.close(player);
        });
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, WarpsMessageKey.WARP_MENU_TITLE, Map.of());
    }

    private ItemStack icon(PlayerRef viewer, Warp warp) {
        return ItemBuilder.of(layout.fallbackIcon())
                .name(text(
                        viewer,
                        WarpsMessageKey.WARP_MENU_ENTRY_NAME,
                        Map.of("warp", warp.name().value())))
                .lore(lore(viewer, warp))
                .build();
    }

    private List<Component> lore(PlayerRef viewer, Warp warp) {
        List<Component> lines = new ArrayList<>();
        if (warp.hasCost()) {
            lines.add(text(
                    viewer,
                    WarpsMessageKey.WARP_MENU_LORE_COST,
                    Map.of("amount", warp.cost().amount().toPlainString())));
        }
        warp.requiredPermission()
                .ifPresent(permission -> lines.add(
                        text(viewer, WarpsMessageKey.WARP_MENU_LORE_PERMISSION, Map.of("permission", permission))));
        lines.add(text(
                viewer,
                WarpsMessageKey.WARP_MENU_LORE_USABLE,
                Map.of("warp", warp.name().value())));
        return lines;
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(layout.navIcon())
                .name(text(viewer, key, Map.of()))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the read-only {@code /homes} browse menu as a uxmLib {@link PaginatedGui}: one display icon per home the
 * viewer owns, paged through the menu's content slots with previous/next buttons pinned in the reserved bottom
 * row. Homes are per-player and unconditional — a player may teleport to any home in their own set, so there is
 * no access gate beyond {@link TeleportHome}'s own resolution and no cost or required-permission lore; the icon
 * carries only the home's name and a click hint, each line resolved from a {@link MessageKey} in the viewer's
 * locale, never an inline literal. The home list is the {@code ListHomes.homesOf} read the chat list also uses,
 * so the menu lists exactly the homes the text list shows. Clicking an icon teleports the viewer home through the
 * same {@link TeleportHome} use case the {@code /home <name>} command drives — the view adds no home, warmup,
 * cooldown, or teleport logic of its own; TeleportHome resolves the home and delegates the gated hop to the
 * teleport context — and then closes the menu.
 *
 * <p>Homes carry no item, so every icon uses a single fixed bed-flavoured {@link #FALLBACK_ICON}. The clicked
 * home's identity comes from the bound element ({@code home.name()}), never from re-reading the icon, so case
 * normalisation is irrelevant. {@link #open} touches the live player, so the caller schedules it on the viewer's
 * entity thread through the kernel {@link Scheduler}; a click hops the live player, so it too runs on the
 * viewer's entity thread through that same scheduler.
 */
@NullMarked
public final class HomeMenuView {

    private static final int MENU_ROWS = 6;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final Material FALLBACK_ICON = Material.RED_BED;

    private final Messages messages;
    private final Scheduler scheduler;
    private final TeleportHome teleportHome;
    private final MiniMessage miniMessage;

    public HomeMenuView(Messages messages, Scheduler scheduler, TeleportHome teleportHome) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.teleportHome = Objects.requireNonNull(teleportHome, "teleportHome");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the browse menu listing {@code homes} for {@code player}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, List<Home> homes) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(homes, "homes");
        List<Home> snapshot = List.copyOf(homes);
        scheduler.onEntity(viewer, () -> {
            PaginatedGui gui =
                    Guis.paginated().title(title(viewer)).rows(MENU_ROWS).build();
            gui.populate(
                    snapshot,
                    ItemPopulator.of(
                            home -> icon(viewer, home), (home, event) -> onIconClick(player, viewer, gui, home)));
            gui.set(PREV_SLOT, GuiItem.previousPage(gui, navIcon(viewer, HomesMessageKey.HOME_MENU_PREV)));
            gui.set(NEXT_SLOT, GuiItem.nextPage(gui, navIcon(viewer, HomesMessageKey.HOME_MENU_NEXT)));
            gui.open(player);
        });
    }

    /**
     * Teleport to the clicked home and close the menu. The home identity comes from the bound element, never from
     * re-reading the clicked icon. The hop moves the live player, so it runs on the viewer's entity thread;
     * {@link TeleportHome} resolves the home and delegates the gated hop to the teleport context itself.
     */
    private void onIconClick(Player player, PlayerRef viewer, PaginatedGui gui, Home home) {
        scheduler.onEntity(viewer, () -> {
            teleportHome.toNamed(viewer, home.name());
            gui.close(player);
        });
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, HomesMessageKey.HOME_MENU_TITLE, Map.of());
    }

    private ItemStack icon(PlayerRef viewer, Home home) {
        return ItemBuilder.of(FALLBACK_ICON)
                .name(text(
                        viewer,
                        HomesMessageKey.HOME_MENU_ENTRY_NAME,
                        Map.of("home", home.name().value())))
                .lore(List.of(text(
                        viewer,
                        HomesMessageKey.HOME_MENU_LORE_USABLE,
                        Map.of("home", home.name().value()))))
                .build();
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(Material.ARROW).name(text(viewer, key, Map.of())).build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

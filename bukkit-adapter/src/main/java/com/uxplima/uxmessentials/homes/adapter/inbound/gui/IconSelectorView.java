package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
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
 * The home-icon picker, opened from a home's {@link HomeActionView}. A {@link PaginatedGui} of the configured
 * material palette; clicking one sets the home's icon through {@link SetHomeIcon} and returns to the action menu,
 * and a reset button clears the custom icon. Every visible string resolves from a {@link MessageKey} in the
 * viewer's locale, never an inline literal. The picker touches the live player, so it builds and clicks on the
 * viewer's entity thread through the kernel {@link Scheduler}.
 */
@NullMarked
public final class IconSelectorView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final SetHomeIcon setHomeIcon;
    private final IconSelectorLayout layout;
    private final MiniMessage miniMessage;

    public IconSelectorView(
            Messages messages, Scheduler scheduler, SetHomeIcon setHomeIcon, IconSelectorLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.setHomeIcon = Objects.requireNonNull(setHomeIcon, "setHomeIcon");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the picker for {@code home}; {@code onDone} reopens the action menu after a pick or reset. */
    public void open(Player player, PlayerRef viewer, Home home, Runnable onDone) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(onDone, "onDone");
        scheduler.onEntity(viewer, () -> {
            PaginatedGui gui = Guis.paginated()
                    .title(text(viewer, HomesMessageKey.HOME_ICON_TITLE, Map.of()))
                    .rows(layout.rows())
                    .build();
            gui.populate(
                    layout.icons(),
                    ItemPopulator.of(
                            material -> entryIcon(viewer, material),
                            (material, event) -> pick(viewer, home, material, onDone)));
            pinNavigation(viewer, gui, home, onDone);
            gui.open(player);
        });
    }

    private void pinNavigation(PlayerRef viewer, PaginatedGui gui, Home home, Runnable onDone) {
        gui.set(layout.prevSlot(), GuiItem.previousPage(gui, navIcon(viewer, HomesMessageKey.HOME_ICON_PREV)));
        gui.set(layout.nextSlot(), GuiItem.nextPage(gui, navIcon(viewer, HomesMessageKey.HOME_ICON_NEXT)));
        gui.set(layout.backSlot(), GuiItem.button(navIcon(viewer, HomesMessageKey.HOME_ICON_BACK), e -> onDone.run()));
        gui.set(layout.resetSlot(), GuiItem.button(resetIcon(viewer), e -> reset(viewer, home, onDone)));
    }

    private void pick(PlayerRef viewer, Home home, Material material, Runnable onDone) {
        scheduler.onEntity(viewer, () -> {
            setHomeIcon.setIcon(home.owner(), home.slot(), Optional.of(HomeIcon.of(material.name())));
            onDone.run();
        });
    }

    private void reset(PlayerRef viewer, Home home, Runnable onDone) {
        scheduler.onEntity(viewer, () -> {
            setHomeIcon.setIcon(home.owner(), home.slot(), Optional.empty());
            onDone.run();
        });
    }

    private ItemStack entryIcon(PlayerRef viewer, Material material) {
        return ItemBuilder.of(material)
                .name(text(viewer, HomesMessageKey.HOME_ICON_ENTRY_NAME, Map.of("icon", material.name())))
                .build();
    }

    private ItemStack resetIcon(PlayerRef viewer) {
        return ItemBuilder.of(layout.resetMaterial())
                .name(text(viewer, HomesMessageKey.HOME_ICON_RESET_NAME, Map.of()))
                .lore(List.of(text(viewer, HomesMessageKey.HOME_ICON_RESET_LORE, Map.of())))
                .build();
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(layout.navMaterial())
                .name(text(viewer, key, Map.of()))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

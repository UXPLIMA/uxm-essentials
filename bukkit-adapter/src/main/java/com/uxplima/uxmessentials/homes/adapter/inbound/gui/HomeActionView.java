package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The per-home action menu, opened by clicking a filled slot in the {@link HomeListView}. Buttons drive the
 * player-facing use cases on the viewer's entity thread: teleport, delete, relocate-here, rename (through a
 * vanilla anvil), change-icon (the {@link IconSelectorView}), a read-only info display, and a back button. Every
 * visible string resolves from a {@link MessageKey} in the viewer's locale, never an inline literal. The menu
 * touches the live player, so it builds and clicks on the viewer's entity thread through the kernel
 * {@link Scheduler}.
 */
@NullMarked
public final class HomeActionView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final TeleportHome teleportHome;
    private final DeleteHome deleteHome;
    private final RelocateHome relocateHome;
    private final RenameHome renameHome;
    private final IconSelectorView iconSelector;
    private final AnvilInput anvil;
    private final HomeActionsLayout layout;
    private final DateTimeFormatter dateFormat;
    private final MiniMessage miniMessage;

    public HomeActionView(
            Messages messages,
            Scheduler scheduler,
            TeleportHome teleportHome,
            DeleteHome deleteHome,
            RelocateHome relocateHome,
            RenameHome renameHome,
            IconSelectorView iconSelector,
            AnvilInput anvil,
            HomeActionsLayout layout,
            DateTimeFormatter dateFormat) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.teleportHome = Objects.requireNonNull(teleportHome, "teleportHome");
        this.deleteHome = Objects.requireNonNull(deleteHome, "deleteHome");
        this.relocateHome = Objects.requireNonNull(relocateHome, "relocateHome");
        this.renameHome = Objects.requireNonNull(renameHome, "renameHome");
        this.iconSelector = Objects.requireNonNull(iconSelector, "iconSelector");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.dateFormat = Objects.requireNonNull(dateFormat, "dateFormat");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the action menu for {@code home}; {@code reopenList} re-renders the grid after a mutating action. */
    public void open(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(reopenList, "reopenList");
        scheduler.onEntity(viewer, () -> {
            SimpleGui gui = Guis.gui()
                    .title(text(viewer, HomesMessageKey.HOME_ACTION_TITLE, slotName(home)))
                    .rows(layout.rows())
                    .build();
            fill(gui);
            placeButtons(player, viewer, home, reopenList, gui);
            gui.open(player);
        });
    }

    private void placeButtons(Player player, PlayerRef viewer, Home home, Runnable reopenList, SimpleGui gui) {
        gui.set(layout.infoSlot(), GuiItem.display(infoIcon(viewer, home)));
        gui.set(
                layout.teleportSlot(),
                GuiItem.button(button(viewer, home, true), e -> teleport(viewer, home, gui, player)));
        gui.set(
                layout.deleteSlot(),
                GuiItem.button(button(viewer, home, false), e -> delete(viewer, home, reopenList)));
        gui.set(
                layout.relocateSlot(),
                GuiItem.button(relocateIcon(viewer), e -> relocate(viewer, home, player, reopenList)));
        gui.set(layout.renameSlot(), GuiItem.button(renameIcon(viewer), e -> rename(player, viewer, home, reopenList)));
        gui.set(
                layout.changeIconSlot(),
                GuiItem.button(iconIcon(viewer), e -> changeIcon(player, viewer, home, reopenList)));
        gui.set(layout.backSlot(), GuiItem.button(backIcon(viewer), e -> reopenList.run()));
    }

    private void teleport(PlayerRef viewer, Home home, SimpleGui gui, Player player) {
        scheduler.onEntity(viewer, () -> {
            teleportHome.toSlot(viewer, home.slot());
            gui.close(player);
        });
    }

    private void delete(PlayerRef viewer, Home home, Runnable reopenList) {
        scheduler.onEntity(viewer, () -> {
            deleteHome.delete(home.owner(), home.slot());
            reopenList.run();
        });
    }

    private void relocate(PlayerRef viewer, Home home, Player player, Runnable reopenList) {
        scheduler.onEntity(viewer, () -> {
            org.bukkit.Location at = Objects.requireNonNull(player.getLocation(), "player location");
            relocateHome.relocate(home.owner(), home.slot(), BukkitRefs.toPosition(at));
            reopenList.run();
        });
    }

    private void changeIcon(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        iconSelector.open(player, viewer, home, () -> open(player, viewer, home, reopenList));
    }

    private void rename(Player player, PlayerRef viewer, Home home, Runnable reopenList) {
        scheduler.onEntity(
                viewer,
                () -> anvil.open(
                        player, renamePrompt(viewer), result -> onRenamed(player, viewer, home, reopenList, result)));
    }

    private void onRenamed(Player player, PlayerRef viewer, Home home, Runnable reopenList, AnvilResult result) {
        if (!(result instanceof AnvilResult.Submitted submitted)) {
            open(player, viewer, home, reopenList);
            return;
        }
        String typed = submitted.text().strip();
        if (typed.isEmpty() || typed.length() > HomeLabel.MAX_LENGTH) {
            messages.resolve(viewer, HomesMessageKey.HOME_RENAME_TOO_LONG, Map.of());
            renameHome.rename(home.owner(), home.slot(), Optional.empty());
            open(player, viewer, home, reopenList);
            return;
        }
        renameHome.rename(home.owner(), home.slot(), Optional.of(HomeLabel.of(typed)));
        open(player, viewer, home, reopenList);
    }

    private void fill(SimpleGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    private ItemStack infoIcon(PlayerRef viewer, Home home) {
        return ItemBuilder.of(layout.infoMaterial())
                .name(text(viewer, HomesMessageKey.HOME_ACTION_INFO_NAME, slotName(home)))
                .lore(List.of(
                        text(
                                viewer,
                                HomesMessageKey.HOME_ACTION_INFO_LORE_WORLD,
                                Map.of("world", home.location().world().name())),
                        text(viewer, HomesMessageKey.HOME_ACTION_INFO_LORE_COORDS, coords(home)),
                        text(viewer, HomesMessageKey.HOME_ACTION_INFO_LORE_CREATED, Map.of("created", created(home)))))
                .build();
    }

    private ItemStack button(PlayerRef viewer, Home home, boolean teleport) {
        MessageKey name =
                teleport ? HomesMessageKey.HOME_ACTION_TELEPORT_NAME : HomesMessageKey.HOME_ACTION_DELETE_NAME;
        MessageKey lore =
                teleport ? HomesMessageKey.HOME_ACTION_TELEPORT_LORE : HomesMessageKey.HOME_ACTION_DELETE_LORE;
        return ItemBuilder.of(teleport ? layout.teleportMaterial() : layout.deleteMaterial())
                .name(text(viewer, name, slotName(home)))
                .lore(List.of(text(viewer, lore, Map.of())))
                .build();
    }

    private ItemStack relocateIcon(PlayerRef viewer) {
        return labelled(
                viewer,
                layout.relocateMaterial(),
                HomesMessageKey.HOME_ACTION_RELOCATE_NAME,
                HomesMessageKey.HOME_ACTION_RELOCATE_LORE);
    }

    private ItemStack renameIcon(PlayerRef viewer) {
        return labelled(
                viewer,
                layout.renameMaterial(),
                HomesMessageKey.HOME_ACTION_RENAME_NAME,
                HomesMessageKey.HOME_ACTION_RENAME_LORE);
    }

    private ItemStack iconIcon(PlayerRef viewer) {
        return labelled(
                viewer,
                layout.changeIconMaterial(),
                HomesMessageKey.HOME_ACTION_ICON_NAME,
                HomesMessageKey.HOME_ACTION_ICON_LORE);
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(layout.backMaterial())
                .name(text(viewer, HomesMessageKey.HOME_ACTION_BACK_NAME, Map.of()))
                .build();
    }

    private ItemStack labelled(PlayerRef viewer, org.bukkit.Material material, MessageKey name, MessageKey lore) {
        return ItemBuilder.of(material)
                .name(text(viewer, name, Map.of()))
                .lore(List.of(text(viewer, lore, Map.of())))
                .build();
    }

    private ItemStack renamePrompt(PlayerRef viewer) {
        return ItemBuilder.of(layout.renameMaterial())
                .name(text(viewer, HomesMessageKey.HOME_RENAME_PROMPT, Map.of()))
                .build();
    }

    private Map<String, String> slotName(Home home) {
        String label = home.label()
                .map(HomeLabel::value)
                .orElseGet(() -> Integer.toString(home.slot().displayNumber()));
        return Map.of("home", label, "slot", Integer.toString(home.slot().displayNumber()));
    }

    private Map<String, String> coords(Home home) {
        return Map.of(
                "x", Integer.toString(home.location().blockX()),
                "y", Integer.toString(home.location().blockY()),
                "z", Integer.toString(home.location().blockZ()));
    }

    private String created(Home home) {
        return dateFormat.format(home.createdAt());
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

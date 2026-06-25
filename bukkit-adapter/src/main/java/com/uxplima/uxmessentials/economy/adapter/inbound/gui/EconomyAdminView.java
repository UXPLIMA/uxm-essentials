package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The hub the bare {@code /eco} opens (when GUIs are enabled): three entries — [Manage a player] opens the
 * shared {@link PlayerPickerView} and routes the picked target to the per-player screen, [Server-wide] opens the
 * bulk give-all / reset-all screen, and [Transaction history] opens the global transaction log. The raw
 * {@code /eco give|take|set|reset …} subcommands are untouched; this view is only the bare-root opener.
 *
 * <p>The picker's offline resolver is backed by {@link PlayerLookup}, so a staff member can type an offline
 * name the head grid does not show and still manage that player's wallet. The view holds the collaborator
 * views (target, bulk) and opens each on the viewer's entity thread.
 */
@NullMarked
public final class EconomyAdminView {

    private static final int ROWS = 3;
    private static final int MANAGE_SLOT = 11;
    private static final int BULK_SLOT = 13;
    private static final int HISTORY_SLOT = 15;
    private static final int CLOSE_SLOT = 22;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final PlayerPickerView picker;
    private final PlayerLookup players;
    private final EconomyTargetMenu targetMenu;
    private final EconomyBulkMenu bulkMenu;
    private final TransactionsHistoryMenu historyView;

    public EconomyAdminView(
            GuiText guiText,
            Scheduler scheduler,
            PlayerPickerView picker,
            PlayerLookup players,
            EconomyTargetMenu targetMenu,
            EconomyBulkMenu bulkMenu,
            TransactionsHistoryMenu historyView) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.players = Objects.requireNonNull(players, "players");
        this.targetMenu = Objects.requireNonNull(targetMenu, "targetMenu");
        this.bulkMenu = Objects.requireNonNull(bulkMenu, "bulkMenu");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
    }

    /** Open the admin hub for {@code viewer}. */
    public void open(Player viewer, PlayerRef viewerRef) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        scheduler.onEntity(viewerRef, () -> buildAndOpen(viewer, viewerRef));
    }

    private void buildAndOpen(Player viewer, PlayerRef viewerRef) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_TITLE))
                .rows(ROWS)
                .build();
        fill(gui);
        gui.set(
                MANAGE_SLOT,
                entry(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_MANAGE_NAME,
                        EconomyMessageKey.ECO_ADMIN_GUI_MANAGE_LORE,
                        Material.PLAYER_HEAD,
                        () -> openPicker(viewer, viewerRef)));
        gui.set(
                BULK_SLOT,
                entry(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_BULK_NAME,
                        EconomyMessageKey.ECO_ADMIN_GUI_BULK_LORE,
                        Material.BEACON,
                        () -> bulkMenu.open(viewer, viewerRef)));
        gui.set(
                HISTORY_SLOT,
                entry(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_HISTORY_NAME,
                        EconomyMessageKey.ECO_ADMIN_GUI_HISTORY_LORE,
                        Material.BOOK,
                        () -> openGlobalHistory(viewerRef)));
        gui.set(
                CLOSE_SLOT,
                GuiItem.button(closeIcon(viewerRef), e -> scheduler.onEntity(viewerRef, () -> gui.close(viewer))));
        gui.open(viewer);
    }

    private void openPicker(Player viewer, PlayerRef viewerRef) {
        PlayerPickerView.Request request = new PlayerPickerView.Request(
                EconomyMessageKey.ECO_ADMIN_GUI_PICK_TITLE,
                target -> targetMenu.open(viewer, viewerRef, target),
                this::resolveOffline,
                EconomyMessageKey.ECO_ADMIN_TARGET_UNKNOWN);
        picker.open(viewer, viewerRef, request);
    }

    private Optional<PlayerRef> resolveOffline(String name) {
        return players.findByName(name);
    }

    private void openGlobalHistory(PlayerRef viewerRef) {
        scheduler.onEntity(viewerRef, () -> historyView.open(viewerRef, null, "Global"));
    }

    private GuiItem entry(PlayerRef viewer, MessageKey nameKey, MessageKey loreKey, Material icon, Runnable onClick) {
        ItemStack item = ItemBuilder.of(icon)
                .name(guiText.text(viewer, nameKey))
                .lore(List.of(guiText.text(viewer, loreKey)))
                .build();
        return GuiItem.button(item, e -> onClick.run());
    }

    private ItemStack closeIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.BARRIER)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_CLOSE))
                .build();
    }

    private void fill(SimpleGui gui) {
        ItemStack filler = ItemBuilder.of(FILLER).name(Component.empty()).build();
        for (int slot = 0; slot < ROWS * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }
}

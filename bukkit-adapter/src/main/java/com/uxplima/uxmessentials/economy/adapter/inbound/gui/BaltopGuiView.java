package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * Interactive 6-row Chest GUI rendering the top balances leaderboard.
 */
@NullMarked
public final class BaltopGuiView {

    private final BaltopSnapshots snapshots;
    private final Scheduler scheduler;
    private final EconomyNotifier notifier;
    private final Messages messages;
    private final MiniMessage miniMessage;

    public BaltopGuiView(BaltopSnapshots snapshots, Scheduler scheduler, EconomyNotifier notifier, Messages messages) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Opens the Baltop GUI for the viewer.
     *
     * @param viewer target player who opens the menu
     * @param currency target currency to query top list
     */
    public void open(Player viewer, Currency currency) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());

        // Query top rows off-tick
        scheduler.async(() -> {
            // Retrieve top 100 entries from the snapshot cache
            List<BaltopRow> rows = snapshots.top(currency, 100);

            class RankedEntry {
                final int rank;
                final BaltopRow row;

                RankedEntry(int rank, BaltopRow row) {
                    this.rank = rank;
                    this.row = row;
                }
            }

            List<RankedEntry> entries = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                entries.add(new RankedEntry(i + 1, rows.get(i)));
            }

            scheduler.onEntity(viewerRef, () -> {
                Component titleText =
                        text(viewerRef, EconomyMessageKey.BALTOP_GUI_TITLE, Map.of("currency", currency.plural()));

                PaginatedGui gui = Guis.paginated()
                        .title(titleText)
                        .rows(6)
                        .contentSlots(List.of(
                                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                                24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44))
                        .build();

                // Populate entries (skulls)
                gui.populate(
                        entries,
                        ItemPopulator.of(entry -> buildSkullIcon(viewerRef, entry.rank, entry.row), (entry, event) -> {
                            /* Read-only GUI */
                        }));

                // Pagination buttons
                ItemStack prevItem = ItemBuilder.of(Material.ARROW)
                        .name(text(viewerRef, EconomyMessageKey.BALTOP_GUI_PREVIOUS, Map.of()))
                        .build();
                ItemStack nextItem = ItemBuilder.of(Material.ARROW)
                        .name(text(viewerRef, EconomyMessageKey.BALTOP_GUI_NEXT, Map.of()))
                        .build();
                ItemStack closeItem = ItemBuilder.of(Material.BARRIER)
                        .name(text(viewerRef, EconomyMessageKey.BALTOP_GUI_CLOSE, Map.of()))
                        .build();

                gui.set(45, GuiItem.previousPage(gui, prevItem));
                gui.set(49, GuiItem.button(closeItem, event -> {
                    scheduler.onEntity(viewerRef, () -> gui.close(viewer));
                }));
                gui.set(53, GuiItem.nextPage(gui, nextItem));

                // Empty fillers on bottom row
                ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build();
                for (int slot : List.of(46, 47, 48, 50, 51, 52)) {
                    gui.set(slot, GuiItem.display(filler));
                }

                gui.open(viewer);
            });
        });
    }

    private ItemStack buildSkullIcon(PlayerRef viewer, int rank, BaltopRow row) {
        String formattedAmount = notifier.amount(row.balance());

        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skull(SkullData.ofUuid(row.owner().uuid()))
                .name(text(
                        viewer,
                        EconomyMessageKey.BALTOP_GUI_ENTRY_NAME,
                        Map.of(
                                "rank", Integer.toString(rank),
                                "player", row.owner().name())))
                .lore(List.of(text(viewer, EconomyMessageKey.BALTOP_GUI_ENTRY_LORE, Map.of("amount", formattedAmount))))
                .build();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}

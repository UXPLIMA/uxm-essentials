package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Interactive chest GUI rendering transaction history, admin logs, or a bank's logs. The three entry points
 * differ only in the records they query and the title; the page layout, nav buttons, and entry icons resolve
 * every line through a {@code MessageKey} in the viewer's locale, never an inline literal.
 */
@NullMarked
public final class TransactionsHistoryView {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    // The close button sits in the reserved bottom row; rows, nav slots, content slots, and icons are
    // externalised to modules/economy/gui/transaction-logs.conf.
    private static final int CLOSE_SLOT = 49;
    private static final List<Integer> FILLER_SLOTS = List.of(46, 47, 48, 50, 51, 52);

    private final TransactionHistory history;
    private final Scheduler scheduler;
    private final Messages messages;
    private final GuiLayout layout;
    private final MiniMessage miniMessage;

    public TransactionsHistoryView(
            TransactionHistory history, Scheduler scheduler, Messages messages, GuiLayout layout) {
        this.history = Objects.requireNonNull(history, "history");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Opens the transaction history GUI for the viewer; {@code targetPlayer} null renders the global log. */
    public void open(Player viewer, @Nullable UUID targetPlayer, String targetName) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        scheduler.async(() -> {
            history.flush();
            List<HistoryRecord> records = targetPlayer == null
                    ? history.queryGlobalTransactions(200, 0)
                    : history.queryTransactions(targetPlayer, 200, 0);
            Component title = targetPlayer == null
                    ? text(viewerRef, EconomyMessageKey.HISTORY_GUI_TITLE_GLOBAL, Map.of())
                    : text(viewerRef, EconomyMessageKey.HISTORY_GUI_TITLE_PLAYER, Map.of("player", targetName));
            scheduler.onEntity(viewerRef, () -> renderPage(viewer, viewerRef, title, records));
        });
    }

    /** Opens the bank transaction logs GUI for the viewer. */
    public void openForBank(Player viewer, String bankId, String bankName) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        scheduler.async(() -> {
            history.flush();
            List<HistoryRecord> records = history.queryBankTransactions(bankId, 200, 0);
            Component title = text(viewerRef, EconomyMessageKey.HISTORY_GUI_TITLE_BANK, Map.of("bank", bankName));
            scheduler.onEntity(viewerRef, () -> renderPage(viewer, viewerRef, title, records));
        });
    }

    private void renderPage(Player viewer, PlayerRef viewerRef, Component title, List<HistoryRecord> records) {
        Guis.PaginatedBuilder builder = Guis.paginated().title(title).rows(layout.rows());
        layout.explicitContentSlots().ifPresent(builder::contentSlots);
        PaginatedGui gui = builder.build();

        gui.populate(records, ItemPopulator.of(record -> icon(viewerRef, record), (record, event) -> {
            /* read-only GUI */
        }));

        gui.set(
                layout.prevSlot(),
                GuiItem.previousPage(
                        gui,
                        ItemBuilder.of(layout.navIcon())
                                .name(text(viewerRef, EconomyMessageKey.HISTORY_GUI_PREV, Map.of()))
                                .build()));
        gui.set(
                CLOSE_SLOT,
                GuiItem.button(
                        ItemBuilder.of(Material.BARRIER)
                                .name(text(viewerRef, EconomyMessageKey.HISTORY_GUI_CLOSE, Map.of()))
                                .build(),
                        event -> scheduler.onEntity(viewerRef, () -> gui.close(viewer))));
        gui.set(
                layout.nextSlot(),
                GuiItem.nextPage(
                        gui,
                        ItemBuilder.of(layout.navIcon())
                                .name(text(viewerRef, EconomyMessageKey.HISTORY_GUI_NEXT, Map.of()))
                                .build()));

        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int slot : FILLER_SLOTS) {
            gui.set(slot, GuiItem.display(filler));
        }
        gui.open(viewer);
    }

    private ItemStack icon(PlayerRef viewer, HistoryRecord record) {
        Material mat = iconMaterial(record);
        String sender =
                record.fromName() != null ? record.fromName() : (record.fromUuid() != null ? record.fromUuid() : "-");
        String receiver = record.toName() != null ? record.toName() : (record.toUuid() != null ? record.toUuid() : "-");
        String amountText = record.amount().toPlainString() + " " + record.currency();
        return ItemBuilder.of(mat)
                .name(typeLabel(viewer, record))
                .lore(List.of(
                        text(viewer, EconomyMessageKey.HISTORY_GUI_LORE_ID, Map.of("id", Long.toString(record.id()))),
                        text(
                                viewer,
                                EconomyMessageKey.HISTORY_GUI_LORE_DATE,
                                Map.of("date", FORMATTER.format(record.timestamp()))),
                        text(viewer, EconomyMessageKey.HISTORY_GUI_LORE_FROM, Map.of("from", sender)),
                        text(viewer, EconomyMessageKey.HISTORY_GUI_LORE_TO, Map.of("to", receiver)),
                        text(viewer, EconomyMessageKey.HISTORY_GUI_LORE_AMOUNT, Map.of("amount", amountText)),
                        text(viewer, EconomyMessageKey.HISTORY_GUI_LORE_REASON, Map.of("reason", record.reason()))))
                .build();
    }

    private Material iconMaterial(HistoryRecord record) {
        if (record.reason().startsWith("ADMIN_")) {
            return Material.GOLD_BLOCK;
        }
        if ("CREDIT".equalsIgnoreCase(record.type())) {
            return Material.GREEN_WOOL;
        }
        if ("DEBIT".equalsIgnoreCase(record.type())) {
            return Material.RED_WOOL;
        }
        return Material.PAPER;
    }

    private Component typeLabel(PlayerRef viewer, HistoryRecord record) {
        EconomyMessageKey key;
        if (record.reason().startsWith("ADMIN_")) {
            key = EconomyMessageKey.HISTORY_GUI_TYPE_ADMIN;
        } else if ("CREDIT".equalsIgnoreCase(record.type())) {
            key = EconomyMessageKey.HISTORY_GUI_TYPE_CREDIT;
        } else if ("DEBIT".equalsIgnoreCase(record.type())) {
            key = EconomyMessageKey.HISTORY_GUI_TYPE_DEBIT;
        } else {
            key = EconomyMessageKey.HISTORY_GUI_TYPE_TRANSFER;
        }
        return text(viewer, key, Map.of());
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(viewer, key, placeholders))
                .decoration(TextDecoration.ITALIC, false);
    }
}

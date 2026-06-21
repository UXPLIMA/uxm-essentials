package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The mailbox (opened by {@code /mail} with no arguments, or the mailbox entry on the {@code /uxmess gui} hub): a
 * config-driven, paginated grid of the viewer's mail newest-first, drawn through the shared {@link EntityListView}.
 * Opening it marks the box read — the GUI equivalent of {@code /mail read} — so clicking a page opens its
 * read-only detail rather than re-marking. The list's create button is repurposed as a confirm-gated "clear all"
 * that empties the box through the existing {@code ClearMail} use case.
 *
 * <p>The box is a DB read, so the open loads it off the tick thread (marking it read in the same hop) and hops
 * back to the viewer's entity thread to render. The per-mail detail is a small fixed sub-menu whose internal
 * geometry is a code default (one icon, one back button) — the same convention the property sub-menus follow;
 * the paged list geometry that matters comes from the conf.
 */
@NullMarked
public final class MailboxView {

    /** The detail sub-menu's fixed geometry: a three-row menu with the mail icon centred and a back button below. */
    private static final int DETAIL_ROWS = 3;

    private static final int DETAIL_ICON_SLOT = 13;
    private static final int DETAIL_BACK_SLOT = 22;

    /** How many characters of the body the list lore previews before eliding. */
    private static final int SNIPPET_LIMIT = 40;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final MailRepository mail;
    private final ClearMail clearMail;
    private final AtomicReference<List<MailItem>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<MailItem> view;

    public MailboxView(
            GuiText guiText, Scheduler scheduler, MailRepository mail, ClearMail clearMail, EntityListLayout layout) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mail = Objects.requireNonNull(mail, "mail");
        this.clearMail = Objects.requireNonNull(clearMail, "clearMail");
        Objects.requireNonNull(layout, "layout");
        this.view = EntityListView.<MailItem>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(MessagingMessageKey.GUI_MAIL_TITLE)
                .navNames(MessagingMessageKey.GUI_MAIL_PREV, MessagingMessageKey.GUI_MAIL_NEXT)
                .entities(snapshot::get)
                .iconRenderer(this::icon)
                .onSelect(this::openDetail)
                .onCreate(MessagingMessageKey.GUI_MAIL_CLEAR, this::confirmClear)
                .build();
    }

    /** Load the viewer's box off-thread (marking it read), then open the list on their entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            MailBox box = mail.load(viewer);
            snapshot.set(box.items());
            // Opening the mailbox is the GUI's read action; mark every item read so the count clears, mirroring
            // /mail read. The icons still render from the loaded (pre-mark) snapshot so unread mail reads as new.
            mail.markAllRead(viewer);
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }

    private ItemStack icon(PlayerRef viewer, MailItem item) {
        Map<String, String> placeholders = Map.of(
                "sender", item.sender().name(),
                "time", TIME.format(item.sentAt()),
                "snippet", snippet(item.body().value()),
                "message", item.body().value());
        Material material = item.read() ? Material.PAPER : Material.WRITTEN_BOOK;
        return ItemBuilder.of(material)
                .name(guiText.text(viewer, MessagingMessageKey.GUI_MAIL_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, MessagingMessageKey.GUI_MAIL_ENTRY_LORE, placeholders))
                .build();
    }

    private void openDetail(Player player, MailItem item) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        Map<String, String> placeholders = Map.of(
                "sender", item.sender().name(),
                "time", TIME.format(item.sentAt()),
                "message", item.body().value());
        ItemStack icon = ItemBuilder.of(item.read() ? Material.PAPER : Material.WRITTEN_BOOK)
                .name(guiText.text(viewer, MessagingMessageKey.GUI_MAIL_DETAIL_NAME, placeholders))
                .lore(guiText.text(viewer, MessagingMessageKey.GUI_MAIL_DETAIL_LORE, placeholders))
                .build();
        ItemStack back = ItemBuilder.of(Material.ARROW)
                .name(guiText.text(viewer, MessagingMessageKey.GUI_MAIL_DETAIL_BACK))
                .build();
        ItemStack filler = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).build();
        SimpleGui detail = Guis.gui()
                .title(guiText.text(viewer, MessagingMessageKey.GUI_MAIL_DETAIL_TITLE))
                .rows(DETAIL_ROWS)
                .build();
        for (int slot = 0; slot < DETAIL_ROWS * 9; slot++) {
            detail.set(slot, GuiItem.display(filler));
        }
        detail.set(DETAIL_ICON_SLOT, GuiItem.display(icon));
        detail.set(DETAIL_BACK_SLOT, GuiItem.button(back, e -> open(player, viewer)));
        detail.open(player);
    }

    private void confirmClear(Player player) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        ConfirmMenu.of(
                        guiText.text(viewer, MessagingMessageKey.GUI_MAIL_CLEAR_CONFIRM),
                        () -> scheduler.async(() -> {
                            clearMail.clear(viewer);
                            open(player, viewer);
                        }),
                        () -> open(player, viewer))
                .open(player);
    }

    private static String snippet(String body) {
        String trimmed = body.strip();
        if (trimmed.length() <= SNIPPET_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_LIMIT) + "…";
    }
}

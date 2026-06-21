package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A target's full disciplinary history: a config-driven, paginated, read-only grid drawn through the shared
 * {@link EntityListView} over the append-only {@link SanctionHistory} — the same newest-first timeline the
 * {@code /history} command renders, here as one icon per entry showing the action, the actor, the reason, and
 * the timestamp. The history read is a DB read, so the open resolves it off the tick thread and hops back to
 * the viewer's entity thread to render.
 *
 * <p>Read-only: there is no create button, and a click on an entry does nothing (the entry is an immutable
 * audit row). The view holds no domain logic — the entity supplier is the same history query the command uses.
 */
@NullMarked
public final class PlayerHistoryView {

    /** Mirrors the per-page cap the {@code /history} read uses, so the off-thread read stays bounded. */
    private static final int PAGE_LIMIT = 50;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Scheduler scheduler;
    private final SanctionHistory history;
    private final AtomicReference<List<SanctionHistoryEntry>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<SanctionHistoryEntry> view;

    public PlayerHistoryView(GuiText guiText, Scheduler scheduler, SanctionHistory history, EntityListLayout layout) {
        Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.history = Objects.requireNonNull(history, "history");
        Objects.requireNonNull(layout, "layout");
        this.view = EntityListView.<SanctionHistoryEntry>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(ModerationMessageKey.MOD_GUI_HISTORY_TITLE)
                .navNames(ModerationMessageKey.MOD_GUI_HISTORY_PREV, ModerationMessageKey.MOD_GUI_HISTORY_NEXT)
                .entities(snapshot::get)
                .iconRenderer((viewer, entry) -> icon(guiText, viewer, entry))
                .onSelect((player, entry) -> {
                    // An audit row is immutable; selecting it is a no-op so the menu stays put.
                })
                .build();
    }

    /** Resolve {@code target}'s history off-thread, then open the read-only list on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, UUID target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        scheduler.async(() -> {
            snapshot.set(List.copyOf(history.recentForTarget(target, PAGE_LIMIT)));
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }

    private ItemStack icon(GuiText guiText, PlayerRef viewer, SanctionHistoryEntry entry) {
        Map<String, String> placeholders = Map.of(
                "action", entry.action().name(),
                "issuer", entry.actor().name(),
                "reason", entry.reason().filter(r -> !r.isBlank()).orElse(""),
                "at", STAMP.format(entry.at()));
        return ItemBuilder.of(material(entry))
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_HISTORY_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, ModerationMessageKey.MOD_GUI_HISTORY_ENTRY_LORE, placeholders))
                .build();
    }

    private Material material(SanctionHistoryEntry entry) {
        return switch (entry.action()) {
            case BAN -> Material.BARRIER;
            case UNBAN -> Material.LIME_DYE;
            case MUTE -> Material.BOOK;
            case UNMUTE -> Material.WRITABLE_BOOK;
            case WARN -> Material.PAPER;
            case KICK -> Material.LEATHER_BOOTS;
        };
    }
}

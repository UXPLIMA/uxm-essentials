package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.time.Clock;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Assembles the moderation management GUI's three views — the active-punishments list, the per-punishment
 * detail/manage view, and a target's read-only history — and threads the navigation between them. The list
 * opens by default ({@code /mod} with no args and the {@code /uxmess gui} hub entry); a click drills into the
 * detail view, which can revoke (confirm-gated) or open the clicked target's history. Layouts come from the
 * module's {@code gui/*.conf} (operator-editable, code default otherwise); every visible string is a catalog
 * key. The views are constructed once here and reused for every viewer.
 *
 * <p>The list↔detail cycle is broken with a one-slot holder: the detail view's back/history callbacks read the
 * list (and re-resolve a viewer from the live player) at click time, by which point the list is built. This
 * mirrors the holograms/npc/playerwarps GUI wiring.
 */
@NullMarked
public final class ModerationGuiViews {

    private static final String MODULE = "moderation";

    private final ActivePunishmentsView list;

    private ModerationGuiViews(ActivePunishmentsView list) {
        this.list = list;
    }

    /** Build the three views over the existing use cases and the module's GUI layouts. */
    public static ModerationGuiViews create(
            GuiText guiText,
            Scheduler scheduler,
            ModerationServices services,
            ModerationRepository repository,
            PlayerLookup players,
            SanctionHistory history,
            Clock clock,
            GuiLayouts layouts) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(layouts, "layouts");

        EntityListLayout listLayout = layouts.loadEntityList(MODULE, "punishments-list", listCodeDefault());
        EntityEditorLayout detailLayout = layouts.loadEntityEditor(MODULE, "punishment-detail", detailCodeDefault());
        EntityListLayout historyLayout = layouts.loadEntityList(MODULE, "player-history", historyCodeDefault());

        PlayerHistoryView historyView = new PlayerHistoryView(guiText, scheduler, history, historyLayout);

        // Revoking routes by kind to the existing audited use cases the /unban /unmute /unjail commands take.
        PunishmentRevoker revoker = (actor, target, kind) -> {
            switch (kind) {
                case BAN -> services.unban().unban(actor, target);
                case MUTE -> services.unmute().unmute(actor, target);
                case JAIL -> services.unjail().unjail(actor, target);
            }
        };

        // Break the list↔detail cycle with a one-slot holder: the detail view's back callback reads the list at
        // click time, by which point it is constructed. This mirrors the npc/playerwarps GUI wiring.
        ActivePunishmentsView[] listHolder = new ActivePunishmentsView[1];
        PunishmentDetailView detail = new PunishmentDetailView(
                guiText,
                scheduler,
                revoker,
                clock,
                detailLayout,
                (player, viewer) -> listHolder[0].open(player, viewer),
                (player, punishment) -> historyView.open(
                        player,
                        com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toRef(player),
                        punishment.target()));

        ActivePunishmentsView list =
                new ActivePunishmentsView(guiText, scheduler, repository, players, clock, listLayout, detail);
        listHolder[0] = list;
        return new ModerationGuiViews(list);
    }

    /** Open the active-punishments list — the management GUI's entry point — for {@code viewer}. */
    public void open(Player player, PlayerRef viewer) {
        list.open(player, viewer);
    }

    private static EntityListLayout listCodeDefault() {
        return EntityListLayout.paginatedDefault(Material.BARRIER);
    }

    private static EntityListLayout historyCodeDefault() {
        return EntityListLayout.paginatedDefault(Material.PAPER);
    }

    private static EntityEditorLayout detailCodeDefault() {
        // Six properties (target, type, issuer, reason, remaining, history) across the upper rows, a back button,
        // and a revoke button wired to the editor's delete slot — the framework confirm-gates the revoke.
        return new EntityEditorLayout(
                3,
                java.util.List.of(10, 11, 12, 13, 14, 16),
                22,
                java.util.OptionalInt.of(26),
                Material.ARROW,
                Material.LAVA_BUCKET,
                Material.BLACK_STAINED_GLASS_PANE);
    }
}

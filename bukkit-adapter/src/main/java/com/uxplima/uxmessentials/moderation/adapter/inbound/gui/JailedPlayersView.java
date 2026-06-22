package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The jailed-players management list (capability C of the {@code /jail} GUI): a config-driven, paginated grid of
 * every currently-jailed player drawn through the shared {@link EntityListView}, each icon showing the target,
 * the jail, the issuer, the reason and the remaining time. A click releases that player through the same audited
 * {@code Unjail} use case {@code /unjail} takes, then reopens the refreshed list so the released row disappears.
 *
 * <p>The active-jail read ({@code activeJails}) is a DB query, so the open resolves it off the tick thread,
 * resolves each target's display name through the {@link PlayerLookup}, and hops back to the viewer's entity
 * thread to render — exactly the bounded read {@code /jailedplayers} runs, here turned into a clickable list.
 * The view holds no domain logic: the entity supplier is the repository query, and a click hands off to the
 * release use case.
 */
@NullMarked
public final class JailedPlayersView {

    /** Mirrors the per-list page cap the {@code /jailedplayers} read uses, so the off-thread read stays bounded. */
    private static final int PAGE_LIMIT = 50;

    private final Scheduler scheduler;
    private final ModerationServices services;
    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Clock clock;
    private final AtomicReference<List<JailEntry>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<JailEntry> view;

    public JailedPlayersView(
            GuiText guiText,
            Scheduler scheduler,
            ModerationServices services,
            ModerationRepository repository,
            PlayerLookup players,
            Clock clock,
            EntityListLayout layout) {
        Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.services = Objects.requireNonNull(services, "services");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(layout, "layout");
        this.view = EntityListView.<JailEntry>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(ModerationMessageKey.MOD_GUI_JAILED_TITLE)
                .navNames(ModerationMessageKey.MOD_GUI_JAILED_PREV, ModerationMessageKey.MOD_GUI_JAILED_NEXT)
                .entities(snapshot::get)
                .iconRenderer((viewer, entry) -> icon(guiText, viewer, entry))
                .onSelect(this::release)
                .build();
    }

    /** Resolve the active jails off-thread, then open the list on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            snapshot.set(repository.activeJails(clock.instant(), PAGE_LIMIT));
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }

    /** Release the clicked target through the audited {@code Unjail} use case, then reopen the refreshed list. */
    private void release(Player player, JailEntry entry) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        PlayerRef target = new PlayerRef(entry.target(), targetName(entry));
        services.unjail().unjail(viewer, target);
        open(player, viewer);
    }

    private ItemStack icon(GuiText guiText, PlayerRef viewer, JailEntry entry) {
        Map<String, String> placeholders = Map.of(
                "player", targetName(entry),
                "jail", entry.jail(),
                "issuer", entry.issuer().name(),
                "reason", entry.reason().filter(r -> !r.isBlank()).orElse(""),
                "remaining", remaining(entry));
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAILED_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAILED_ENTRY_LORE, placeholders))
                .skull(com.uxplima.uxmlib.item.SkullData.ofUuid(entry.target()))
                .build();
    }

    private String remaining(JailEntry entry) {
        if (entry.until().isEmpty() && entry.remaining().isEmpty()) {
            return "permanent";
        }
        Duration left = entry.until()
                .map(until -> Duration.between(clock.instant(), until))
                .or(entry::remaining)
                .orElse(Duration.ZERO);
        return SanctionDuration.format(left.isNegative() ? Duration.ZERO : left);
    }

    private String targetName(JailEntry entry) {
        return players.findByUuid(entry.target())
                .map(PlayerRef::name)
                .orElseGet(() -> entry.target().toString());
    }
}

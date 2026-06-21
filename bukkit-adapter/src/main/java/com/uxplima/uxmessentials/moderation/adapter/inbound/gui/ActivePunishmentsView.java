package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The active-punishments management list: a config-driven, paginated grid of every currently-active ban, mute,
 * and jail drawn through the shared {@link EntityListView}, each icon showing the target, the kind, the
 * issuer, the reason, and the remaining time, and opening {@link PunishmentDetailView} on click. The three
 * active read models ({@code activeBans} / {@code activeMutes} / {@code activeJails}) are DB reads, so the open
 * resolves them off the tick thread, resolves each target's display name through the {@link PlayerLookup}, and
 * hops back to the viewer's entity thread to render — exactly the bounded reads {@code /banlist} /
 * {@code /mutelist} / {@code /jailedplayers} run, here folded into one list.
 *
 * <p>The view holds no domain logic: the entity supplier is the same repository query the list commands use,
 * and a click hands off to the detail view. There is no create button — punishments are issued by the existing
 * {@code /ban} / {@code /mute} / {@code /jail} commands, not the management list.
 */
@NullMarked
public final class ActivePunishmentsView {

    /** Mirrors the per-list page cap the {@code /banlist} read uses, so the off-thread read stays bounded. */
    private static final int PAGE_LIMIT = 50;

    private final Scheduler scheduler;
    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Clock clock;
    private final AtomicReference<List<ActivePunishment>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<ActivePunishment> view;

    public ActivePunishmentsView(
            GuiText guiText,
            Scheduler scheduler,
            ModerationRepository repository,
            PlayerLookup players,
            Clock clock,
            EntityListLayout layout,
            PunishmentDetailView detail) {
        Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(detail, "detail");
        this.view = EntityListView.<ActivePunishment>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(ModerationMessageKey.MOD_GUI_LIST_TITLE)
                .navNames(ModerationMessageKey.MOD_GUI_LIST_PREV, ModerationMessageKey.MOD_GUI_LIST_NEXT)
                .entities(snapshot::get)
                .iconRenderer((viewer, punishment) -> icon(guiText, viewer, punishment))
                .onSelect((player, punishment) -> detail.open(
                        player, com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toRef(player), punishment))
                .build();
    }

    /** Resolve the active punishments off-thread, then open the list on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            snapshot.set(resolve());
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }

    private List<ActivePunishment> resolve() {
        java.time.Instant now = clock.instant();
        List<ActivePunishment> all = new ArrayList<>();
        for (BanEntry ban : repository.activeBans(now, PAGE_LIMIT)) {
            all.add(ActivePunishment.ofBan(ban, name(ban.target())));
        }
        for (MuteEntry mute : repository.activeMutes(now, PAGE_LIMIT)) {
            all.add(ActivePunishment.ofMute(mute, name(mute.target())));
        }
        for (JailEntry jail : repository.activeJails(now, PAGE_LIMIT)) {
            all.add(ActivePunishment.ofJail(jail, name(jail.target())));
        }
        return List.copyOf(all);
    }

    private String name(java.util.UUID target) {
        return players.findByUuid(target).map(PlayerRef::name).orElseGet(target::toString);
    }

    private ItemStack icon(GuiText guiText, PlayerRef viewer, ActivePunishment punishment) {
        if (punishment == null) {
            return ItemBuilder.of(Material.BARRIER).build();
        }
        Map<String, String> placeholders = Map.of(
                "player", punishment.targetName(),
                "type", plain(guiText, viewer, punishment.kind().label()),
                "issuer", punishment.issuer().name(),
                "reason", punishment.reason().filter(r -> !r.isBlank()).orElse(""),
                "remaining", remaining(guiText, viewer, punishment));
        Material material =
                switch (punishment.kind()) {
                    case BAN -> Material.BARRIER;
                    case MUTE -> Material.BOOK;
                    case JAIL -> Material.IRON_BARS;
                };
        return ItemBuilder.of(material)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_LIST_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, ModerationMessageKey.MOD_GUI_LIST_ENTRY_LORE, placeholders))
                .build();
    }

    private String remaining(GuiText guiText, PlayerRef viewer, ActivePunishment punishment) {
        if (punishment.isPermanent()) {
            return plain(guiText, viewer, ModerationMessageKey.MOD_GUI_VALUE_PERMANENT);
        }
        Duration left = punishment
                .until()
                .map(until -> Duration.between(clock.instant(), until))
                .or(punishment::remaining)
                .orElse(Duration.ZERO);
        return SanctionDuration.format(left.isNegative() ? Duration.ZERO : left);
    }

    private String plain(GuiText guiText, PlayerRef viewer, ModerationMessageKey key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(guiText.text(viewer, key));
    }
}

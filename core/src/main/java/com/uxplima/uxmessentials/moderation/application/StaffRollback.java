package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /staffrollback <staff> [limit]}: revoke a (rogue or mistaken) staff member's still-active sanctions —
 * un-ban, un-mute and clear-warns every target they sanctioned that is currently still under that sanction.
 * The append-only history records every sanction a staff member ever applied but carries <em>no</em> active
 * flag, so each revoke is gated by a live-state read against the DB-backed sanction store at the injected
 * {@link Clock}: a ban already lifted (by an {@code /unban}, an {@code /unbanip}, or a since-lapsed timed
 * sentence), or re-applied by another staff member then lifted, reads as not-active and is left alone. Only what
 * is in effect <em>now</em> is undone, so the rollback never resurrects a sanction or emits a spurious "unbanned
 * X" for a player who was not banned.
 *
 * <p>Targets are deduped per action through a {@link Set} of UUIDs: a target the staff member banned twice (or
 * banned then re-banned) is un-banned once. {@link SanctionAction#UNBAN}, {@link SanctionAction#UNMUTE} and
 * {@link SanctionAction#KICK} rows are skipped — a kick is a live disconnect with nothing to undo, and a lift is
 * itself not a sanction. The {@code limit} caps how far back the history read reaches (newest-first), so an
 * operator can scope a rollback to a staff member's recent activity. The result is a {@link RollbackSummary} of
 * the counts undone, reported to the actor; when nothing was in effect the actor is told so instead.
 */
public final class StaffRollback {

    private final SanctionHistory history;
    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Unban unban;
    private final Unmute unmute;
    private final ClearWarns clearWarns;
    private final ModerationNotifier notifier;
    private final Clock clock;

    public StaffRollback(
            SanctionHistory history,
            ModerationRepository repository,
            PlayerLookup players,
            Unban unban,
            Unmute unmute,
            ClearWarns clearWarns,
            ModerationNotifier notifier,
            Clock clock) {
        this.history = Objects.requireNonNull(history, "history");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.unban = Objects.requireNonNull(unban, "unban");
        this.unmute = Objects.requireNonNull(unmute, "unmute");
        this.clearWarns = Objects.requireNonNull(clearWarns, "clearWarns");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Revoke {@code staff}'s still-active sanctions over their most recent {@code limit} history rows. */
    public RollbackSummary rollback(PlayerRef actor, PlayerRef staff, int limit) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(staff, "staff");
        Instant now = clock.instant();
        List<SanctionHistoryEntry> rows = history.recentByActor(staff.uuid(), limit);
        Set<UUID> unbanned = new HashSet<>();
        Set<UUID> unmuted = new HashSet<>();
        Set<UUID> cleared = new HashSet<>();
        int bans = 0;
        int mutes = 0;
        int warns = 0;
        for (SanctionHistoryEntry row : rows) {
            switch (row.action()) {
                case BAN -> bans += revokeBan(actor, row.target(), now, unbanned);
                case MUTE -> mutes += revokeMute(actor, row.target(), now, unmuted);
                case WARN -> warns += revokeWarns(actor, row.target(), now, cleared);
                case UNBAN, UNMUTE, KICK -> {
                    // A lift is not a sanction and a kick is a live disconnect — nothing to undo.
                }
            }
        }
        RollbackSummary summary = new RollbackSummary(bans, mutes, warns);
        notify(actor, staff, summary);
        return summary;
    }

    private int revokeBan(PlayerRef actor, UUID target, Instant now, Set<UUID> seen) {
        if (!seen.add(target)) {
            return 0;
        }
        PlayerRef ref = ref(target);
        if (repository.loadTempban(ref) instanceof TempbanState.Active active && active.isActiveAt(now)) {
            unban.unban(actor, ref);
            return 1;
        }
        return 0;
    }

    private int revokeMute(PlayerRef actor, UUID target, Instant now, Set<UUID> seen) {
        if (!seen.add(target)) {
            return 0;
        }
        PlayerRef ref = ref(target);
        if (repository.loadMute(ref).isActiveAt(now)) {
            unmute.unmute(actor, ref);
            return 1;
        }
        return 0;
    }

    private int revokeWarns(PlayerRef actor, UUID target, Instant now, Set<UUID> seen) {
        if (!seen.add(target)) {
            return 0;
        }
        PlayerRef ref = ref(target);
        if (repository.warns(ref, now).isEmpty()) {
            return 0;
        }
        clearWarns.clear(actor, ref);
        return 1;
    }

    private void notify(PlayerRef actor, PlayerRef staff, RollbackSummary summary) {
        if (summary.total() == 0) {
            notifier.send(actor, ModerationMessageKey.MOD_STAFFROLLBACK_NONE, Map.of("staff", staff.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.MOD_STAFFROLLBACK_SUMMARY,
                Map.of(
                        "staff", staff.name(),
                        "bans", Integer.toString(summary.bans()),
                        "mutes", Integer.toString(summary.mutes()),
                        "warns", Integer.toString(summary.warns()),
                        "total", Integer.toString(summary.total())));
    }

    /** The target as a {@link PlayerRef}, name-resolved through the lookup or the raw UUID when unknown. */
    private PlayerRef ref(UUID target) {
        return players.findByUuid(target).orElseGet(() -> new PlayerRef(target, target.toString()));
    }

    /**
     * The tally a {@code /staffrollback} undid: the count of bans lifted, mutes lifted and warn-sets cleared,
     * each deduped per target. {@link #total()} folds the three for the summary line.
     *
     * @param bans the number of distinct targets un-banned
     * @param mutes the number of distinct targets un-muted
     * @param warns the number of distinct targets whose warnings were cleared
     */
    public record RollbackSummary(int bans, int mutes, int warns) {

        /** The combined count of sanctions revoked across all three kinds. */
        public int total() {
            return bans + mutes + warns;
        }
    }
}

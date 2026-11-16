package com.uxplima.uxmessentials.moderation.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;

/**
 * The DB-backed append-only sanction-history store (the same hard moderation invariant as the sanction state:
 * it survives restart, never PDC). Kept a separate port from {@link ModerationRepository} so the history
 * concern stays isolated. Recording one row never touches the active-state tables, and a read never widens
 * the repository's surface.
 *
 * <p>Each row records a single ban/unban/mute/unmute/warn/kick applied to a target; nothing is ever updated.
 * The family reads scope to a family (ban vs mute) for {@code /banhistory}/{@code /mutehistory}; the unified
 * {@code /history} read folds every kind for one target, and {@code /staffhistory} folds every kind issued by
 * one staff member. All reads return newest-first and are capped at {@code limit} so the read stays within the
 * main-thread budget (the command hops it off-tick regardless).
 */
public interface SanctionHistory {

    /** Append one history row. The store assigns the row's synthetic key; the row is never updated after. */
    void append(SanctionHistoryEntry entry);

    /**
     * Append several rows in one transaction, for a command that sanctions many players at once ({@code /kickall}).
     * The store assigns the keys for the whole batch, so a large batch costs one write rather than one per row.
     */
    default void appendAll(List<SanctionHistoryEntry> entries) {
        entries.forEach(this::append);
    }

    /** {@code target}'s ban-family rows (ban and unban) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> banHistory(UUID target, int limit);

    /** {@code target}'s mute-family rows (mute and unmute) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> muteHistory(UUID target, int limit);

    /** {@code target}'s rows of every kind (the unified {@code /history}) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> recentForTarget(UUID target, int limit);

    /** Rows of every kind issued by {@code actor} ({@code /staffhistory}) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> recentByActor(UUID actor, int limit);

    /**
     * The player bans, mutes and warnings {@code actor} issued, newest-first, capped at {@code limit}: the rows
     * {@code /staffrollback} can undo. IP-scoped rows, kicks and lifts are left out by the read itself, so they
     * never use up the limit.
     */
    List<SanctionHistoryEntry> revocableByActor(UUID actor, int limit);

    /**
     * How many punishments each staff member issued at or after {@code threshold}, counted by the store so no row is
     * left out by a read limit: the read side of {@code /modstats}. One {@link Tally} per issuer name and action;
     * {@code actor} narrows it to one staff member. Only bans, mutes, warnings and kicks are counted, and as actions
     * rather than rows:
     *
     * <ul>
     *   <li>the kicks of one {@code /kickall} share one instant and count once;
     *   <li>an IP ban row counts unless the same staff member's player ban of the same player was written at the same
     *       instant, so a {@code /banip} counts and the addresses a STRICT {@code /ban} reached do not.
     * </ul>
     */
    List<Tally> countsSince(Instant threshold, Optional<UUID> actor);

    /**
     * One staff member's count of one action.
     *
     * @param actor the issuer's UUID, or empty for the console
     * @param actorName the name the issuer had on these rows
     * @param action the action counted
     * @param count how many times it was issued
     * @param latest when the newest of these rows was written, to tell a renamed issuer's current name
     */
    record Tally(Optional<UUID> actor, String actorName, SanctionAction action, int count, Instant latest) {

        public Tally {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(actorName, "actorName");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(latest, "latest");
        }
    }
}

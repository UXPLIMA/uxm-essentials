package com.uxplima.uxmessentials.moderation.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;

/**
 * The DB-backed append-only sanction-history store (the same hard moderation invariant as the sanction state:
 * it survives restart, never PDC). Kept a separate port from {@link ModerationRepository} so the history
 * concern stays isolated — recording one row never touches the active-state tables, and a read never widens
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

    /** {@code target}'s ban-family rows (ban and unban) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> banHistory(UUID target, int limit);

    /** {@code target}'s mute-family rows (mute and unmute) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> muteHistory(UUID target, int limit);

    /** {@code target}'s rows of every kind (the unified {@code /history}) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> recentForTarget(UUID target, int limit);

    /** Rows of every kind issued by {@code actor} ({@code /staffhistory}) newest-first, capped at {@code limit}. */
    List<SanctionHistoryEntry> recentByActor(UUID actor, int limit);
}

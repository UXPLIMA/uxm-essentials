package com.uxplima.uxmessentials.vote.application.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/**
 * Outbound port for the vote context's durable state: the single global vote-party counter, the
 * per-player offline reward queue, and per-player vote totals across every tracked period.
 *
 * <p>All three concerns survive a restart: an accumulated party count must not reset on a crash, a
 * vote for an offline player must still pay out on their next join, and a player's vote history must
 * persist so leaderboards and tally displays are accurate after a reload.
 *
 * <p>The queue is ordered per player: {@link #enqueue} appends a batch, {@link #drainFor} returns every
 * pending batch for one player and removes those rows in the same transaction (so a reward is paid out
 * exactly once), and {@link #hasPending} is the cheap existence probe the join handler runs first.
 */
public interface VoteRepository {

    /** The current accumulated vote-party count; zero when no party is in progress. */
    int partyCount();

    /** Persist the accumulated vote-party count (the single global counter row). */
    void setPartyCount(int count);

    /**
     * Atomically add one to the global vote-party counter and return the post-increment value. The
     * increment happens in the durable store in a single statement, so two votes that land concurrently
     * each observe a distinct count and exactly one of them sees the threshold crossing — the
     * read-then-write of {@link #partyCount()} plus {@link #setPartyCount(int)} cannot give that
     * guarantee and is kept only for the reset and for the hot read.
     */
    int incrementAndGetPartyCount();

    /** Append a reward batch for an offline voter to that player's queue. */
    void enqueue(QueuedReward reward);

    /** Return and remove every pending reward batch for {@code player}, in queued order. */
    List<QueuedReward> drainFor(PlayerRef player);

    /** True when {@code player} has at least one pending reward batch. */
    boolean hasPending(PlayerRef player);

    /**
     * Return the stored {@link VoteTally} for {@code player}, or {@link VoteTally#empty()} when the
     * player has never voted on this server.
     */
    VoteTally totalsOf(PlayerRef player);

    /** Persist an updated {@link VoteTally} for {@code player}. */
    void saveTotals(PlayerRef player, VoteTally tally);

    /**
     * Return the top {@code limit} voters for {@code period}, ordered from highest to lowest vote
     * count. Returns an empty list when no votes have been recorded for the period. The implementation
     * must not return more than {@code limit} rows.
     */
    List<VoteRanking> topVoters(VotePeriod period, int limit);

    // --- Party participant tracking ---

    /**
     * Record {@code player} as a participant in the current party window (they voted before the next
     * party fires). Idempotent — calling it multiple times for the same player in the same window is
     * safe.
     */
    void markPartyParticipant(PlayerRef player);

    /**
     * The UUIDs of every player who has voted during the current party window. Returns a snapshot;
     * the returned set is not live.
     */
    Set<UUID> partyParticipants();

    /**
     * Clear the participant set — called after a party fires so the next window starts empty.
     */
    void clearPartyParticipants();

    // --- Party period key (for reset-schedule boundary detection) ---

    /**
     * The stored party period key: 0 when no period key has been persisted yet (first run), or the
     * key written by the most recent {@link #setPartyPeriodKey(long)} call.
     *
     * <p>Interpretation is schedule-dependent: for {@code DAILY} this is an epoch-day; for
     * {@code WEEKLY} it is {@code weekBasedYear * 100 + weekOfYear}; for {@code NONE} the key is
     * always 0 and is never compared.
     */
    long partyPeriodKey();

    /** Persist the party period key for the current calendar window. */
    void setPartyPeriodKey(long key);

    // --- Threshold override (escalation) ---

    /**
     * The operator-or-escalation threshold override; 0 means no override is active and the base
     * threshold from {@link com.uxplima.uxmessentials.vote.application.PartyConfig} is used.
     */
    int thresholdOverride();

    /** Persist the threshold override (positive) or clear it (0). */
    void setThresholdOverride(int override);

    // --- Admin reset ---

    /**
     * Reset all vote totals for {@code player} to zero (alltime, daily, weekly, monthly, and their
     * period keys). Does not affect the vote queue or the party counter.
     */
    void resetTotals(PlayerRef player);
}

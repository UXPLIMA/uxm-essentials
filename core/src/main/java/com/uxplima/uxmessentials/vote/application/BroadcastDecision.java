package com.uxplima.uxmessentials.vote.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.vote.domain.BroadcastType;

/**
 * The pure policy that turns a {@link BroadcastType} plus the facts of one credited vote into a single
 * yes/no: should this vote be announced? All of {@link HandleVote}'s broadcast branching lives here so it
 * can be exhaustively tested without any port, and so the decision is identical wherever it is asked.
 *
 * <p>A blacklisted voter is never announced regardless of type. Otherwise each type reads its own facts:
 * {@code EVERY_VOTE} always announces; {@code ONLINE_ONLY} announces an online voter; {@code FIRST_VOTE_OF_DAY}
 * announces only when this vote was the voter's first of the day (the post-record daily count is exactly one);
 * {@code COOLDOWN_PER_PLAYER} announces an online voter whose last broadcast is absent or older than the
 * configured window.
 */
public final class BroadcastDecision {

    private BroadcastDecision() {}

    /**
     * Decide whether the vote just recorded should be broadcast.
     *
     * @param type the configured broadcast policy
     * @param voterOnline whether the voter is online (the same determination used for crediting)
     * @param dailyAfter the voter's daily vote count after this vote was recorded
     * @param blacklisted whether the voter is on the broadcast blacklist
     * @param lastBroadcast the voter's last broadcast instant, if any (only consulted for the cooldown type)
     * @param now the instant of this vote
     * @param cooldown the per-player cooldown window for {@link BroadcastType#COOLDOWN_PER_PLAYER}
     * @return {@code true} when this vote should be announced
     */
    public static boolean shouldBroadcast(
            BroadcastType type,
            boolean voterOnline,
            long dailyAfter,
            boolean blacklisted,
            Optional<Instant> lastBroadcast,
            Instant now,
            Duration cooldown) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lastBroadcast, "lastBroadcast");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(cooldown, "cooldown");
        if (blacklisted) {
            return false;
        }
        return switch (type) {
            case NONE -> false;
            case EVERY_VOTE -> true;
            case ONLINE_ONLY -> voterOnline;
            case FIRST_VOTE_OF_DAY -> dailyAfter == 1;
            case COOLDOWN_PER_PLAYER ->
                voterOnline
                        && (lastBroadcast.isEmpty()
                                || !now.isBefore(lastBroadcast.get().plus(cooldown)));
        };
    }
}

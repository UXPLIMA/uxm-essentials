package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpShuffle;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Deals the random browse order again once per round, off the tick thread. Rounds are counted from the epoch in UTC,
 * so a round's number depends only on the time: every server that shares the database and the round length deals
 * the same round at the same moment, and the order changes once per round across the network however many servers
 * run the sweep. A pass runs at start, so a server that was down catches up at once, and then at each boundary.
 *
 * <p>A round length of zero turns the sweep off and the order stays where it is.
 */
@NullMarked
public final class RandomOrderSweep extends AbstractPeriodicSweep {

    private final PlayerWarpShuffle shuffle;
    private final long roundMillis;

    public RandomOrderSweep(PlayerWarpShuffle shuffle, Scheduler scheduler, Duration round, Clock clock, Logger log) {
        super(scheduler, round, clock, log);
        this.shuffle = Objects.requireNonNull(shuffle, "shuffle");
        this.roundMillis = round.toMillis();
    }

    @Override
    protected boolean enabled() {
        return roundMillis > 0;
    }

    @Override
    protected boolean passOnStart() {
        return true;
    }

    @Override
    protected Duration untilNextPass() {
        return Duration.ofMillis(roundMillis - Math.floorMod(clock.millis(), roundMillis));
    }

    /** Deal the round the clock is in; a round already dealt by another server writes nothing. */
    @Override
    public void sweepOnce() {
        long round = Math.floorDiv(clock.millis(), roundMillis);
        int moved = shuffle.shuffle(round);
        log.debug("event=playerwarp_random_order round={} moved={}", round, moved);
    }
}

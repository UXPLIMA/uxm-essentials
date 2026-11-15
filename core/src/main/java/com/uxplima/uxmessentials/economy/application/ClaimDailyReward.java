package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.DailyRewardLedger;

/**
 * Decides on a network whether a joining player is owed the daily login reward. The stamp is taken in the shared
 * {@link DailyRewardLedger} before anything is paid, so a player who logs into two backends in quick succession is
 * paid by one of them only.
 *
 * <p>The stamp this server's PDC still holds from before the network was switched on is folded in first, keeping
 * the later of the two, so turning the network on does not hand everybody a fresh reward.
 *
 * <p>Blocking: the caller runs it on the async lane.
 */
public final class ClaimDailyReward {

    private final DailyRewardLedger ledger;
    private final Clock clock;
    private final Duration window;

    public ClaimDailyReward(DailyRewardLedger ledger, Clock clock, Duration window) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = Objects.requireNonNull(window, "window");
    }

    /**
     * Whether {@code player} is paid now. {@code localStamp} is the PDC stamp this server holds for them, if any;
     * true means the reward was stamped as taken and the caller pays it.
     */
    public boolean claim(UUID player, OptionalLong localStamp) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(localStamp, "localStamp");
        if (localStamp.isPresent()) {
            ledger.keepLatest(player, localStamp.getAsLong());
        }
        return ledger.claimIfDue(player, clock.millis(), window.toMillis());
    }
}

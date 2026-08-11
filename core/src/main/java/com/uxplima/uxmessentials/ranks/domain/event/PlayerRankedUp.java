package com.uxplima.uxmessentials.ranks.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player climbed one rung of the ladder, through {@code /rankup} or the autorank scan. Raised after the new
 * pointer is stored and the rank's actions have run, so a consumer reading the player's rank sees the new one.
 *
 * <p>An admin set is a different fact ({@link PlayerRankSet}): this one means the player met the requirements and
 * paid the cost, which is the half a reward plugin usually wants.
 *
 * @param who the player who advanced
 * @param from the rank they held
 * @param to the rank they now hold
 */
public record PlayerRankedUp(PlayerRef who, RankId from, RankId to) implements RankEvent {

    public PlayerRankedUp {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}

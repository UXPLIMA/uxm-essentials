package com.uxplima.uxmessentials.ranks.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player prestiged: they reached the top of the ladder, paid the prestige cost and were reset to the first rank
 * one level higher. Raised after the reset is stored and the prestige actions have run.
 *
 * @param who the player who prestiged
 * @param level the prestige level they reached
 * @param rewardMultiplier the reward multiplier that level now earns them
 */
public record PlayerPrestiged(PlayerRef who, int level, double rewardMultiplier) implements RankEvent {

    public PlayerPrestiged {
        Objects.requireNonNull(who, "who");
        if (level < 1) {
            throw new IllegalArgumentException("a prestige level of at least one has been reached: " + level);
        }
    }
}
